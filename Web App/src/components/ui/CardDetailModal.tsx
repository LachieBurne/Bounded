import React from 'react';
import { X, Plus, Minus } from 'lucide-react';
import type { CardWithQuantity } from '../../models/card';
import { useCards } from '../../context/CardContext';

interface CardDetailModalProps {
    item: CardWithQuantity;
    onClose: () => void;
}

export const CardDetailModal: React.FC<CardDetailModalProps> = ({ item, onClose }) => {
    const { updateCardQuantity } = useCards();
    const [price, setPrice] = React.useState<number | null>(null);

    React.useEffect(() => {
        fetch('https://lachieburne.github.io/Riftbounded/prices.json')
            .then(res => res.json())
            .then(data => {
                const key = item.card.cardId;
                if (data[key] !== undefined) {
                    setPrice(data[key]);
                }
            })
            .catch(err => console.error("Could not load prices", err));
    }, [item]);

    const handleBackdropClick = (e: React.MouseEvent<HTMLDivElement>) => {
        if (e.target === e.currentTarget) {
            onClose();
        }
    };

    const handleIncrement = () => {
        updateCardQuantity(item.card.cardId, item.quantity + 1);
    };

    const handleDecrement = () => {
        if (item.quantity > 0) {
            updateCardQuantity(item.card.cardId, item.quantity - 1);
        }
    };

    return (
        <div
            className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-fade-in"
            onClick={handleBackdropClick}
        >
            <div className="relative w-full max-w-4xl max-h-[90vh] overflow-y-auto bg-riftbound-darkest rounded-2xl shadow-2xl border border-white/10 flex flex-col md:flex-row animate-scale-in">

                {/* Close Button */}
                <button
                    onClick={onClose}
                    className="absolute top-4 right-4 z-10 p-2 bg-black/50 hover:bg-white/10 text-white rounded-full backdrop-blur-md transition-colors"
                >
                    <X className="w-5 h-5" />
                </button>

                {/* Left Side: Card Image */}
                <div className="md:w-1/2 p-6 md:p-10 flex items-center justify-center bg-riftbound-darker relative overflow-hidden">
                    {/* Glow behind image */}
                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-3/4 h-3/4 bg-riftbound-accent/20 blur-[80px] rounded-full" />

                    <img
                        src={item.card.imageUrl.replace("http://", "https://").replace(":80", "")}
                        alt={item.card.name}
                        className={`relative z-10 w-full max-w-sm rounded-2xl shadow-2xl ${item.card.type === 'BATTLEFIELD' ? '-rotate-90 scale-[0.75] my-20' : ''}`}
                    />
                </div>

                {/* Right Side: Details */}
                <div className="md:w-1/2 p-6 md:p-10 flex flex-col">
                    <div className="flex-1">
                        <div className="flex items-center justify-between mb-2">
                            <div className="flex items-center gap-2">
                                <span className="text-xs font-bold tracking-widest text-riftbound-accentBright uppercase">
                                    {item.card.type}
                                </span>
                                {item.card.domain.map(d => (
                                    <img key={d} src={`https://lachieburne.github.io/Riftbounded/domain_images/${d.toLowerCase()}.png`} alt={d} className="w-5 h-5" title={d} />
                                ))}
                            </div>
                            <div className="flex items-center gap-2">
                                <img src={`https://lachieburne.github.io/Riftbounded/rarity_images/${item.card.rarity.toLowerCase()}.png`} alt={item.card.rarity} className="h-4" title={item.card.rarity} />
                                <span className="text-xs font-medium text-riftbound-textDim bg-white/5 py-1 px-3 rounded-full border border-white/10">
                                    {item.card.setCode} • {item.card.cardId.replace(`${item.card.setCode}-`, '')}
                                </span>
                            </div>
                        </div>

                        <h2 className="text-3xl font-black text-white mb-2">
                            {item.card.name}
                        </h2>

                        {item.card.artistName && (
                            <p className="text-sm text-riftbound-textDim italic mb-6">Illustrated by {item.card.artistName}</p>
                        )}

                        {/* Stats Row */}
                        <div className="flex gap-4 mb-6">
                            <div className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 flex-1 text-center">
                                <span className="block text-xs text-riftbound-textDim uppercase font-bold mb-1">Cost</span>
                                <span className="text-xl font-black text-white">{item.card.cost}</span>
                            </div>
                            <div className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 flex-1 text-center">
                                <span className="block text-xs text-riftbound-textDim uppercase font-bold mb-1">Might</span>
                                <span className="text-xl font-black text-white">{item.card.might}</span>
                            </div>
                            <div className="bg-white/5 border border-white/10 rounded-xl px-4 py-3 flex-1 text-center">
                                <span className="block text-xs text-riftbound-textDim uppercase font-bold mb-1">Price</span>
                                <span className="text-xl font-black text-white">{price !== null ? `$${price.toFixed(2)}` : '---'}</span>
                            </div>
                        </div>

                        {/* Rules Text */}
                        <div className="prose prose-invert max-w-none">
                            <p className="text-lg leading-relaxed text-riftbound-textPrimary whitespace-pre-wrap">
                                {item.card.text}
                            </p>
                            {item.card.flavorText && (
                                <p className="text-riftbound-textDim italic mt-4 border-l-2 border-white/10 pl-4">
                                    "{item.card.flavorText}"
                                </p>
                            )}
                        </div>
                    </div>

                    {/* Collection Controller */}
                    <div className="pt-8 mt-8 border-t border-white/10">
                        <h3 className="text-sm font-bold text-riftbound-textDim uppercase mb-4 text-center">Collection Status</h3>
                        <div className="flex items-center justify-center gap-6 glass-panel p-4 rounded-xl max-w-xs mx-auto">
                            <button
                                onClick={handleDecrement}
                                disabled={item.quantity === 0}
                                className="w-12 h-12 flex items-center justify-center rounded-full bg-white/5 hover:bg-white/10 disabled:opacity-30 disabled:hover:bg-white/5 text-white transition-colors"
                            >
                                <Minus className="w-6 h-6" />
                            </button>

                            <div className="flex flex-col items-center min-w-[3rem]">
                                <span className="text-3xl font-black text-white">{item.quantity}</span>
                                <span className="text-[10px] text-riftbound-textDim uppercase font-bold tracking-wider">Owned</span>
                            </div>

                            <button
                                onClick={handleIncrement}
                                className="w-12 h-12 flex items-center justify-center rounded-full bg-riftbound-accent hover:bg-riftbound-accentBright text-white shadow-lg shadow-riftbound-accent/30 transition-all active:scale-95"
                            >
                                <Plus className="w-6 h-6" />
                            </button>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
};
