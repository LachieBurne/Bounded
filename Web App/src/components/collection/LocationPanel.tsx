import { useState } from 'react';
import { MapPin, X } from 'lucide-react';
import { useLocations } from '../../context/LocationContext';
import { addPlace, normalised, setCount, unplaced, type Places } from '../../lib/places';
import { QuantityStepper, RiftLabel } from '../rift/Rift';

/**
 * Where you keep a card's copies: places like "Red binder" or "Deck box" with a count each, and
 * how many aren't anywhere yet. Optional, and empty until you add a place. Adding a place puts
 * every unplaced copy there in one tap; the steppers move copies between a place and unplaced.
 */
export function LocationPanel({ cardId, cardName, quantity }: { cardId: string; cardName: string; quantity: number }) {
    const { places, known, savePlaces } = useLocations();
    const [draft, setDraft] = useState('');
    const [error, setError] = useState<string | null>(null);

    const stored = places.get(cardId);
    const placed = normalised(stored, quantity);
    const free = unplaced(stored, quantity);
    const onCard = new Set(placed.map(([name]) => name.toLowerCase()));
    const suggestions = known.filter(name => !onCard.has(name.toLowerCase())).slice(0, 6);

    const save = async (next: Places) => {
        setError(null);
        try {
            await savePlaces(cardId, next, quantity);
        } catch (e) {
            console.error(e);
            setError("Couldn't save the location. Try again.");
        }
    };
    const add = (name: string) => {
        if (!name.trim() || free === 0) return;
        setDraft('');
        void save(addPlace(stored, quantity, name));
    };

    if (quantity === 0) {
        return (
            <section aria-label="Location" className="flex items-center gap-2 text-sm text-muted">
                <MapPin aria-hidden="true" className="w-4 h-4" />Add a copy to note where you keep it.
            </section>
        );
    }

    return (
        <section aria-label="Location" className="flex flex-col gap-3 py-3 px-[18px] bg-surface/90 border border-line">
            <div className="flex items-center gap-2">
                <MapPin aria-hidden="true" className="w-4 h-4 text-gold" />
                <RiftLabel className="flex-1 text-sm text-muted">Location</RiftLabel>
                <span className="text-sm text-muted">{free === 0 ? 'All copies placed' : `${free} unplaced`}</span>
            </div>

            {placed.map(([name, count]) => (
                <div key={name} className="flex items-center gap-3">
                    <span className="flex-1 min-w-0 truncate">{name}</span>
                    <QuantityStepper
                        size="sm"
                        value={count}
                        max={count + free}
                        label={`${cardName} in ${name}`}
                        onChange={n => void save(setCount(stored, quantity, name, n))}
                    />
                    <button
                        type="button"
                        aria-label={`Remove ${name}`}
                        onClick={() => void save(setCount(stored, quantity, name, 0))}
                        className="w-9 h-9 flex items-center justify-center text-muted hover:text-ivory cursor-pointer"
                    >
                        <X aria-hidden="true" className="w-4 h-4" />
                    </button>
                </div>
            ))}

            {free > 0 ? (
                <>
                    <form className="flex gap-2" onSubmit={e => { e.preventDefault(); add(draft); }}>
                        <input
                            type="text"
                            value={draft}
                            onChange={e => setDraft(e.target.value)}
                            placeholder={placed.length ? 'Another place' : 'Red binder, deck box…'}
                            aria-label="Place name"
                            maxLength={40}
                            className="field flex-1 min-w-0 h-10"
                        />
                        <button type="submit" disabled={!draft.trim()} className="btn-quiet min-h-10 disabled:opacity-40">
                            {free === quantity ? 'Put all here' : `Put ${free} here`}
                        </button>
                    </form>
                    {suggestions.length > 0 && (
                        <div className="flex flex-wrap gap-1.5">
                            {suggestions.map(name => (
                                <button key={name} type="button" onClick={() => add(name)} className="chip">{name}</button>
                            ))}
                        </div>
                    )}
                </>
            ) : (
                placed.length > 0 && <p className="text-sm text-muted">Lower a place to move copies somewhere else.</p>
            )}

            {error && <p role="alert" className="text-sm text-banned">{error}</p>}
        </section>
    );
}
