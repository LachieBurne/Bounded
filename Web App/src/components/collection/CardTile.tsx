import { Ban } from 'lucide-react';
import type { CardWithQuantity } from '../../models/card';
import { domainStripe } from '../../lib/style';
import { QuantityStepper } from '../rift/Rift';

/**
 * A card in a collection grid: the art, a domain stripe and the owned count. Hovering or
 * tabbing into it shows a − / + stepper, so adding a copy is one click without opening the card.
 */
export function CardTile({ item, onOpen, onQuantity }: {
    item: CardWithQuantity;
    onOpen: () => void;
    onQuantity: (next: number) => void;
}) {
    const { card, quantity } = item;
    const owned = quantity > 0;
    const banned = card.bannedIn?.includes('STANDARD');
    const landscape = card.orientation === 'landscape';
    return (
        <div className="tile relative flex flex-col gap-1.5 min-w-0">
            <button
                type="button"
                onClick={onOpen}
                aria-label={`${card.name}, ${quantity} owned`}
                className="relative block w-full aspect-[744/1039] bg-surface overflow-hidden cursor-pointer hover:outline-2 hover:outline-gold hover:outline-offset-2"
            >
                {landscape ? (
                    <span className="absolute inset-0 flex items-center justify-center">
                        <img src={card.imageUrl} alt="" loading="lazy" className={`-rotate-90 w-[139.7%] max-w-none ${owned ? '' : 'opacity-40'}`} />
                    </span>
                ) : (
                    <img src={card.imageUrl} alt="" loading="lazy" className={`w-full h-full object-cover ${owned ? '' : 'opacity-40'}`} />
                )}
                {banned && (
                    <span className="absolute inset-x-0 top-0 h-[22px] flex items-center justify-center gap-1 bg-banned/95 text-white rift-label text-xs tracking-[0.1em]">
                        <Ban aria-hidden="true" className="w-3 h-3" strokeWidth={3} />Banned
                    </span>
                )}
                <span className={`absolute right-0 bottom-0 min-w-[30px] h-[26px] px-1.5 flex items-center justify-center bg-ink border rift-number text-base ${owned ? 'border-gold-deep text-gold' : 'border-line text-muted'}`}>
                    ×{quantity}
                </span>
            </button>
            <span aria-hidden="true" className="h-1" style={{ background: domainStripe(card.domain) }} />
            <span className={`text-sm font-semibold truncate ${owned ? 'text-ivory' : 'text-muted'}`}>{card.name}</span>
            <div className="tile-actions absolute left-1.5 right-1.5 bottom-[46px] flex justify-center bg-ink/90 border border-gold-deep">
                <QuantityStepper value={quantity} onChange={onQuantity} label={card.name} size="sm" />
            </div>
        </div>
    );
}
