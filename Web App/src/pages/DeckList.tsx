import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDecks } from '../context/DeckContext';
import { useCards } from '../context/CardContext';
import { Plus, Trash2, Edit2 } from 'lucide-react';

const DeckList: React.FC = () => {
    const { decks, loadingDecks, createDeck, deleteDeck } = useDecks();
    const { masterCards } = useCards();
    const navigate = useNavigate();

    const [isCreating, setIsCreating] = useState(false);
    const [newDeckName, setNewDeckName] = useState('');

    const handleCreateDeck = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newDeckName.trim()) return;

        const newDeck = {
            deckId: crypto.randomUUID(),
            name: newDeckName.trim(),
            format: 'CONSTRUCTED' as const,
            legendCardId: null,
            championCardId: null,
            coverCardUrl: null,
            mainCount: 0,
            sideCount: 0,
            lastModified: Date.now()
        };

        await createDeck(newDeck);
        setIsCreating(false);
        setNewDeckName('');
        navigate(`/decks/${newDeck.deckId}`);
    };

    if (loadingDecks) {
        return (
            <div className="flex-1 flex items-center justify-center p-8">
                <div className="w-12 h-12 border-4 border-white/10 border-t-riftbound-accent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="flex-1 overflow-y-auto p-4 md:p-8 custom-scrollbar">
            <div className="max-w-7xl mx-auto">
                <div className="flex justify-between items-center mb-8">
                    <h1 className="text-3xl md:text-5xl font-black text-white uppercase tracking-tight">Your Decks</h1>
                    <button
                        onClick={() => setIsCreating(true)}
                        className="flex items-center gap-2 bg-riftbound-accent text-white px-4 md:px-6 py-2 md:py-3 rounded-xl font-bold hover:bg-riftbound-accentBright transition-colors shadow-lg shadow-riftbound-accent/20"
                    >
                        <Plus className="w-5 h-5" />
                        <span className="hidden sm:inline">New Deck</span>
                    </button>
                </div>

                {/* Create Deck Form */}
                {isCreating && (
                    <div className="bg-white/5 border border-white/10 p-6 rounded-2xl mb-8 glass-panel animate-in fade-in slide-in-from-top-4">
                        <form onSubmit={handleCreateDeck} className="flex flex-col sm:flex-row gap-4">
                            <input
                                autoFocus
                                type="text"
                                value={newDeckName}
                                onChange={e => setNewDeckName(e.target.value)}
                                placeholder="Enter deck name..."
                                className="flex-1 bg-white/5 border border-white/10 rounded-xl px-4 py-3 text-white focus:outline-none focus:border-riftbound-accent"
                            />
                            <div className="flex gap-2">
                                <button
                                    type="button"
                                    onClick={() => { setIsCreating(false); setNewDeckName(''); }}
                                    className="px-6 py-3 rounded-xl font-bold text-white bg-white/10 hover:bg-white/20 transition-colors"
                                >
                                    Cancel
                                </button>
                                <button
                                    type="submit"
                                    disabled={!newDeckName.trim()}
                                    className="px-6 py-3 rounded-xl font-bold text-white bg-riftbound-accent hover:bg-riftbound-accentBright transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    Create
                                </button>
                            </div>
                        </form>
                    </div>
                )}

                {/* Decks Grid */}
                {decks.length === 0 && !isCreating ? (
                    <div className="text-center py-20 bg-white/5 rounded-2xl border border-white/10 glass-panel">
                        <div className="w-20 h-20 mx-auto bg-white/5 rounded-full flex items-center justify-center mb-6">
                            <Plus className="w-10 h-10 text-riftbound-textDim" />
                        </div>
                        <h3 className="text-2xl font-bold text-white mb-2">No Decks Yet</h3>
                        <p className="text-riftbound-textDim max-w-md mx-auto">
                            You haven't constructed any decks. Create one to begin assembling your forces for the Rift!
                        </p>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
                        {decks.map(deck => {
                            // Find champion/legend logic (placeholder for actual URLs)
                            let bgImage = deck.coverCardUrl;
                            if (!bgImage && deck.championCardId) {
                                const champ = masterCards.find(c => c.cardId === deck.championCardId);
                                if (champ) bgImage = champ.imageUrl;
                            }
                            if (!bgImage && deck.legendCardId) {
                                const leg = masterCards.find(c => c.cardId === deck.legendCardId);
                                if (leg) bgImage = leg.imageUrl;
                            }

                            // Placeholder default bg
                            bgImage = bgImage || 'https://lachieburne.github.io/Riftbounded/card_images/OGN-001.png';

                            return (
                                <div key={deck.deckId} onClick={() => navigate(`/decks/${deck.deckId}`)} className="group relative rounded-2xl overflow-hidden border border-white/10 aspect-[3/2] flex flex-col justify-end cursor-pointer transition-all duration-300 hover:scale-[1.02] hover:border-riftbound-accent shadow-xl">
                                    {/* Cover Image */}
                                    <div className="absolute inset-0 z-0">
                                        <img src={bgImage} alt="Cover" className="w-full h-full object-cover object-top opacity-50 group-hover:opacity-70 transition-opacity" />
                                        <div className="absolute inset-0 bg-gradient-to-t from-riftbound-darkest via-riftbound-darkest/80 to-transparent"></div>
                                    </div>

                                    {/* Content */}
                                    <div className="relative z-10 p-5 flex flex-col gap-2">
                                        <div className="flex flex-col gap-1">
                                            <h3 className="text-xl font-bold text-white line-clamp-2 drop-shadow-md leading-tight">{deck.name}</h3>
                                            <div className="flex items-center justify-between mt-1">
                                                <div className="text-sm text-riftbound-textDim font-medium">
                                                    {deck.format}
                                                </div>
                                                <div className="flex items-center">
                                                    <span className="text-[10px] md:text-xs font-bold bg-white/10 px-2 py-1 rounded text-riftbound-textPrimary border border-white/5 shadow-inner backdrop-blur-sm">
                                                        Main: <span className="text-riftbound-accentBright">{deck.mainCount ?? 0}/40</span><span className="mx-1 text-white/30">|</span>Side: <span className="text-riftbound-accentBright">{deck.sideCount ?? 0}/8</span>
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Hover Actions Float */}
                                    <div className="absolute inset-0 z-20 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-4 backdrop-blur-sm">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); navigate(`/decks/${deck.deckId}`); }}
                                            className="w-12 h-12 bg-riftbound-accent rounded-full flex items-center justify-center text-white hover:scale-110 transition-transform shadow-lg shadow-riftbound-accent/40"
                                            title="Edit Deck"
                                        >
                                            <Edit2 className="w-5 h-5" />
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); if (window.confirm('Delete deck entirely?')) deleteDeck(deck.deckId); }}
                                            className="w-12 h-12 bg-red-600 rounded-full flex items-center justify-center text-white hover:scale-110 transition-transform shadow-lg shadow-red-600/40"
                                            title="Delete Deck"
                                        >
                                            <Trash2 className="w-5 h-5" />
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
};

export default DeckList;
