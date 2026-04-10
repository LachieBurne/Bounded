import React from 'react';
import { Plus, Minus } from 'lucide-react';
import type { CardWithQuantity } from '../../models/card';
import { useCards } from '../../context/CardContext';

interface CardGridProps {
    cards: CardWithQuantity[];
    onCardClick?: (card: CardWithQuantity) => void;
}

export const CardGrid: React.FC<CardGridProps> = ({ cards, onCardClick }) => {
    const { updateCardQuantity } = useCards();

    return (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-4 lg:gap-6 p-4">
            {cards.map((item) => (
                <div
                    key={item.card.cardId}
                    onClick={() => onCardClick?.(item)}
                    className="relative group cursor-pointer transition-all duration-300 hover:-translate-y-2 hover:scale-[1.02]"
                >
                    {/* Quantity Badge */}
                    <div className={`absolute -top-3 -right-3 z-10 w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm shadow-xl border-2 border-riftbound-darkest
            ${item.quantity > 0
                            ? 'bg-riftbound-accent text-white'
                            : 'bg-riftbound-darker text-riftbound-textDim'}`}>
                        {item.quantity}
                    </div>

                    {/* Card Image Wrapper */}
                    <div className="relative w-full aspect-[63/88] flex items-center justify-center rounded-xl">
                        <img
                            src={item.card.imageUrl.replace("http://", "https://").replace(":80", "")}
                            alt={item.card.name}
                            className={`shrink-0 shadow-lg transition-all duration-300 origin-center rounded-xl ${item.quantity === 0 ? 'opacity-40 grayscale-[50%]' : 'opacity-100'
                                } ${item.card.type === 'BATTLEFIELD'
                                    ? '-rotate-90 w-[139.7%] max-w-none'
                                    : 'w-full h-full object-cover'
                                }`}
                            loading="lazy"
                        />
                    </div>

                    {/* Glow Behind the card */}
                    {item.quantity > 0 && (
                        <div className="absolute -inset-2 bg-riftbound-accent z-[-1] opacity-0 group-hover:opacity-20 blur-xl transition-opacity duration-500 rounded-xl" />
                    )}

                    {/* Inline Quantity Controls */}
                    <div className="flex items-center justify-center gap-3 mt-3 relative z-20">
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                if (item.quantity > 0) {
                                    updateCardQuantity(item.card.cardId, item.quantity - 1);
                                }
                            }}
                            className="w-8 h-8 rounded-full bg-black/60 hover:bg-red-500/80 hover:scale-110 flex items-center justify-center shadow-lg transition-all text-white border border-white/20"
                        >
                            <Minus className="w-4 h-4" />
                        </button>
                        <span className="text-white font-black min-w-[2rem] text-center drop-shadow">{item.quantity}</span>
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                updateCardQuantity(item.card.cardId, item.quantity + 1);
                            }}
                            className="w-8 h-8 rounded-full bg-black/60 hover:bg-green-500/80 hover:scale-110 flex items-center justify-center shadow-lg transition-all text-white border border-white/20"
                        >
                            <Plus className="w-4 h-4" />
                        </button>
                    </div>
                </div>
            ))}
        </div>
    );
};
