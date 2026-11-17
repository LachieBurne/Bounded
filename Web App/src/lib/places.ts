/**
 * Where you keep a card's copies: a map of place name ("Red binder", "Deck box") to how many
 * copies are there. Copies in no place are unplaced. Stored apart from the collection entry, at
 * users/{uid}/locations/{cardId}, so an app that only knows quantities can't erase it
 * (docs/binders.md, Locations). Mirrors data/model/CardPlaces.kt in the Android app; both run
 * shared/location-test-cases.json.
 */
export type Places = Record<string, number>;
export type Placement = [name: string, count: number];

const key = (name: string) => name.trim().toLowerCase();
const byName = (a: string, b: string) => key(a).localeCompare(key(b)) || (a < b ? -1 : a > b ? 1 : 0);

/**
 * The places in name order, fitted to the copies owned: blank names and empty counts dropped,
 * names that differ only in case merged, and counts cut down so they never add up to more than
 * [quantity].
 */
export function normalised(places: Places | undefined, quantity: number): Placement[] {
    const merged = new Map<string, Placement>();
    for (const raw of Object.keys(places ?? {}).sort(byName)) {
        const name = raw.trim();
        const count = Math.floor(places?.[raw] ?? 0);
        if (!name || count <= 0) continue;
        const existing = merged.get(key(name));
        merged.set(key(name), existing ? [existing[0], existing[1] + count] : [name, count]);
    }
    const fitted: Placement[] = [];
    let room = Math.max(0, quantity);
    for (const [name, count] of merged.values()) {
        const take = Math.min(count, room);
        if (take > 0) fitted.push([name, take]);
        room -= take;
    }
    return fitted;
}

/** Copies owned that aren't in any place. */
export function unplaced(places: Places | undefined, quantity: number): number {
    return Math.max(0, quantity - normalised(places, quantity).reduce((n, [, c]) => n + c, 0));
}

const toPlaces = (placements: Placement[]): Places => Object.fromEntries(placements);

/**
 * The places with [place] holding [count] copies, limited to the copies not placed elsewhere.
 * Zero removes the place. A name matching an existing place in any case updates it, keeping
 * its spelling.
 */
export function setCount(places: Places | undefined, quantity: number, place: string, count: number): Places {
    const current = normalised(places, quantity);
    const name = place.trim();
    if (!name) return toPlaces(current);
    const existing = current.find(([n]) => key(n) === key(name));
    const others = current.filter(p => p !== existing);
    const room = Math.max(0, quantity) - others.reduce((n, [, c]) => n + c, 0);
    const next = Math.max(0, Math.min(Math.floor(count), room));
    const placed: Placement[] = next > 0 ? [...others, [existing?.[0] ?? name, next]] : others;
    return toPlaces(normalised(toPlaces(placed), quantity));
}

/** Puts every unplaced copy in [place]. Changes nothing when all copies are placed. */
export function addPlace(places: Places | undefined, quantity: number, place: string): Places {
    const free = unplaced(places, quantity);
    const current = normalised(places, quantity);
    if (free === 0) return toPlaces(current);
    const existing = current.find(([n]) => key(n) === key(place));
    return setCount(places, quantity, place, (existing?.[1] ?? 0) + free);
}

/**
 * Place names used anywhere in the collection, most-used first, for suggestions. Names that
 * differ only in case count as one, under the spelling seen first.
 */
export function knownPlaces(all: Iterable<Places>): string[] {
    const counts = new Map<string, { name: string; uses: number }>();
    for (const places of all) {
        for (const raw of Object.keys(places)) {
            const name = raw.trim();
            if (!name || (places[raw] ?? 0) <= 0) continue;
            const hit = counts.get(key(name));
            counts.set(key(name), { name: hit?.name ?? name, uses: (hit?.uses ?? 0) + 1 });
        }
    }
    return [...counts.values()].sort((a, b) => b.uses - a.uses || byName(a.name, b.name)).map(x => x.name);
}
