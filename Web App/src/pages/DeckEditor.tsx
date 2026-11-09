import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, Copy, LibraryBig, Minus, Plus, Search } from 'lucide-react';
import { collection, onSnapshot, doc, updateDoc, writeBatch } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import { useCards } from '../context/CardContext';
import { useDecks } from '../context/DeckContext';
import { usePrices } from '../context/PriceContext';
import type { Card, Domain } from '../models/card';
import type { Deck, DeckEntry } from '../models/deck';
import {
    ADD_RESULT_MESSAGES, BATTLEFIELD_COUNT, ISSUE_MESSAGES, MAIN_DECK_MIN, RULE_BREAK_CODES, RUNE_DECK_SIZE, SIDEBOARD_MAX,
    canBeChampion, checkAdd, counts as deckCounts, currentStep as rulesStep, deckContents, isCandidate, isPlayable, validate,
    type DeckSection, type DeckStep,
} from '../rules/deckRules';
import { DOMAIN_LABEL, DOMAIN_SIGIL, domainStripe, isBaseRune, shownDomains } from '../lib/style';
import { DomainBand } from '../components/rift/DomainBand';
import { Dialog } from '../components/rift/Dialog';
import { BannedTag, DomainIcon, EnergyToken, Loading, RiftLabel } from '../components/rift/Rift';

const STEP_LABELS: Record<DeckStep, { title: string; instruction: string }> = {
    LEGEND: { title: "Select Legend", instruction: "Choose 1 Legend card to lead your deck." },
    CHAMPION: { title: "Select Champion", instruction: "Choose the Champion unit that matches your Legend." },
    RUNES: { title: "Select Runes", instruction: `Choose ${RUNE_DECK_SIZE} Runes from your Legend's domains.` },
    BATTLEFIELDS: { title: "Select Battlefields", instruction: `Choose ${BATTLEFIELD_COUNT} Battlefields with different names.` },
    MAIN: { title: "Build Main Deck", instruction: `Add Units, Spells and Gear: at least ${MAIN_DECK_MIN} cards, including your Champion.` },
    SIDEBOARD: { title: "Sideboard", instruction: `Add up to ${SIDEBOARD_MAX} cards (optional).` },
    COMPLETE: { title: "Deck Complete", instruction: "Your deck is ready!" },
};

// Edits happen in event handlers, not during render; the indirection keeps the React
// compiler's purity check from flagging Date.now().
const timestamp = () => Date.now();

interface DeckStats {
    totalMainDeck: number;
    unitCount: number;
    spellCount: number;
    gearCount: number;
    energyCurve: Record<number, number>;
    mightCurve: Record<number, number>;
    domainBreakdown: Record<string, number>;
    totalValue: number;
    ownedValue: number;
    missingValue: number;
}

