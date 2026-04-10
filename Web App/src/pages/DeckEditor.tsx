import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, Minus, Save, Edit2, Copy, Download } from 'lucide-react';
import { collection, onSnapshot, doc, setDoc, deleteDoc, updateDoc } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import { useCards } from '../context/CardContext';
import { useDecks } from '../context/DeckContext';
import { usePrices } from '../context/PriceContext';
import { CardDetailModal } from '../components/ui/CardDetailModal';
import type { Card } from '../models/card';
import type { Deck, DeckEntry } from '../models/deck';
import { SleekSearchBar } from '../components/ui/SleekSearchBar';

const DOMAIN_COLORS: Record<string, string> = {
    'FURY': '#ef4444',
    'BODY': '#f97316',
    'ORDER': '#eab308',
    'MIND': '#3b82f6',
    'CALM': '#22c55e',
    'CHAOS': '#a855f7',
    'COLORLESS': '#9ca3af'
};

const DeckBuildStep = {
    SELECT_LEGEND: 0,
    SELECT_CHAMPION: 1,
    SELECT_RUNES: 2,
    SELECT_BATTLEFIELDS: 3,
    SELECT_MAIN: 4,
    SELECT_SIDEBOARD: 5,
    COMPLETE: 6
} as const;

const STEP_LABELS = [
    { title: "Select Legend", instruction: "Choose 1 Legend card to lead your deck." },
    { title: "Select Champion", instruction: "Choose the Champion unit that matches your Legend." },
    { title: "Select Runes", instruction: "Choose exactly 12 Runes compatible with your Legend." },
    { title: "Select Battlefields", instruction: "Choose exactly 3 Battlefields." },
    { title: "Build Main Deck", instruction: "Select 39 cards (Units, Spells, etc)." },
    { title: "Sideboard", instruction: "Select up to 8 cards (Optional)." },
    { title: "Deck Complete", instruction: "Your deck is ready!" }
];

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

    const [deck, setDeck] = useState<Deck | null>(null);
    const [entries, setEntries] = useState<DeckEntry[]>([]);
    const [searchQuery, setSearchQuery] = useState('');
    const [isEditingName, setIsEditingName] = useState(false);
    const [newName, setNewName] = useState('');
    const [selectedCard, setSelectedCard] = useState<Card | null>(null);
    const [showUnowned, setShowUnowned] = useState(true);
    const [activeTab, setActiveTab] = useState<'CARDS' | 'STATS'>('CARDS');

    const { prices, convertPrice, getCurrencySymbol } = usePrices();

    useEffect(() => {
        if (!deckId || loadingDecks) return;
        const found = decks.find(d => d.deckId === deckId);
        if (found) {
            setDeck(found);
            setNewName(found.name);
        } else {
            navigate('/decks'); // Deck not found
        }
    }, [deckId, decks, loadingDecks, navigate]);

    useEffect(() => {
        if (!deckId) return;
        const user = auth.currentUser;
        if (!user) return;

        const entriesRef = collection(db, 'users', user.uid, 'decks', deckId, 'entries');
        const unsubscribe = onSnapshot(entriesRef, snapshot => {
            const fetched = snapshot.docs.map(doc => doc.data() as DeckEntry);
            setEntries(fetched);
        });

        return () => unsubscribe();
    }, [deckId]);

    // Calculate current step based on deck contents
    const currentStep = useMemo(() => {
        if (!deck) return DeckBuildStep.SELECT_LEGEND;

        const main = entries.filter(e => !e.isSideboard);
        const side = entries.filter(e => e.isSideboard);

        const runes = main.reduce((acc, curr) => acc + (masterCards.find(c => c.cardId === curr.cardId)?.type === 'RUNE' ? curr.quantity : 0), 0);
        const battlefields = main.reduce((acc, curr) => acc + (masterCards.find(c => c.cardId === curr.cardId)?.type === 'BATTLEFIELD' ? curr.quantity : 0), 0);
        const totalMain = main.reduce((acc, curr) => acc + curr.quantity, 0);
        const variableCards = totalMain - 17; // Assuming 12 runes + 3 battlefields + 1 legend + 1 champ are in main? Actually android logic says totalMain - 17. Wait Android says: totalMain = main.sumOf { it.deckQuantity }. variableCards = totalMain - 17.
        const sideCount = side.reduce((acc, curr) => acc + curr.quantity, 0);

        if (!deck.legendCardId) return DeckBuildStep.SELECT_LEGEND;
        if (!deck.championCardId) return DeckBuildStep.SELECT_CHAMPION;
        if (runes < 12) return DeckBuildStep.SELECT_RUNES;
        if (battlefields < 3) return DeckBuildStep.SELECT_BATTLEFIELDS;
        if (variableCards < 39) return DeckBuildStep.SELECT_MAIN;
        if (sideCount < 8) return DeckBuildStep.SELECT_SIDEBOARD;
        return DeckBuildStep.COMPLETE;
    }, [deck, entries, masterCards]);

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

        const gameplayEntries = entries.filter(e => {
            const m = masterCards.find(c => c.cardId === e.cardId);
            return m && !e.isSideboard && m.type !== 'LEGEND' && m.type !== 'RUNE' && m.type !== 'BATTLEFIELD';
        });

        gameplayEntries.forEach(entry => {
            const master = masterCards.find(m => m.cardId === entry.cardId);
            if (!master) return;
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
    }, [entries, masterCards, binderCards, prices, convertPrice]);

    const handleSaveName = async () => {
        if (!deckId || !newName.trim() || newName === deck?.name) {
            setIsEditingName(false);
            return;
        }
        await renameDeck(deckId, newName.trim());
        setIsEditingName(false);
    };

    const updateEntry = async (cardId: string, quantity: number, isSideboard = false) => {
        const user = auth.currentUser;
        if (!user || !deckId) return;

        const docId = `${cardId}_${isSideboard}`;
        const docRef = doc(db, 'users', user.uid, 'decks', deckId, 'entries', docId);

        if (quantity > 0) {
            await setDoc(docRef, { deckId, cardId, quantity, isSideboard });
        } else {
            await deleteDoc(docRef);
        }

        const nonMainTypes: string[] = ['LEGEND', 'BATTLEFIELD', 'RUNE'];

        const newTotalMain = entries.filter(e => {
            if (e.isSideboard || (e.cardId === cardId && !e.isSideboard)) return false;
            const master = masterCards.find(m => m.cardId === e.cardId);
            return master && !nonMainTypes.includes(master.type);
        }).reduce((acc, curr) => acc + curr.quantity, 0) + (isSideboard || nonMainTypes.includes(masterCards.find(m => m.cardId === cardId)?.type || '') ? 0 : Math.max(0, quantity));

        const newTotalSide = entries.filter(e => e.isSideboard && !(e.cardId === cardId && e.isSideboard)).reduce((acc, curr) => acc + curr.quantity, 0) + (isSideboard ? Math.max(0, quantity) : 0);

        const deckRef = doc(db, 'users', user.uid, 'decks', deckId);

        // Special field handling for Legend / Champion setting directly on Deck doc
        const deckUpdate: Partial<Deck> = {
            mainCount: newTotalMain,
            sideCount: newTotalSide,
            lastModified: Date.now()
        };

        const cardMaster = masterCards.find(c => c.cardId === cardId);
        if (cardMaster) {
            // Note: DeckBuildStep enum checks below only take effect if the step logically aligns matching what we added.
            if (currentStep === DeckBuildStep.SELECT_LEGEND && quantity > 0 && !isSideboard) {
                deckUpdate.legendCardId = cardId;
                deckUpdate.coverCardUrl = cardMaster.imageUrl;
            } else if (quantity === 0 && deck?.legendCardId === cardId) {
                deckUpdate.legendCardId = null;
                deckUpdate.coverCardUrl = null;
            } else if (currentStep === DeckBuildStep.SELECT_CHAMPION && quantity > 0 && !isSideboard) {
                deckUpdate.championCardId = cardId;
            } else if (quantity === 0 && deck?.championCardId === cardId) {
                deckUpdate.championCardId = null;
            }
        }

        await updateDoc(deckRef, deckUpdate);
    };

    const handleAddCard = (card: Card, targetIsSideboard?: boolean) => {
        const isSideboard = targetIsSideboard !== undefined ? targetIsSideboard : (currentStep === DeckBuildStep.SELECT_SIDEBOARD);

        const mainCopies = entries.find(e => e.cardId === card.cardId && !e.isSideboard)?.quantity || 0;
        const sideCopies = entries.find(e => e.cardId === card.cardId && e.isSideboard)?.quantity || 0;

        const currentQtyInTarget = isSideboard ? sideCopies : mainCopies;

        if (isSideboard) {
            const sideTotal = entries.filter(e => e.isSideboard).reduce((ac, cv) => ac + cv.quantity, 0);
            if (sideTotal >= 8) return;
        } else {
            if (card.type === 'RUNE') {
                const runesTotal = entries.filter(e => !e.isSideboard && masterCards.find(m => m.cardId === e.cardId)?.type === 'RUNE').reduce((ac, cv) => ac + cv.quantity, 0);
                if (runesTotal >= 12) return;
            } else if (card.type === 'BATTLEFIELD') {
                const bfTotal = entries.filter(e => !e.isSideboard && masterCards.find(m => m.cardId === e.cardId)?.type === 'BATTLEFIELD').reduce((ac, cv) => ac + cv.quantity, 0);
                if (bfTotal >= 3) return;
            } else if (card.cardId !== deck?.legendCardId && card.cardId !== deck?.championCardId && currentStep !== DeckBuildStep.SELECT_LEGEND && currentStep !== DeckBuildStep.SELECT_CHAMPION) {
                const mainTotal = entries.filter(e => !e.isSideboard && e.cardId !== deck?.legendCardId && e.cardId !== deck?.championCardId && masterCards.find(m => m.cardId === e.cardId)?.type !== 'RUNE' && masterCards.find(m => m.cardId === e.cardId)?.type !== 'BATTLEFIELD').reduce((ac, cv) => ac + cv.quantity, 0);
                if (mainTotal >= 39) return;
            }
        }

        if (currentStep === DeckBuildStep.SELECT_LEGEND || currentStep === DeckBuildStep.SELECT_CHAMPION) {
            if (currentQtyInTarget >= 1) return; // Only 1 legend/champ allowed
        } else if (card.type === 'BATTLEFIELD') {
            if (mainCopies + sideCopies >= 1) return; // Only 1 copy of each battlefield allowed
        } else if (card.type !== 'RUNE') {
            if (mainCopies + sideCopies >= 3) return; // General 3-copy rule for non-runes
        }

        updateEntry(card.cardId, currentQtyInTarget + 1, isSideboard);
    };

    const handleRemoveCard = (cardId: string, fromSideboard?: boolean) => {
        if (cardId === deck?.legendCardId && currentStep !== DeckBuildStep.SELECT_LEGEND) return; // Prevent removing Legend after step 1

        let isSideboard = false;
        if (fromSideboard !== undefined) {
            isSideboard = fromSideboard;
        } else {
            const sideQuantity = entries.find(e => e.cardId === cardId && e.isSideboard)?.quantity || 0;
            const mainQuantity = entries.find(e => e.cardId === cardId && !e.isSideboard)?.quantity || 0;

            if (currentStep === DeckBuildStep.SELECT_SIDEBOARD) {
                isSideboard = sideQuantity > 0; // If they have it in sideboard, remove it there first
            } else {
                isSideboard = mainQuantity === 0 && sideQuantity > 0;
            }
        }

        const existing = entries.find(e => e.cardId === cardId && e.isSideboard === isSideboard);
        if (!existing) {
            // Fallback just in case
            const fallback = entries.find(e => e.cardId === cardId && e.isSideboard === !isSideboard);
            if (fallback) updateEntry(cardId, fallback.quantity - 1, !isSideboard);
            return;
        }
        updateEntry(cardId, existing.quantity - 1, isSideboard);
    };

    const displayCards = useMemo(() => {
        const legendCard = masterCards.find(c => c.cardId === deck?.legendCardId);
        const legendDomains = legendCard?.domain || [];

        let results = binderCards.filter(bc => {
            const card = bc.card;

            // Staged filter constraints
            switch (currentStep) {
                case DeckBuildStep.SELECT_LEGEND:
                    if (card.type !== 'LEGEND') return false;
                    break;
                case DeckBuildStep.SELECT_CHAMPION: {
                    const legendName = legendCard?.name || "";
                    const baseName = legendName.split(" - ")[0]?.trim() || "";
                    if (!baseName || !card.name.toLowerCase().startsWith(baseName.toLowerCase()) || card.type !== 'UNIT') return false;
                    break;
                }
                case DeckBuildStep.SELECT_RUNES:
                    if (card.type !== 'RUNE' || !card.domain.every(d => legendDomains.includes(d))) return false;
                    break;
                case DeckBuildStep.SELECT_BATTLEFIELDS:
                    if (card.type !== 'BATTLEFIELD') return false;
                    break;
                case DeckBuildStep.SELECT_MAIN:
                case DeckBuildStep.SELECT_SIDEBOARD: {
                    const isToken = card.cost === 0 && card.domain.includes('COLORLESS') as unknown as boolean; // Simplification
                    if (isToken || card.type === 'BATTLEFIELD' || card.type === 'LEGEND' || card.type === 'RUNE') return false;
                    if (!card.domain.every(d => legendDomains.includes(d))) return false;
                    break;
                }
                case DeckBuildStep.COMPLETE:
                    return false;
            }
            return true;
        });

        if (searchQuery.trim()) {
            const lowerQuery = searchQuery.toLowerCase();
            results = results.filter(bc =>
                bc.card.name.toLowerCase().includes(lowerQuery) ||
                bc.card.type.toLowerCase().includes(lowerQuery)
            );
        }

        // Handle 'Show Unowned' toggle and Sort
        let unownedFiltered = showUnowned ? results : results.filter(bc => {
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
    }, [binderCards, searchQuery, currentStep, deck, masterCards, showUnowned]);

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
                        style={{ width: `${(currentStep / 6) * 100}%` }}
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
                        <h3 className="text-xl font-black text-white">{STEP_LABELS[currentStep]?.title || "Deck Complete"}</h3>
                        <p className="text-sm text-riftbound-accentBright font-bold">{STEP_LABELS[currentStep]?.instruction}</p>
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
                                    onClick={() => setIsEditingName(true)}
                                    className="opacity-0 group-hover:opacity-100 p-1 text-riftbound-textDim hover:text-white transition-all transform hover:scale-110"
                                >
                                    <Edit2 className="w-4 h-4" />
                                </button>
                            </div>
                        )}
                        {/* Display Total Gameplay Cards */}
                        <p className="text-riftbound-textDim text-sm font-medium mt-1">
                            {deck.format} • Main: {entries.filter(e => !e.isSideboard && !['LEGEND', 'BATTLEFIELD', 'RUNE'].includes(masterCards.find(m => m.cardId === e.cardId)?.type || '')).reduce((a, c) => a + c.quantity, 0)} / 40 | Side: {entries.filter(e => e.isSideboard).reduce((a, c) => a + c.quantity, 0)} / 8
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
                                                                    <img key={d} src={`https://lachieburne.github.io/Riftbounded/domain_images/${d.toLowerCase()}.png`} alt={d} className="w-3 h-3" />
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
                                                    <img src={`https://lachieburne.github.io/Riftbounded/domain_images/${domain.toLowerCase()}.png`} alt={domain} className="w-full h-full object-contain drop-shadow" />
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
