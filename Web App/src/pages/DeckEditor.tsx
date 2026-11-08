import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, Minus, Save, Edit2, Copy, Download } from 'lucide-react';
import { collection, onSnapshot, doc, updateDoc, writeBatch } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import { useCards } from '../context/CardContext';
import { useDecks } from '../context/DeckContext';
import { usePrices } from '../context/PriceContext';
import { CardDetailModal } from '../components/ui/CardDetailModal';
import type { Card } from '../models/card';
import type { Deck, DeckEntry } from '../models/deck';
import { SleekSearchBar } from '../components/ui/SleekSearchBar';
import {
    ADD_RESULT_MESSAGES, BATTLEFIELD_COUNT, DECK_STEPS, ISSUE_MESSAGES, MAIN_DECK_MIN, RUNE_DECK_SIZE, SIDEBOARD_MAX,
    canBeChampion, checkAdd, counts as deckCounts, currentStep as rulesStep, deckContents, isCandidate, isPlayable, validate,
    type DeckSection, type DeckStep, type IssueCode,
} from '../rules/deckRules';

const DOMAIN_COLORS: Record<string, string> = {
    'FURY': '#ef4444',
    'BODY': '#f97316',
    'ORDER': '#eab308',
    'MIND': '#3b82f6',
    'CALM': '#22c55e',
    'CHAOS': '#a855f7',
    'COLORLESS': '#9ca3af'
};

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