export default function DeckEditor() {
    const { deckId } = useParams<{ deckId: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const { decks, loadingDecks, renameDeck } = useDecks();
    const { binderCards, masterCards, updateCardQuantity } = useCards();

    const [entries, setEntries] = useState<DeckEntry[]>([]);
    const [entriesLoaded, setEntriesLoaded] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [isEditingName, setIsEditingName] = useState(false);
    const [newName, setNewName] = useState('');
    const [showUnowned, setShowUnowned] = useState(true);
    /** A step chosen from the step bar, to change runes, battlefields or the sideboard later. */
    const [viewStep, setViewStep] = useState<DeckStep | null>(null);
    const [confirmAddAll, setConfirmAddAll] = useState(false);
    const [notice, setNotice] = useState<string | null>(null);

    // The latest deck and entries, updated straight away on every edit. Edits read these
    // rather than React state, so rapid clicks see each other instead of the same stale
    // quantity (which lost copies and let decks go past the copy limit).
    const deckRef = useRef<Deck | null>(null);
    const entriesRef = useRef<DeckEntry[]>([]);

    const { prices, formatPrice } = usePrices();

    const cardsById = useMemo(() => new Map(masterCards.map(c => [c.cardId, c])), [masterCards]);

    const deck = useMemo(() => decks.find(d => d.deckId === deckId) ?? null, [decks, deckId]);

    useEffect(() => {
        if (deckId && !loadingDecks && !deck) navigate('/decks'); // Deck not found
    }, [deckId, deck, loadingDecks, navigate]);

    // Follow the saved deck, but never step back to an older copy than an edit just made.
    useEffect(() => {
        const local = deckRef.current;
        if (deck && (!local || local.deckId !== deck.deckId || deck.lastModified >= local.lastModified)) {
            deckRef.current = deck;
        }
    }, [deck]);

    useEffect(() => {
        if (!deckId) return;
        const user = auth.currentUser;
        if (!user) return;

        const entriesRefPath = collection(db, 'users', user.uid, 'decks', deckId, 'entries');
        const unsubscribe = onSnapshot(entriesRefPath, snapshot => {
            const fetched = snapshot.docs.map(d => {
                const data = d.data();
                // The Android app used to store the flag as "sideboard"; read either.
                return {
                    deckId: data.deckId ?? deckId,
                    cardId: data.cardId,
                    quantity: data.quantity ?? 0,
                    isSideboard: data.isSideboard ?? data.sideboard ?? false,
                } as DeckEntry;
            }).filter(e => e.cardId && e.quantity > 0);
            entriesRef.current = fetched;
            setEntries(fetched);
            setEntriesLoaded(true);
        });

        return () => unsubscribe();
    }, [deckId]);

    useEffect(() => {
        if (!notice) return;
        const timer = setTimeout(() => setNotice(null), 3500);
        return () => clearTimeout(timer);
    }, [notice]);

    const contents = useMemo(() => (deck ? deckContents(deck, entries) : null), [deck, entries]);

    const currentStep: DeckStep = useMemo(
        () => (contents && masterCards.length > 0 ? rulesStep(contents, cardsById) : 'LEGEND'),
        [contents, cardsById, masterCards.length],
    );
    const counts = useMemo(
        () => (contents ? deckCounts(contents, cardsById) : { runes: 0, battlefields: 0, mainDeck: 0, sideboard: 0 }),
        [contents, cardsById],
    );
    const ruleBreaks = useMemo(
        () => (contents && masterCards.length > 0 ? validate(contents, cardsById).filter(i => RULE_BREAK_CODES.has(i.code)) : []),
        [contents, cardsById, masterCards.length],
    );

    // Once the legend and champion are chosen, any later step can be opened to make changes.
    const canChooseStep = Boolean(deck?.legendCardId && deck?.championCardId);
    const pickerStep: DeckStep = viewStep && canChooseStep ? viewStep : (currentStep === 'COMPLETE' ? 'MAIN' : currentStep);
    const addTarget = pickerStep === 'LEGEND' || pickerStep === 'CHAMPION' ? undefined : pickerStep === 'SIDEBOARD';
    const stepCount = (step: DeckStep): string | null => {
        switch (step) {
            case 'RUNES': return `${counts.runes} / ${RUNE_DECK_SIZE}`;
            case 'BATTLEFIELDS': return `${counts.battlefields} / ${BATTLEFIELD_COUNT}`;
            case 'MAIN': return `${counts.mainDeck} / ${MAIN_DECK_MIN}`;
            case 'SIDEBOARD': return `${counts.sideboard} / ${SIDEBOARD_MAX}`;
            default: return null;
        }
    };
    const sideQty = (id: string) => entries.find(e => e.cardId === id && e.isSideboard)?.quantity ?? 0;

    // Decks saved before mainCount/sideCount existed, or by older Android builds, show the
    // wrong count in the deck list. Correct it once when the deck is opened.
    const countsChecked = useRef(false);
    useEffect(() => {
        if (countsChecked.current || !entriesLoaded || !deck || masterCards.length === 0) return;
        countsChecked.current = true;
        const user = auth.currentUser;
        if (!user || !deckId) return;
        if (deck.mainCount !== counts.mainDeck || deck.sideCount !== counts.sideboard) {
            updateDoc(doc(db, 'users', user.uid, 'decks', deckId), { mainCount: counts.mainDeck, sideCount: counts.sideboard })
                .catch(e => console.error('Failed to correct deck counts', e));
        }
    }, [entriesLoaded, deck, masterCards.length, counts, deckId]);

    const deckStats = useMemo<DeckStats>(() => {
        const stats: DeckStats = {
            totalMainDeck: 0,
            unitCount: 0,
            spellCount: 0,
            gearCount: 0,
            energyCurve: {},
            mightCurve: {},
            domainBreakdown: {},
            totalValue: 0,
            ownedValue: 0,
            missingValue: 0,
        };

        if (masterCards.length === 0) return stats;

        entries.forEach(entry => {
            const master = cardsById.get(entry.cardId);
            if (!master || entry.isSideboard || !isPlayable(master)) return;
            const qty = entry.quantity;

            stats.totalMainDeck += qty;

            if (master.type === 'UNIT') stats.unitCount += qty;
            if (master.type === 'SPELL') stats.spellCount += qty;
            if (master.type === 'GEAR') stats.gearCount += qty;

            const cost = master.cost || 0;
            stats.energyCurve[cost] = (stats.energyCurve[cost] || 0) + qty;

            if (master.type === 'UNIT') {
                const might = master.might || 0;
                stats.mightCurve[might] = (stats.mightCurve[might] || 0) + qty;
            }

            master.domain.forEach(d => {
                stats.domainBreakdown[d] = (stats.domainBreakdown[d] || 0) + qty;
            });
        });

        // Price Logic (all slots including Sideboard/Runes/Battlefields)
        entries.forEach(entry => {
            // US dollars; formatPrice converts when shown.
            const price = prices[entry.cardId] || 0;
            const needed = entry.quantity;
            const owned = (binderCards.find(b => b.card.cardId === entry.cardId)?.quantity || 0);

            stats.totalValue += (price * needed);

            const missingCount = Math.max(0, needed - owned);
            stats.missingValue += (price * missingCount);
        });

        stats.ownedValue = Math.max(0, stats.totalValue - stats.missingValue);

        return stats;
    }, [entries, masterCards, cardsById, binderCards, prices]);

    const handleSaveName = async () => {
        if (!deckId || !newName.trim() || newName === deck?.name) {
            setIsEditingName(false);
            return;
        }
        await renameDeck(deckId, newName.trim());
        setIsEditingName(false);
    };

    const latestContents = useCallback(
        () => (deckRef.current ? deckContents(deckRef.current, entriesRef.current) : null),
        [],
    );

    /**
     * Sets one entry's quantity and the deck's counts (and legend or champion) in a single
     * batch. Applies the change locally first so the next click sees it.
     */
    const applyEdit = (
        cardId: string,
        section: DeckSection,
        quantity: number,
        changes: { legendId?: string | null; championId?: string | null } = {},
    ) => {
        const user = auth.currentUser;
        const currentDeck = deckRef.current;
        if (!user || !deckId || !currentDeck) return;
        const isSideboard = section === 'SIDEBOARD';

        const others = entriesRef.current.filter(e => !(e.cardId === cardId && e.isSideboard === isSideboard));
        const nextEntries = quantity > 0 ? [...others, { deckId, cardId, quantity, isSideboard }] : others;

        const deckFields: Partial<Deck> = {};
        if (changes.legendId !== undefined) {
            deckFields.legendCardId = changes.legendId;
            deckFields.coverCardUrl = changes.legendId ? cardsById.get(changes.legendId)?.imageUrl ?? null : null;
        }
        if (changes.championId !== undefined) deckFields.championCardId = changes.championId;
        const nextDeck: Deck = { ...currentDeck, ...deckFields };
        const nextCounts = deckCounts(deckContents(nextDeck, nextEntries), cardsById);
        Object.assign(deckFields, { mainCount: nextCounts.mainDeck, sideCount: nextCounts.sideboard, lastModified: timestamp() });

        entriesRef.current = nextEntries;
        deckRef.current = { ...nextDeck, ...deckFields };
        setEntries(nextEntries);

        const batch = writeBatch(db);
        const entryRef = doc(db, 'users', user.uid, 'decks', deckId, 'entries', `${cardId}_${isSideboard}`);
        if (quantity > 0) {
            // "sideboard" is what Android builds before 1.1 read.
            batch.set(entryRef, { deckId, cardId, quantity, isSideboard, sideboard: isSideboard });
        } else {
            batch.delete(entryRef);
        }
        batch.update(doc(db, 'users', user.uid, 'decks', deckId), deckFields);
        batch.commit().catch(e => {
            console.error('Failed to save deck change', e);
            setNotice("Couldn't save that change. Check your connection.");
        });
    };

    const handleAddCard = (card: Card, targetIsSideboard?: boolean) => {
        const current = latestContents();
        if (!current) return;
        const step = rulesStep(current, cardsById);
        const quantityIn = (section: DeckSection) => (section === 'SIDEBOARD' ? current.side : current.main)[card.cardId] ?? 0;
        const reject = (result: keyof typeof ADD_RESULT_MESSAGES) => setNotice(ADD_RESULT_MESSAGES[result]);

        if (step === 'LEGEND') {
            const result = checkAdd(current, card.cardId, 'MAIN', cardsById);
            if (result !== 'OK') return reject(result);
            applyEdit(card.cardId, 'MAIN', quantityIn('MAIN') + 1, { legendId: card.cardId });
            return;
        }
        if (step === 'CHAMPION' && targetIsSideboard === undefined) {
            const legend = current.legendId ? cardsById.get(current.legendId) : undefined;
            if (!canBeChampion(card, legend)) {
                setNotice('Your champion must be a champion unit that matches your legend.');
                return;
            }
            const result = checkAdd(current, card.cardId, 'MAIN', cardsById);
            if (result !== 'OK') return reject(result);
            applyEdit(card.cardId, 'MAIN', quantityIn('MAIN') + 1, { championId: card.cardId });
            return;
        }

        const section: DeckSection = targetIsSideboard !== undefined
            ? (targetIsSideboard ? 'SIDEBOARD' : 'MAIN')
            : (step === 'SIDEBOARD' ? 'SIDEBOARD' : 'MAIN');
        const result = checkAdd(current, card.cardId, section, cardsById);
        if (result !== 'OK') return reject(result);
        applyEdit(card.cardId, section, quantityIn(section) + 1);
    };

    const handleRemoveCard = (cardId: string, fromSideboard?: boolean) => {
        const current = latestContents();
        if (!current) return;
        if (cardId === current.legendId) return; // The legend can't be removed once chosen.

        let section: DeckSection;
        if (fromSideboard !== undefined) {
            section = fromSideboard ? 'SIDEBOARD' : 'MAIN';
        } else if (rulesStep(current, cardsById) === 'SIDEBOARD') {
            // Remove from the sideboard first if the card is there.
            section = (current.side[cardId] ?? 0) > 0 ? 'SIDEBOARD' : 'MAIN';
        } else {
            section = (current.main[cardId] ?? 0) > 0 ? 'MAIN' : 'SIDEBOARD';
        }

        const quantity = (section === 'SIDEBOARD' ? current.side : current.main)[cardId] ?? 0;
        if (quantity <= 0) return;
        const removesChampion = section === 'MAIN' && cardId === current.championId && quantity === 1;
        applyEdit(cardId, section, quantity - 1, removesChampion ? { championId: null } : {});
    };

    const displayCards = useMemo(() => {
        const legendCard = deck?.legendCardId ? cardsById.get(deck.legendCardId) : undefined;

        let results = binderCards.filter(bc => isCandidate(bc.card, pickerStep, legendCard));

        if (searchQuery.trim()) {
            const lowerQuery = searchQuery.toLowerCase();
            results = results.filter(bc =>
                bc.card.name.toLowerCase().includes(lowerQuery) ||
                bc.card.type.toLowerCase().includes(lowerQuery)
            );
        }

        // Handle 'Show Unowned' toggle and Sort
        const unownedFiltered = showUnowned ? results : results.filter(bc => {
            return bc.quantity > 0 || isBaseRune(bc.card);
        });

        return unownedFiltered.sort((a, b) => {
            const isBaseRuneA = isBaseRune(a.card);
            const isBaseRuneB = isBaseRune(b.card);
            const aOwned = a.quantity > 0 || isBaseRuneA;
            const bOwned = b.quantity > 0 || isBaseRuneB;
            if (aOwned && !bOwned) return -1;
            if (!aOwned && bOwned) return 1;
            return a.card.cardId.localeCompare(b.card.cardId);
        });
    }, [binderCards, searchQuery, pickerStep, deck, cardsById, showUnowned]);

    // Group deck entries by structural step for the right pane
    const deckListGroups = useMemo(() => {
        type DeckListGroup = { id: string, title: string, items: { card: Card, quantity: number, isSideboard: boolean }[] };
        const groups: DeckListGroup[] = [
            { id: 'Legend', title: 'Legend', items: [] },
            { id: 'Champion', title: 'Champion', items: [] },
            { id: 'Runes', title: 'Runes', items: [] },
            { id: 'Battlefields', title: 'Battlefields', items: [] },
            { id: 'Main Deck', title: 'Main Deck', items: [] },
            { id: 'Sideboard', title: 'Sideboard', items: [] },
        ];

        entries.forEach(entry => {
            const masterCard = masterCards.find(c => c.cardId === entry.cardId);
            if (!masterCard) return;

            if (entry.isSideboard) {
                groups[5].items.push({ card: masterCard, quantity: entry.quantity, isSideboard: true });
            } else if (masterCard.cardId === deck?.legendCardId) {
                groups[0].items.push({ card: masterCard, quantity: entry.quantity, isSideboard: false });
            } else if (masterCard.cardId === deck?.championCardId) {
                groups[1].items.push({ card: masterCard, quantity: 1, isSideboard: false });
                if (entry.quantity > 1) {
                    groups[4].items.push({ card: masterCard, quantity: entry.quantity - 1, isSideboard: false });
                }
            } else if (masterCard.type === 'RUNE') {
                groups[2].items.push({ card: masterCard, quantity: entry.quantity, isSideboard: false });
            } else if (masterCard.type === 'BATTLEFIELD') {
                groups[3].items.push({ card: masterCard, quantity: entry.quantity, isSideboard: false });
            } else {
                groups[4].items.push({ card: masterCard, quantity: entry.quantity, isSideboard: false });
            }
        });

        // Sort items inside each structural group
        groups.forEach(g => {
            g.items.sort((a, b) => (a.card.cost || 0) - (b.card.cost || 0) || a.card.name.localeCompare(b.card.name));
        });

        return groups.filter(g => g.items.length > 0);
    }, [entries, masterCards, deck]);

    const handleExportDeck = async () => {
        if (!deck) return;

        let exportStr = "";

        const legendItem = deckListGroups.find(g => g.id === 'Legend')?.items[0];
        if (legendItem) {
            exportStr += `Legend:\n1 ${legendItem.card.name}\n\n`;
        }

        const champItem = deckListGroups.find(g => g.id === 'Champion')?.items[0];
        if (champItem) {
            exportStr += `Champion:\n1 ${champItem.card.name}\n\n`;
        }

        const mainGroup = deckListGroups.find(g => g.id === 'Main Deck');
        if (mainGroup && mainGroup.items.length > 0) {
            exportStr += `MainDeck:\n`;
            mainGroup.items.forEach(i => exportStr += `${i.quantity} ${i.card.name}\n`);
            exportStr += `\n`;
        }

        const bfGroup = deckListGroups.find(g => g.id === 'Battlefields');
        if (bfGroup && bfGroup.items.length > 0) {
            exportStr += `Battlefields:\n`;
            bfGroup.items.forEach(i => exportStr += `${i.quantity} ${i.card.name}\n`);
            exportStr += `\n`;
        }

        const runeGroup = deckListGroups.find(g => g.id === 'Runes');
        if (runeGroup && runeGroup.items.length > 0) {
            exportStr += `Runes:\n`;
            runeGroup.items.forEach(i => exportStr += `${i.quantity} ${i.card.name}\n`);
            exportStr += `\n`;
        }

        const sideGroup = deckListGroups.find(g => g.id === 'Sideboard');
        if (sideGroup && sideGroup.items.length > 0) {
            exportStr += `Sideboard:\n`;
            sideGroup.items.forEach(i => exportStr += `${i.quantity} ${i.card.name}\n`);
            exportStr += `\n`;
        }

        try {
            await navigator.clipboard.writeText(exportStr.trimEnd());
            setNotice('Decklist copied to your clipboard.');
        } catch (err) {
            console.error('Failed to copy text: ', err);
            prompt("Copy to clipboard: Ctrl+C, Enter", exportStr.trimEnd());
        }
    };

    const handleAddMissingToBinder = async (cardId: string, additionalCount: number) => {
        const currentOwned = binderCards.find(bc => bc.card.cardId === cardId)?.quantity || 0;
        await updateCardQuantity(cardId, currentOwned + additionalCount);
    };

    const handleAddAllMissingToBinder = async () => {
        const missingUpdates: { cardId: string, additionalNeeded: number }[] = [];

        entries.forEach(entry => {
            const card = masterCards.find(c => c.cardId === entry.cardId);
            if (!card) return;
            if (isBaseRune(card)) return;

            const totalInDeck = entries.filter(e => e.cardId === entry.cardId).reduce((ac, cv) => ac + cv.quantity, 0);
            const owned = binderCards.find(bc => bc.card.cardId === entry.cardId)?.quantity || 0;

            if (totalInDeck > owned) {
                if (!missingUpdates.find(mu => mu.cardId === entry.cardId)) {
                    missingUpdates.push({ cardId: entry.cardId, additionalNeeded: totalInDeck - owned });
                }
            }
        });

        if (missingUpdates.length === 0) return;

        await Promise.all(missingUpdates.map(async (update) => {
            const currentOwned = binderCards.find(bc => bc.card.cardId === update.cardId)?.quantity || 0;
            await updateCardQuantity(update.cardId, currentOwned + update.additionalNeeded);
        }));
    };

    const ownedById = useMemo(() => new Map(binderCards.map(bc => [bc.card.cardId, bc.quantity])), [binderCards]);
    const inDeckById = useMemo(() => {
        const m = new Map<string, number>();
        entries.forEach(e => m.set(e.cardId, (m.get(e.cardId) ?? 0) + e.quantity));
        return m;
    }, [entries]);
    const missingById = useMemo(() => {
        const m = new Map<string, number>();
        inDeckById.forEach((qty, id) => {
            const card = cardsById.get(id);
            if (!card || isBaseRune(card)) return;
            const short = qty - (ownedById.get(id) ?? 0);
            if (short > 0) m.set(id, short);
        });
        return m;
    }, [inDeckById, ownedById, cardsById]);
    const missingTotal = [...missingById.values()].reduce((a, n) => a + n, 0);
    const missingValue = [...missingById].reduce((a, [id, n]) => a + (prices[id] ?? 0) * n, 0);

    const openCard = (cardId: string, ids: string[]) => navigate(`/cards/${cardId}`, { state: { background: location, ids } });

    if (!deck) return <Loading label="Loading deck" />;

    const legend = deck.legendCardId ? cardsById.get(deck.legendCardId) : undefined;
    const champion = deck.championCardId ? cardsById.get(deck.championCardId) : undefined;
    const pickerLabel = currentStep === 'COMPLETE' && !viewStep ? STEP_LABELS.COMPLETE : STEP_LABELS[pickerStep];
    const steps: { step: DeckStep; label: string; count: string; done: boolean; open: boolean }[] = [
        { step: 'LEGEND', label: 'Legend', count: legend ? '1' : '0', done: Boolean(legend), open: false },
        { step: 'CHAMPION', label: 'Champion', count: champion ? '1' : '0', done: Boolean(champion), open: false },
        { step: 'RUNES', label: 'Runes', count: `${counts.runes}/${RUNE_DECK_SIZE}`, done: counts.runes === RUNE_DECK_SIZE, open: canChooseStep },
        { step: 'BATTLEFIELDS', label: 'Battlefields', count: `${counts.battlefields}/${BATTLEFIELD_COUNT}`, done: counts.battlefields === BATTLEFIELD_COUNT, open: canChooseStep },
        { step: 'MAIN', label: 'Main deck', count: `${counts.mainDeck}/${MAIN_DECK_MIN}`, done: counts.mainDeck >= MAIN_DECK_MIN, open: canChooseStep },
        { step: 'SIDEBOARD', label: 'Sideboard', count: `${counts.sideboard}/${SIDEBOARD_MAX}`, done: false, open: canChooseStep },
    ];
    const curve = (() => {
        const keys = ['0', '1', '2', '3', '4', '5', '6', '7+'];
        const n = (k: string) => k === '7+'
            ? Object.entries(deckStats.energyCurve).filter(([c]) => Number(c) >= 7).reduce((a, [, v]) => a + v, 0)
            : deckStats.energyCurve[Number(k)] ?? 0;
        const peak = Math.max(1, ...keys.map(n));
        return keys.map(k => ({ cost: k, n: n(k), pct: (n(k) / peak) * 100 }));
    })();
    const domainTotal = Object.values(deckStats.domainBreakdown).reduce((a, n) => a + n, 0);

    return (
        <div className="w-full max-w-[1600px] mx-auto flex flex-col">
            <div className="px-4 sm:px-8 pt-5 flex flex-col gap-3.5">
                <div className="flex flex-wrap items-stretch gap-3.5">
                    <button type="button" onClick={() => navigate('/decks')} aria-label="Back to decks" className="w-11 flex items-center justify-center border border-line text-parchment hover:text-ivory cursor-pointer">
                        <ArrowLeft aria-hidden="true" className="w-5 h-5" />
                    </button>
                    {legend && <img src={legend.imageUrl} alt="" className="hidden sm:block w-16 h-16 object-cover object-[50%_20%] border border-gold-deep" />}
                    <div className="flex-[1_1_320px] min-w-0">
                        {(() => {
                            const inner = (
                                <div className="flex items-center gap-3 min-h-16 px-[18px]">
                                    {isEditingName ? (
                                        <form onSubmit={e => { e.preventDefault(); handleSaveName(); }} className="flex-1 flex flex-col">
                                            <label className="rift-label text-[13px] text-white/75" htmlFor="deck-name">Deck name</label>
                                            <input id="deck-name" autoFocus type="text" value={newName} onChange={e => setNewName(e.target.value)} onBlur={handleSaveName} className="bg-transparent outline-none text-white font-bold text-2xl" />
                                        </form>
                                    ) : (
                                        <button type="button" onClick={() => { setNewName(deck.name); setIsEditingName(true); }} className="flex-1 min-w-0 flex flex-col items-start text-left cursor-text" title="Rename deck">
                                            <span className="rift-label text-[13px] text-white/75">Deck name</span>
                                            <span className="font-bold text-2xl text-white truncate max-w-full">{deck.name}</span>
                                        </button>
                                    )}
                                    {legend && shownDomains(legend.domain).filter(d => d !== 'COLORLESS').map(d => <DomainIcon key={d} domain={d} className="w-[30px] h-[30px]" />)}
                                </div>
                            );
                            return legend ? <DomainBand domains={legend.domain}>{inner}</DomainBand> : <div className="band-cut bg-raised">{inner}</div>;
                        })()}
                    </div>
                    <button type="button" onClick={handleExportDeck} className="btn-outline"><Copy aria-hidden="true" className="w-4 h-4" />Copy list</button>
                </div>

                <nav aria-label="Build steps" className="flex flex-wrap gap-1.5">
                    {steps.map(s => {
                        const current = s.step === pickerStep;
                        const content = (
                            <>
                                <span aria-hidden="true" className={`w-2 h-2 rotate-45 ${s.done ? 'bg-[#508C33]' : current ? 'bg-gold' : 'bg-line-strong'}`} />
                                {s.label}
                                <span className={`rift-number text-[15px] ${s.done ? 'text-muted' : 'text-gold'}`}>{s.count}</span>
                            </>
                        );
                        const cls = `min-h-[38px] flex items-center gap-2 px-3.5 border text-sm font-semibold ${current ? 'bg-gold-wash border-gold-deep text-gold' : 'bg-surface border-line text-parchment'}`;
                        return s.open ? (
                            <button key={s.step} type="button" aria-current={current ? 'step' : undefined} onClick={() => setViewStep(s.step)} className={`${cls} cursor-pointer hover:border-line-strong`}>{content}</button>
                        ) : (
                            <span key={s.step} aria-current={current ? 'step' : undefined} className={cls}>{content}</span>
                        );
                    })}
                </nav>

                {ruleBreaks.length > 0 && (
                    <div role="alert" className="flex flex-wrap items-start gap-3 px-3.5 py-2.5 border border-banned bg-banned/15">
                        <span className="h-6 inline-flex items-center px-2 bg-banned rift-label text-[13px] tracking-[0.1em] text-white">Not legal</span>
                        <ul className="flex-1 text-[15px] text-parchment">
                            {ruleBreaks.map(issue => (
                                <li key={`${issue.code}:${issue.subject ?? ''}`}>
                                    {ISSUE_MESSAGES[issue.code]}{issue.subject ? ` (${cardsById.get(issue.subject)?.name ?? issue.subject})` : ''}
                                </li>
                            ))}
                        </ul>
                    </div>
                )}
                {notice && <p role="status" className="text-sm text-warn">{notice}</p>}
            </div>

            <div className="px-4 sm:px-8 pt-4 pb-16 flex flex-wrap items-start gap-6">
                <section aria-label="Add cards" className="flex-[999_1_520px] min-w-0 flex flex-col gap-3">
                    <div className="flex flex-wrap items-baseline gap-x-2.5 gap-y-1">
                        <h1 className="rift-label text-[28px] tracking-[0.06em]">{pickerLabel.title}</h1>
                        {stepCount(pickerStep) && <span className="rift-number text-[22px] text-gold">{stepCount(pickerStep)}</span>}
                        <span className="text-sm text-muted">{pickerLabel.instruction} Click a card to add a copy.</span>
                    </div>
                    <div className="flex flex-wrap gap-2">
                        <label className="flex-[1_1_240px] flex items-center gap-2.5 h-10 px-3 bg-surface border border-line text-muted focus-within:border-gold-deep">
                            <Search aria-hidden="true" className="w-[18px] h-[18px]" />
                            <span className="sr-only">Search cards</span>
                            <input type="search" value={searchQuery} onChange={e => setSearchQuery(e.target.value)} placeholder="Search cards you can add" className="flex-1 min-w-0 bg-transparent outline-none text-ivory placeholder:text-muted text-[15px]" />
                        </label>
                        <button type="button" aria-pressed={!showUnowned} onClick={() => setShowUnowned(!showUnowned)} className="chip min-h-10">Owned only</button>
                    </div>
                    {displayCards.length === 0 ? (
                        <div className="panel p-10 text-center text-muted">{currentStep === 'COMPLETE' && !viewStep ? 'Your deck is complete. Pick a step above to make changes.' : 'No cards match.'}</div>
                    ) : (
                        <div className="grid grid-cols-[repeat(auto-fill,minmax(124px,1fr))] gap-x-3 gap-y-3.5">
                            {displayCards.map(bc => {
                                const card = bc.card;
                                const inDeck = inDeckById.get(card.cardId) ?? 0;
                                const owned = bc.quantity;
                                const plentiful = isBaseRune(card);
                                const short = !plentiful && inDeck > owned;
                                const banned = card.bannedIn?.includes('STANDARD');
                                return (
                                    <div key={card.cardId} className="tile relative flex flex-col gap-1.5 min-w-0">
                                        <button
                                            type="button"
                                            onClick={() => handleAddCard(card, addTarget)}
                                            aria-label={`Add ${card.name}${inDeck ? `, ${inDeck} in deck` : ''}`}
                                            className="relative block w-full aspect-[744/1039] bg-surface overflow-hidden cursor-pointer hover:outline-2 hover:outline-gold hover:outline-offset-2"
                                        >
                                            {card.orientation === 'landscape' ? (
                                                <span className="absolute inset-0 flex items-center justify-center">
                                                    <img src={card.imageUrl} alt="" loading="lazy" className={`-rotate-90 w-[139.7%] max-w-none ${owned || plentiful ? '' : 'opacity-45'}`} />
                                                </span>
                                            ) : (
                                                <img src={card.imageUrl} alt="" loading="lazy" className={`w-full h-full object-cover ${owned || plentiful ? '' : 'opacity-45'}`} />
                                            )}
                                            {banned && (
                                                <span className="absolute inset-x-0 top-0 h-5 flex items-center justify-center bg-banned/95 rift-label text-xs tracking-[0.1em] text-white">Banned</span>
                                            )}
                                            {inDeck > 0 && (
                                                <span className="absolute left-1.5 bottom-1.5 h-6 px-1.5 flex items-center bg-gold text-on-gold rift-number text-[15px]">{inDeck} in deck</span>
                                            )}
                                        </button>
                                        <span className="text-[13px] font-semibold truncate">{card.name}</span>
                                        <span className={`text-xs ${short ? 'text-warn' : 'text-muted'}`}>{plentiful ? 'Basic rune' : `You own ${owned}`}</span>
                                        {inDeck > 0 && card.type !== 'LEGEND' && (
                                            <button
                                                type="button"
                                                aria-label={`Remove a copy of ${card.name}`}
                                                onClick={() => handleRemoveCard(card.cardId, pickerStep === 'SIDEBOARD' ? (sideQty(card.cardId) > 0) : undefined)}
                                                className="tile-actions absolute right-1.5 top-1.5 w-8 h-8 flex items-center justify-center bg-ink/90 border border-line-strong text-ivory hover:bg-banned cursor-pointer"
                                            >
                                                <Minus aria-hidden="true" className="w-4 h-4" />
                                            </button>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </section>

                <aside aria-label="Deck list" className="flex-[1_1_340px] lg:max-w-[400px] flex flex-col gap-2.5 lg:sticky lg:top-20 lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto">
                    {missingTotal > 0 && (
                        <section className="flex flex-col gap-2.5 p-3 bg-surface border border-gold-deep">
                            <div className="flex items-center gap-2.5">
                                <AlertTriangle aria-hidden="true" className="w-5 h-5 text-warn shrink-0" />
                                <div className="flex flex-col">
                                    <span className="font-bold text-[15px]">{missingTotal} {missingTotal === 1 ? 'card' : 'cards'} missing from your collection</span>
                                    <span className="text-[13px] text-muted">{missingValue > 0 ? `About ${formatPrice(missingValue)} to buy.` : "Cards this deck uses that you don't own."}</span>
                                </div>
                            </div>
                            <button type="button" className="btn-gold min-h-11 text-base" onClick={() => setConfirmAddAll(true)}>
                                <LibraryBig aria-hidden="true" className="w-[18px] h-[18px]" />Add all {missingTotal} to collection
                            </button>
                        </section>
                    )}

                    {deckListGroups.length === 0 ? (
                        <div className="panel p-6 text-center text-muted">Click cards on the left to add them to this deck.</div>
                    ) : deckListGroups.map(group => (
                        <section key={group.id} className="flex flex-col gap-1.5">
                            <div className="flex items-center justify-between pt-1.5 px-0.5">
                                <RiftLabel className="text-[15px]">{group.title} · {group.items.reduce((a, i) => a + i.quantity, 0)}</RiftLabel>
                            </div>
                            {group.items.map(({ card, quantity, isSideboard }) => {
                                const missing = missingById.get(card.cardId) ?? 0;
                                const banned = card.bannedIn?.includes('STANDARD');
                                const fixed = card.type === 'LEGEND' || group.id === 'Champion';
                                return (
                                    <div key={`${card.cardId}-${isSideboard}-${group.id}`} className={`flex flex-col gap-1.5 px-2 py-1.5 bg-surface border ${banned ? 'border-banned' : 'border-line'}`}>
                                        <div className="flex items-center gap-2.5">
                                            {['UNIT', 'SPELL', 'GEAR'].includes(card.type)
                                                ? <EnergyToken value={card.cost} className="w-6 h-6 text-sm border-0" />
                                                : <span className="w-6" />}
                                            <span aria-hidden="true" className="w-1 self-stretch" style={{ background: domainStripe(card.domain) }} />
                                            <button type="button" onClick={() => openCard(card.cardId, group.items.map(i => i.card.cardId))} className="flex-1 min-w-0 flex flex-col items-start text-left cursor-pointer hover:text-gold">
                                                <BannedTag card={card} />
                                                <span className="text-sm font-semibold truncate max-w-full">{card.name}</span>
                                            </button>
                                            {fixed ? (
                                                <span className="w-[22px] text-center rift-number text-lg">{quantity}</span>
                                            ) : (
                                                <>
                                                    <button type="button" aria-label={`Remove a copy of ${card.name}`} onClick={() => handleRemoveCard(card.cardId, isSideboard)} className="w-8 h-8 flex items-center justify-center bg-raised text-ivory hover:bg-line cursor-pointer"><Minus aria-hidden="true" className="w-4 h-4" /></button>
                                                    <span className="w-[22px] text-center rift-number text-lg">{quantity}</span>
                                                    <button type="button" aria-label={`Add a copy of ${card.name}`} onClick={() => handleAddCard(card, isSideboard)} className="w-8 h-8 flex items-center justify-center bg-raised text-ivory hover:bg-line cursor-pointer"><Plus aria-hidden="true" className="w-4 h-4" /></button>
                                                </>
                                            )}
                                        </div>
                                        {missing > 0 && (
                                            <div className="flex flex-wrap items-center gap-2.5 pl-[38px]">
                                                <span className="text-[13px] text-warn">Missing {missing}</span>
                                                <button type="button" onClick={() => handleAddMissingToBinder(card.cardId, 1)} className="min-h-7 flex items-center gap-1.5 px-2 border border-gold-deep bg-gold-wash text-[13px] text-ivory hover:border-gold cursor-pointer">
                                                    <Plus aria-hidden="true" className="w-3.5 h-3.5 text-gold" />Add 1 to collection
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </section>
                    ))}
                </aside>

                <aside aria-label="Deck stats" className="flex-[1_1_240px] lg:max-w-[300px] panel p-4 flex flex-col gap-[18px] lg:sticky lg:top-20">
                    <RiftLabel className="text-base">Energy curve</RiftLabel>
                    <div className="h-32 flex items-end gap-1.5">
                        {curve.map(b => (
                            <div key={b.cost} className="flex-1 h-full flex flex-col items-center justify-end gap-1">
                                <span className="text-xs text-parchment">{b.n || ''}</span>
                                <span className="w-full bg-gold" style={{ height: `${b.pct * 0.8}%` }} />
                                <span className="rift-number text-[13px] text-muted">{b.cost}</span>
                            </div>
                        ))}
                    </div>
                    {domainTotal > 0 && (
                        <div className="flex flex-col gap-2">
                            <RiftLabel className="text-base">Domains</RiftLabel>
                            <div className="flex h-2.5">
                                {Object.entries(deckStats.domainBreakdown).map(([d, n]) => (
                                    <span key={d} style={{ width: `${(n / domainTotal) * 100}%`, background: DOMAIN_SIGIL[d as Domain] }} />
                                ))}
                            </div>
                            <div className="flex flex-wrap justify-between gap-x-3 text-[13px] text-parchment">
                                {Object.entries(deckStats.domainBreakdown).map(([d, n]) => <span key={d}>{DOMAIN_LABEL[d as Domain]} {n}</span>)}
                            </div>
                        </div>
                    )}
                    <div className="flex flex-col gap-1.5 text-sm">
                        <RiftLabel className="text-base">Types</RiftLabel>
                        <span className="flex justify-between text-parchment"><span>Units</span><span>{deckStats.unitCount}</span></span>
                        <span className="flex justify-between text-parchment"><span>Spells</span><span>{deckStats.spellCount}</span></span>
                        <span className="flex justify-between text-parchment"><span>Gear</span><span>{deckStats.gearCount}</span></span>
                    </div>
                    <div className="flex flex-col gap-1 pt-3 border-t border-line">
                        <span className="text-[13px] text-muted">Deck value</span>
                        <span className="rift-number text-[28px] text-gold">{formatPrice(deckStats.totalValue)}</span>
                        <span className="text-[13px] text-muted">
                            {deckStats.missingValue > 0 ? `You own ${formatPrice(deckStats.ownedValue)} of it.` : 'You own every card.'} TCGplayer market prices.
                        </span>
                    </div>
                </aside>
            </div>

            {confirmAddAll && (
                <Dialog
                    title={`Add ${missingTotal} ${missingTotal === 1 ? 'card' : 'cards'} to your collection?`}
                    onClose={() => setConfirmAddAll(false)}
                    footer={
                        <div className="flex justify-end gap-2">
                            <button type="button" className="btn-quiet" onClick={() => setConfirmAddAll(false)}>Cancel</button>
                            <button type="button" className="btn-gold min-h-10 text-base" onClick={() => { setConfirmAddAll(false); handleAddAllMissingToBinder(); }}>Add {missingTotal}</button>
                        </div>
                    }
                >
                    <p className="text-parchment">This marks every copy this deck needs as owned. Use it once you have the cards.</p>
                </Dialog>
            )}
        </div>
    );
};