/** Issues that mean the deck breaks a rule, as opposed to simply being unfinished. */
const RULE_BREAK_CODES: ReadonlySet<IssueCode> = new Set<IssueCode>([
    'INVALID_CHAMPION', 'COPY_LIMIT', 'SIGNATURE_LIMIT', 'SIGNATURE_WRONG_CHAMPION', 'OUTSIDE_DOMAIN_IDENTITY',
    'DUPLICATE_BATTLEFIELD', 'NOT_PLAYABLE', 'NOT_ALLOWED_IN_SIDEBOARD', 'SIDEBOARD_TOO_LARGE', 'UNKNOWN_CARD',
]);

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
    const { decks, loadingDecks, renameDeck } = useDecks();
    const { binderCards, masterCards, updateCardQuantity } = useCards();

    const [entries, setEntries] = useState<DeckEntry[]>([]);
    const [entriesLoaded, setEntriesLoaded] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [isEditingName, setIsEditingName] = useState(false);
    const [newName, setNewName] = useState('');
    const [selectedCard, setSelectedCard] = useState<Card | null>(null);
    const [showUnowned, setShowUnowned] = useState(true);
    const [activeTab, setActiveTab] = useState<'CARDS' | 'STATS'>('CARDS');
    const [notice, setNotice] = useState<string | null>(null);

    // The latest deck and entries, updated straight away on every edit. Edits read these
    // rather than React state, so rapid clicks see each other instead of the same stale
    // quantity (which lost copies and let decks go past the copy limit).
    const deckRef = useRef<Deck | null>(null);
    const entriesRef = useRef<DeckEntry[]>([]);

    const { prices, convertPrice, getCurrencySymbol } = usePrices();

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
            const rawPrice = prices[entry.cardId] || 0;
            const price = convertPrice(rawPrice);
            const needed = entry.quantity;
            const owned = (binderCards.find(b => b.card.cardId === entry.cardId)?.quantity || 0);

            stats.totalValue += (price * needed);

            const missingCount = Math.max(0, needed - owned);
            stats.missingValue += (price * missingCount);
        });

        stats.ownedValue = Math.max(0, stats.totalValue - stats.missingValue);

        return stats;
    }, [entries, masterCards, cardsById, binderCards, prices, convertPrice]);

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

        let results = binderCards.filter(bc => isCandidate(bc.card, currentStep, legendCard));

        if (searchQuery.trim()) {
            const lowerQuery = searchQuery.toLowerCase();
            results = results.filter(bc =>
                bc.card.name.toLowerCase().includes(lowerQuery) ||
                bc.card.type.toLowerCase().includes(lowerQuery)
            );
        }

        // Handle 'Show Unowned' toggle and Sort
        const unownedFiltered = showUnowned ? results : results.filter(bc => {
            const isBaseRune = bc.card.type === 'RUNE' && !/[a-z]/.test(bc.card.cardId.split('-')[1] || '');
            return bc.quantity > 0 || isBaseRune;
        });

        return unownedFiltered.sort((a, b) => {
            const isBaseRuneA = a.card.type === 'RUNE' && !/[a-z]/.test(a.card.cardId.split('-')[1] || '');
            const isBaseRuneB = b.card.type === 'RUNE' && !/[a-z]/.test(b.card.cardId.split('-')[1] || '');
            const aOwned = a.quantity > 0 || isBaseRuneA;
            const bOwned = b.quantity > 0 || isBaseRuneB;
            if (aOwned && !bOwned) return -1;
            if (!aOwned && bOwned) return 1;
            return a.card.cardId.localeCompare(b.card.cardId);
        });
    }, [binderCards, searchQuery, currentStep, deck, cardsById, showUnowned]);

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
            alert('Decklist copied to clipboard!');
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
            const isBaseRune = card.type === 'RUNE' && !/[a-z]/.test(card.cardId.split('-')[1] || '');
            if (isBaseRune) return;

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

    const stepProgress = (step: DeckStep) => {
        switch (step) {
            case 'RUNES': return ` (${counts.runes}/${RUNE_DECK_SIZE})`;
            case 'BATTLEFIELDS': return ` (${counts.battlefields}/${BATTLEFIELD_COUNT})`;
            case 'MAIN': return ` (${counts.mainDeck}/${MAIN_DECK_MIN})`;
            case 'SIDEBOARD': return ` (${counts.sideboard}/${SIDEBOARD_MAX})`;
            default: return '';
        }
    };

    if (!deck) {
        return <div className="p-8 text-center text-white">Loading...</div>;
    }

    return (
        <div className="flex flex-col lg:flex-row h-[calc(100vh-64px)] overflow-hidden">
            {/* LEFT PANE: Collection/Binder Search */}
            <div className="w-full lg:w-1/2 h-1/2 lg:h-full flex flex-col border-b lg:border-b-0 lg:border-r border-white/10 p-4 md:p-6 bg-black/20 relative">

                {/* Wizard Header Ribbon */}
                <div className="absolute top-0 left-0 right-0 h-1 bg-white/10">
                    <div
                        className="h-full bg-riftbound-accent transition-all duration-500 ease-in-out"
                        style={{ width: `${(DECK_STEPS.indexOf(currentStep) / (DECK_STEPS.length - 1)) * 100}%` }}
                    />
                </div>

                <div className="mb-6 mt-2">
                    <button
                        onClick={() => navigate('/decks')}
                        className="flex items-center gap-2 text-riftbound-textDim hover:text-white transition-colors mb-4"
                    >
                        <ArrowLeft className="w-4 h-4" /> Back to Decks
                    </button>

                    <div className="mb-4">
                        <h3 className="text-xl font-black text-white">{STEP_LABELS[currentStep].title}{stepProgress(currentStep)}</h3>
                        <p className="text-sm text-riftbound-accentBright font-bold">{STEP_LABELS[currentStep].instruction}</p>
                        {notice && (
                            <p role="status" className="mt-2 text-sm text-amber-300">{notice}</p>
                        )}
                        {ruleBreaks.length > 0 && (
                            <ul className="mt-2 text-xs text-red-300 space-y-0.5">
                                {ruleBreaks.map(issue => (
                                    <li key={`${issue.code}:${issue.subject ?? ''}`}>
                                        {ISSUE_MESSAGES[issue.code]}{issue.subject ? ` (${cardsById.get(issue.subject)?.name ?? issue.subject})` : ''}
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>

                    <div className="flex gap-2 mb-2 items-center">
                        <SleekSearchBar query={searchQuery} setQuery={setSearchQuery} placeholder="Search valid cards..." />
                        <button
                            onClick={() => setShowUnowned(!showUnowned)}
                            className={`shrink-0 px-3 py-2 rounded font-bold text-xs border transition-colors ${showUnowned ? 'bg-riftbound-accent text-white border-white/20' : 'bg-transparent text-riftbound-textDim border-white/10 hover:text-white'}`}
                        >
                            {showUnowned ? 'Hide Unowned' : 'Show Unowned'}
                        </button>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-4 pb-[20vh]">
                    {displayCards.map(bc => {
                        const inDeck = entries.filter(e => e.cardId === bc.card.cardId).reduce((ac, cv) => ac + cv.quantity, 0);
                        const mainCopies = entries.find(e => e.cardId === bc.card.cardId && !e.isSideboard)?.quantity || 0;
                        const sideCopies = entries.find(e => e.cardId === bc.card.cardId && e.isSideboard)?.quantity || 0;

                        const isBaseRune = bc.card.type === 'RUNE' && !/[a-z]/.test(bc.card.cardId.split('-')[1] || '');
                        // Logic: if it's a base rune, we say we effectively have infinite / don't cap visually or lock us out
                        const isAvailable = isBaseRune || (bc.quantity > 0);
                        const isMissing = !isBaseRune && (inDeck > bc.quantity);

                        const canUndo = mainCopies > 0 || sideCopies > 0;

                        return (
                            <div
                                key={bc.card.cardId}
                                className={`relative transition-transform hover:scale-105 self-start ${!isAvailable ? 'opacity-50 grayscale' : ''}`}
                            >
                                <img
                                    src={bc.card.imageUrl}
                                    alt={bc.card.name}
                                    className="w-full rounded-xl shadow-lg cursor-pointer"
                                    loading="lazy"
                                    onClick={() => handleAddCard(bc.card)}
                                />

                                <div className={`absolute top-2 right-2 backdrop-blur-md px-2 py-1 rounded text-xs font-bold border border-white/10 ${isMissing ? 'bg-red-900/90 text-red-200' : 'bg-black/80'}`}>
                                    <span className={isMissing ? "text-red-300" : "text-riftbound-accentBright"}>
                                        {inDeck > 0 ? inDeck : ''}
                                    </span>
                                    {!isBaseRune && (
                                        <span className={isMissing ? "text-red-200" : "text-riftbound-textDim"}>{inDeck > 0 ? ' / ' : ''}{bc.quantity}</span>
                                    )}
                                </div>
                                {inDeck > 0 && bc.card.type === 'LEGEND' && (
                                    <div className="absolute bottom-2 left-2 bg-riftbound-accent text-white px-2 py-1 rounded-full text-xs font-bold border border-white/20 shadow-lg shadow-riftbound-accent/50">
                                        In Deck: {inDeck}
                                    </div>
                                )}
                                {inDeck > 0 && bc.card.type !== 'LEGEND' && (
                                    <div className="absolute bottom-2 left-2 flex items-center bg-riftbound-accent text-white rounded-full text-xs font-bold border border-white/20 shadow-lg shadow-riftbound-accent/50 overflow-hidden">
                                        <button
                                            onClick={(e) => {
                                                if (!canUndo) return;
                                                e.stopPropagation();
                                                handleRemoveCard(bc.card.cardId);
                                            }}
                                            className={`px-2 py-1 transition-colors ${canUndo ? 'hover:bg-white/20 cursor-pointer' : 'opacity-30 cursor-not-allowed'}`}
                                            disabled={!canUndo}
                                        >
                                            <Minus className="w-3 h-3" />
                                        </button>
                                        <span className="px-1 border-x border-white/20">In Deck: {inDeck}</span>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleAddCard(bc.card); }}
                                            className="px-2 py-1 hover:bg-white/20 transition-colors cursor-pointer"
                                        >
                                            <Plus className="w-3 h-3" />
                                        </button>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                    {displayCards.length === 0 && (
                        <div className="col-span-full py-20 text-center text-riftbound-textDim">
                            No owned cards found matching your search.
                        </div>
                    )}
                </div>
            </div>

            {/* RIGHT PANE: Deck Composition */}
            <div className="w-full lg:w-1/2 h-1/2 lg:h-full flex flex-col p-4 md:p-6 bg-riftbound-darkest relative z-10">
                <div className="flex justify-between items-start mb-6 pb-6 border-b border-white/10">
                    <div>
                        {isEditingName ? (
                            <form onSubmit={(e) => { e.preventDefault(); handleSaveName(); }} className="flex items-center gap-2">
                                <input
                                    autoFocus
                                    type="text"
                                    value={newName}
                                    onChange={e => setNewName(e.target.value)}
                                    className="bg-white/10 border border-white/20 text-white text-2xl font-black rounded px-3 py-1 outline-none focus:border-riftbound-accent w-full max-w-sm"
                                />
                                <button type="submit" className="p-2 bg-riftbound-accent text-white rounded hover:bg-riftbound-accentBright transition-colors">
                                    <Save className="w-5 h-5" />
                                </button>
                            </form>
                        ) : (
                            <div className="flex items-center gap-3 group">
                                <h2 className="text-2xl md:text-3xl font-black text-white">{deck.name}</h2>
                                <button
                                    onClick={() => { setNewName(deck.name); setIsEditingName(true); }}
                                    className="opacity-0 group-hover:opacity-100 p-1 text-riftbound-textDim hover:text-white transition-all transform hover:scale-110"
                                >
                                    <Edit2 className="w-4 h-4" />
                                </button>
                            </div>
                        )}
                        {/* Display Total Gameplay Cards */}
                        <p className="text-riftbound-textDim text-sm font-medium mt-1">
                            {deck.format} • Main: {counts.mainDeck} / {MAIN_DECK_MIN} | Side: {counts.sideboard} / {SIDEBOARD_MAX}
                        </p>
                    </div>

                    <div className="flex items-center gap-2 bg-black/40 p-1 rounded-lg">
                        <button
                            onClick={handleExportDeck}
                            className={`px-3 py-1.5 rounded-md text-sm font-bold transition-all text-riftbound-textDim hover:text-white flex items-center gap-1.5 border border-white/5 hover:border-white/20`}
                            title="Export to Text"
                        >
                            <Copy className="w-3.5 h-3.5" /> Export
                        </button>
                        <div className="w-px h-6 bg-white/10 mx-1"></div>
                        <button
                            onClick={handleAddAllMissingToBinder}
                            className={`px-3 py-1.5 rounded-md text-sm font-bold transition-all text-riftbound-textDim hover:text-white flex items-center gap-1.5 border border-white/5 hover:border-white/20`}
                            title="Add All Missing to Binder"
                        >
                            <Download className="w-3.5 h-3.5" /> Add Missing
                        </button>
                        <div className="w-px h-6 bg-white/10 mx-1"></div>
                        <button
                            onClick={() => setActiveTab('CARDS')}
                            className={`px-4 py-1.5 rounded-md text-sm font-bold transition-all ${activeTab === 'CARDS' ? 'bg-riftbound-accent text-white shadow-md' : 'text-riftbound-textDim hover:text-white'}`}
                        >
                            Cards
                        </button>
                        <button
                            onClick={() => setActiveTab('STATS')}
                            className={`px-4 py-1.5 rounded-md text-sm font-bold transition-all ${activeTab === 'STATS' ? 'bg-riftbound-accent text-white shadow-md' : 'text-riftbound-textDim hover:text-white'}`}
                        >
                            Stats
                        </button>
                    </div>
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar pr-2 pb-[20vh]">
                    {activeTab === 'CARDS' ? (
                        Object.keys(deckListGroups).length === 0 ? (
                            <div className="flex flex-col items-center justify-center h-full text-riftbound-textDim">
                                <Plus className="w-12 h-12 mb-4 opacity-20" />
                                <p className="text-center max-w-xs">Click on cards in your collection to add them to this deck.</p>
                            </div>
                        ) : (
                            <div className="flex flex-col gap-6">
                                {deckListGroups.map((group) => (
                                    <div key={group.id}>
                                        <h3 className="text-xs font-bold uppercase tracking-widest text-riftbound-textDim mb-3 border-b border-white/5 pb-2">
                                            {group.title} ({group.items.reduce((acc, curr) => acc + curr.quantity, 0)})
                                        </h3>
                                        <div className="flex flex-col gap-1">
                                            {group.items.map(({ card, quantity, isSideboard }) => {
                                                const owned = binderCards.find(bc => bc.card.cardId === card.cardId)?.quantity || 0;
                                                const totalInDeck = entries.filter(e => e.cardId === card.cardId).reduce((ac, cv) => ac + cv.quantity, 0);
                                                const isBaseRune = card.type === 'RUNE' && !/[a-z]/.test(card.cardId.split('-')[1] || '');
                                                const isMissing = !isBaseRune && (totalInDeck > owned);
                                                const missingCount = isMissing ? (totalInDeck - owned) : 0;
                                                const missingPriceRaw = missingCount * (prices[card.cardId] || 0);
                                                const missingPrice = convertPrice(missingPriceRaw);

                                                const domainColorsMapped = card.domain.map(d => DOMAIN_COLORS[d.toUpperCase()] || DOMAIN_COLORS['COLORLESS']);
                                                const borderGradient = domainColorsMapped.length > 1
                                                    ? `linear-gradient(to right, ${domainColorsMapped.join(', ')})`
                                                    : `linear-gradient(to right, ${domainColorsMapped[0] || DOMAIN_COLORS['COLORLESS']}, ${domainColorsMapped[0] || DOMAIN_COLORS['COLORLESS']})`;

                                                return (
                                                    <div
                                                        key={card.cardId}
                                                        onClick={() => setSelectedCard(card)}
                                                        className="flex items-center cursor-pointer rounded-lg p-2 transition-all relative group shadow-sm hover:shadow-md hover:-translate-y-0.5"
                                                    >
                                                        <div
                                                            className="absolute inset-0 rounded-lg pointer-events-none transition-opacity opacity-50 group-hover:opacity-100"
                                                            style={{
                                                                background: `linear-gradient(#14141d, #14141d) padding-box, ${borderGradient} border-box`,
                                                                border: '1.5px solid transparent'
                                                            }}
                                                        />
                                                        <div className="absolute inset-x-0 inset-y-0 rounded-lg pointer-events-none bg-white/0 group-hover:bg-white/5 transition-colors" />

                                                        <div className="w-8 h-8 rounded-full bg-black/60 shadow-inner flex items-center justify-center text-xs font-bold text-riftbound-accentBright border border-white/5 mr-3 shrink-0 relative z-10">
                                                            {card.cost}
                                                        </div>
                                                        <div className="flex-1 min-w-0 relative z-10">
                                                            <h4 className="text-white font-bold truncate flex items-center gap-2">
                                                                {card.name}
                                                                {isMissing && (
                                                                    <span className="text-[10px] bg-red-900/80 text-red-200 px-1.5 py-0.5 rounded border border-red-500/30 whitespace-nowrap hidden sm:inline-block">
                                                                        Missing {missingCount}
                                                                    </span>
                                                                )}
                                                            </h4>
                                                            <div className="flex items-center gap-2 mt-0.5">
                                                                {card.domain.map(d => (
                                                                    <img key={d} src={`https://lachieburne.github.io/Bounded/domain_images/${d.toLowerCase()}.png`} alt={d} className="w-3 h-3" />
                                                                ))}
                                                                {isMissing && (
                                                                    <span className="text-[10px] text-red-400 font-bold whitespace-nowrap sm:hidden">
                                                                        Missing {missingCount}
                                                                    </span>
                                                                )}
                                                                {isMissing && missingPrice > 0 && (
                                                                    <span className="text-[10px] text-green-400 font-bold">
                                                                        • {getCurrencySymbol()}{missingPrice.toFixed(2)}
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </div>

                                                        <div className="flex items-center gap-3 relative z-10 shrink-0">
                                                            {isMissing && (
                                                                <button
                                                                    onClick={(e) => { e.stopPropagation(); handleAddMissingToBinder(card.cardId, 1); }}
                                                                    className="w-8 h-8 rounded-full bg-black/40 text-riftbound-accentBright hover:text-white hover:bg-riftbound-accent/20 flex items-center justify-center transition-colors border border-white/10"
                                                                    title="Add to Binder"
                                                                >
                                                                    <Download className="w-4 h-4" />
                                                                </button>
                                                            )}

                                                            {card.type !== 'LEGEND' ? (
                                                                <button
                                                                    onClick={(e) => { e.stopPropagation(); handleRemoveCard(card.cardId, isSideboard); }}
                                                                    className="w-8 h-8 rounded-full bg-black/40 text-riftbound-textDim hover:text-white hover:bg-red-500/20 flex items-center justify-center transition-colors border border-white/10"
                                                                >
                                                                    <Minus className="w-4 h-4" />
                                                                </button>
                                                            ) : (
                                                                <div className="w-8 h-8 rounded-full bg-transparent"></div>
                                                            )}

                                                            <div className="flex flex-col items-center justify-center w-8">
                                                                <span className={`text-center font-black ${isMissing ? 'text-red-400' : 'text-white'}`}>
                                                                    {quantity}
                                                                </span>
                                                            </div>

                                                            {card.type !== 'LEGEND' && group.id !== 'Champion' ? (
                                                                <button
                                                                    onClick={(e) => { e.stopPropagation(); handleAddCard(card, isSideboard); }}
                                                                    className="w-8 h-8 rounded-full bg-black/40 text-riftbound-textDim hover:text-white hover:bg-green-500/20 flex items-center justify-center transition-colors border border-white/10"
                                                                >
                                                                    <Plus className="w-4 h-4" />
                                                                </button>
                                                            ) : (
                                                                <div className="w-8 h-8 rounded-full bg-transparent"></div>
                                                            )}
                                                        </div>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )) : (
                        <div className="flex flex-col gap-6 pt-2 pb-6">
                            {/* Estimated Value */}
                            <div className="bg-white/5 rounded-xl p-4 border border-white/10 shadow-lg">
                                <h3 className="text-lg font-bold text-white mb-3">Estimated Value</h3>
                                <div className="text-3xl font-black text-green-400 mb-4 drop-shadow-sm">
                                    {getCurrencySymbol()}{deckStats.totalValue.toFixed(2)}
                                </div>
                                <div className="h-px w-full bg-white/10 mb-4"></div>
                                <div className="flex justify-between items-center text-sm">
                                    <div className="flex flex-col">
                                        <span className="text-riftbound-textDim font-medium">Owned Value</span>
                                        <span className="text-white font-bold">{getCurrencySymbol()}{deckStats.ownedValue.toFixed(2)}</span>
                                    </div>
                                    {deckStats.missingValue > 0 ? (
                                        <div className="flex flex-col items-end">
                                            <span className="text-red-400/80 font-medium">Cost to Finish</span>
                                            <span className="text-red-400 font-bold drop-shadow-sm">{getCurrencySymbol()}{deckStats.missingValue.toFixed(2)}</span>
                                        </div>
                                    ) : (
                                        <div className="flex flex-col items-end">
                                            <span className="text-riftbound-textDim font-medium">Status</span>
                                            <span className="text-green-400 font-bold drop-shadow-sm">Complete</span>
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Deck Composition - SVG Donut Chart */}
                            <div className="bg-black/40 rounded-xl p-6 border border-white/5 flex items-center justify-between">
                                <div className="flex flex-col">
                                    <h3 className="text-sm uppercase tracking-widest font-bold text-riftbound-textDim mb-1">Composition</h3>
                                    <div className="text-2xl font-black text-white">{deckStats.totalMainDeck} Cards</div>
                                    <div className="flex flex-col gap-2 mt-4 text-sm font-bold">
                                        <div className="flex items-center gap-2"><div className="w-3 h-3 rounded-full bg-blue-500"></div>Units: {deckStats.unitCount}</div>
                                        <div className="flex items-center gap-2"><div className="w-3 h-3 rounded-full bg-purple-500"></div>Spells: {deckStats.spellCount}</div>
                                        <div className="flex items-center gap-2"><div className="w-3 h-3 rounded-full bg-amber-500"></div>Gear: {deckStats.gearCount}</div>
                                    </div>
                                </div>
                                <div className="relative w-32 h-32 mr-4 hidden sm:block">
                                    {deckStats.totalMainDeck > 0 ? (() => {
                                        const total = deckStats.totalMainDeck;
                                        const uPct = (deckStats.unitCount / total) * 100;
                                        const sPct = (deckStats.spellCount / total) * 100;
                                        const gPct = (deckStats.gearCount / total) * 100;
                                        return (
                                            <svg viewBox="0 0 36 36" className="w-full h-full -rotate-90 drop-shadow-lg">
                                                {/* Background ring */}
                                                <circle cx="18" cy="18" r="15.9155" fill="transparent" stroke="rgba(255,255,255,0.05)" strokeWidth="4" />
                                                {/* Units */}
                                                {uPct > 0 && <circle cx="18" cy="18" r="15.9155" fill="transparent" stroke="#3b82f6" strokeWidth="4" strokeDasharray={`${uPct} ${100 - uPct}`} strokeDashoffset="0" />}
                                                {/* Spells */}
                                                {sPct > 0 && <circle cx="18" cy="18" r="15.9155" fill="transparent" stroke="#a855f7" strokeWidth="4" strokeDasharray={`${sPct} ${100 - sPct}`} strokeDashoffset={`-${uPct}`} />}
                                                {/* Gear */}
                                                {gPct > 0 && <circle cx="18" cy="18" r="15.9155" fill="transparent" stroke="#f59e0b" strokeWidth="4" strokeDasharray={`${gPct} ${100 - gPct}`} strokeDashoffset={`-${uPct + sPct}`} />}
                                            </svg>
                                        );
                                    })() : (
                                        <div className="w-full h-full rounded-full border-4 border-white/10 flex items-center justify-center text-white/50 text-xs">Empty</div>
                                    )}
                                    <div className="absolute inset-0 flex items-center justify-center text-lg font-black text-white">{deckStats.totalMainDeck}</div>
                                </div>
                            </div>

                            {/* Energy Curve */}
                            <div className="pt-2">
                                <h3 className="text-sm uppercase tracking-widest font-bold text-riftbound-textDim mb-4">Energy Curve</h3>
                                <div className="flex items-end justify-between h-48 gap-1 lg:gap-2 px-1 pb-4">
                                    {(() => {
                                        const keys = Object.keys(deckStats.energyCurve).map(Number);
                                        const maxDynamic = keys.length > 0 ? Math.max(7, ...keys) : 7;
                                        return Array.from({ length: maxDynamic + 1 }, (_, i) => i).map(i => {
                                            const count = deckStats.energyCurve[i] || 0;
                                            const max = Math.max(5, ...Object.values(deckStats.energyCurve));
                                            const heightPct = count > 0 ? Math.max((count / max) * 100, 5) : 0;
                                            return (
                                                <div key={i} className="flex flex-col items-center flex-1 h-full">
                                                    <span className="text-[10px] font-bold text-white mb-2 h-4">{count > 0 ? count : ''}</span>
                                                    <div className="w-full flex-1 rounded-t bg-white/5 relative mt-auto">
                                                        <div
                                                            className="absolute bottom-0 inset-x-0 rounded-t transition-all duration-500 bg-blue-500"
                                                            style={{ height: `${heightPct}%` }}
                                                        ></div>
                                                    </div>
                                                    <span className="text-xs font-bold text-riftbound-textDim mt-2 h-4">{i}</span>
                                                </div>
                                            );
                                        });
                                    })()}
                                </div>
                            </div>

                            {/* Might Curve */}
                            <div className="pt-4">
                                <h3 className="text-sm uppercase tracking-widest font-bold text-riftbound-textDim mb-4">Might Curve</h3>
                                <div className="flex items-end justify-between h-48 gap-1 lg:gap-2 px-1 pb-4">
                                    {(() => {
                                        const keys = Object.keys(deckStats.mightCurve).map(Number);
                                        const maxDynamic = keys.length > 0 ? Math.max(7, ...keys) : 7;
                                        return Array.from({ length: maxDynamic + 1 }, (_, i) => i).map(i => {
                                            const count = deckStats.mightCurve[i] || 0;
                                            const max = Math.max(5, ...Object.values(deckStats.mightCurve));
                                            const heightPct = count > 0 ? Math.max((count / max) * 100, 5) : 0;
                                            return (
                                                <div key={i} className="flex flex-col items-center flex-1 h-full">
                                                    <span className="text-[10px] font-bold text-white mb-2 h-4">{count > 0 ? count : ''}</span>
                                                    <div className="w-full flex-1 rounded-t bg-white/5 relative mt-auto">
                                                        <div
                                                            className="absolute bottom-0 inset-x-0 rounded-t transition-all duration-500 bg-red-600"
                                                            style={{ height: `${heightPct}%` }}
                                                        ></div>
                                                    </div>
                                                    <span className="text-xs font-bold text-riftbound-textDim mt-2 h-4">{i}</span>
                                                </div>
                                            );
                                        });
                                    })()}
                                </div>
                            </div>

                            {/* Domain Breakdown */}
                            <div className="pt-4">
                                <h3 className="text-sm uppercase tracking-widest font-bold text-riftbound-textDim mb-4">Domain Breakdown</h3>
                                <div className="flex items-end justify-between h-48 gap-2 lg:gap-4 px-1 pb-4">
                                    {Object.keys(DOMAIN_COLORS).filter(d => (deckStats.domainBreakdown[d] || 0) > 0).map(domain => {
                                        const count = deckStats.domainBreakdown[domain] || 0;
                                        const max = Math.max(5, ...Object.values(deckStats.domainBreakdown));
                                        const heightPct = count > 0 ? Math.max((count / max) * 100, 5) : 0;
                                        return (
                                            <div key={domain} className="flex flex-col items-center flex-1 h-full">
                                                <span className="text-[10px] font-bold text-white mb-2 h-4">{count > 0 ? count : ''}</span>
                                                <div className="w-full flex-1 rounded-t bg-white/5 relative mt-auto">
                                                    <div
                                                        className="absolute bottom-0 inset-x-0 rounded-t transition-all duration-500 shadow-lg"
                                                        style={{ height: `${heightPct}%`, backgroundColor: DOMAIN_COLORS[domain.toUpperCase()] || DOMAIN_COLORS['COLORLESS'] }}
                                                    ></div>
                                                </div>
                                                <div className="mt-3 w-6 h-6 shrink-0 flex items-center justify-center">
                                                    <img src={`https://lachieburne.github.io/Bounded/domain_images/${domain.toLowerCase()}.png`} alt={domain} className="w-full h-full object-contain drop-shadow" />
                                                </div>
                                            </div>
                                        );
                                    })}
                                    {Object.keys(deckStats.domainBreakdown).length === 0 && (
                                        <div className="w-full h-full flex items-center justify-center text-sm font-bold text-riftbound-textDim/50 pb-10">
                                            No Domains Active
                                        </div>
                                    )}
                                </div>
                            </div>

                        </div>
                    )}
                </div>
            </div>

            {/* Modals */}
            {selectedCard && (
                <CardDetailModal
                    item={{
                        card: selectedCard,
                        quantity: binderCards.find(bc => bc.card.cardId === selectedCard.cardId)?.quantity || 0
                    }}
                    onClose={() => setSelectedCard(null)}
                />
            )}
        </div>
    );
};
