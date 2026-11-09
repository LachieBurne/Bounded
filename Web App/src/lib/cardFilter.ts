import type { Card, CardType, Domain, RiftRarity } from '../models/card';

/*
 * What the card grids show. Mirrors the Android app's CardFilter.kt: within a category a card
 * matches if it has any of the chosen values (Fury or Calm); across categories it must match
 * all of them (Fury and Rare). An empty list means "any".
 */

export type Ownership = 'ALL' | 'OWNED' | 'MISSING';
export type CardSort = 'SET' | 'NAME' | 'COST' | 'PRICE' | 'OWNED';

export const OWNERSHIP_LABELS: Record<Ownership, string> = { ALL: 'All', OWNED: 'Owned', MISSING: 'Missing' };
export const SORT_LABELS: Record<CardSort, string> = {
    SET: 'Set order',
    NAME: 'Name',
    COST: 'Energy cost',
    PRICE: 'Price, highest first',
    OWNED: 'Copies owned',
};

/** The last cost and might chip, which stands for "this or more". */
export const STAT_MAX = 8;

export interface CardFilter {
    query: string;
    sets: string[];
    domains: Domain[];
    rarities: RiftRarity[];
    types: CardType[];
    costs: number[];
    mights: number[];
    ownership: Ownership;
    /** Leave out cards banned in Standard. */
    hideBanned: boolean;
}

export const EMPTY_FILTER: CardFilter = {
    query: '', sets: [], domains: [], rarities: [], types: [], costs: [], mights: [], ownership: 'ALL', hideBanned: false,
};

/** How many categories are narrowed (the search box and ownership toggle aren't counted). */
export function activeCount(f: CardFilter): number {
    return [f.sets, f.domains, f.rarities, f.types, f.costs, f.mights].filter(list => list.length > 0).length
        + (f.hideBanned ? 1 : 0);
}

export function toggle<T>(list: T[], value: T): T[] {
    return list.includes(value) ? list.filter(v => v !== value) : [...list, value];
}

function statMatches(value: number | null | undefined, selected: number[]): boolean {
    if (value === null || value === undefined) return false;
    return selected.includes(value) || (value >= STAT_MAX && selected.includes(STAT_MAX));
}

/**
 * Every word must appear in the name, a tag (champion or region), the artist or the card ID,
 * so "yi ionia" finds Master Yi and "OGN-03" finds OGN-030 to OGN-039.
 */
export function matchesQuery(card: Card, query: string): boolean {
    const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (words.length === 0) return true;
    const haystack = [card.name, ...(card.tags ?? []), card.artistName ?? '', card.cardId].join(' ').toLowerCase();
    return words.every(w => haystack.includes(w));
}

export function isBannedInStandard(card: Card): boolean {
    return card.bannedIn?.includes('STANDARD') ?? false;
}

export function matches(f: CardFilter, card: Card, owned: number): boolean {
    if (f.sets.length && !f.sets.includes(card.setCode)) return false;
    if (f.domains.length && !card.domain.some(d => f.domains.includes(d))) return false;
    if (f.rarities.length && !f.rarities.includes(card.rarity)) return false;
    if (f.types.length && !f.types.includes(card.type)) return false;
    if (f.costs.length && !statMatches(card.cost, f.costs)) return false;
    if (f.mights.length && !statMatches(card.might, f.mights)) return false;
    if (f.ownership === 'OWNED' && owned <= 0) return false;
    if (f.ownership === 'MISSING' && owned > 0) return false;
    if (f.hideBanned && isBannedInStandard(card)) return false;
    return matchesQuery(card, f.query);
}

/** Sorts for display. Ties fall back to set order, so the grid never reshuffles. */
export function sortCards<T extends { card: Card; quantity: number }>(
    items: T[],
    sort: CardSort,
    price: (cardId: string) => number | undefined,
): T[] {
    const setOrder = (a: T, b: T) => a.card.sortOrder - b.card.sortOrder || a.card.cardId.localeCompare(b.card.cardId);
    const by: Record<CardSort, (a: T, b: T) => number> = {
        SET: setOrder,
        NAME: (a, b) => a.card.name.toLowerCase().localeCompare(b.card.name.toLowerCase()) || setOrder(a, b),
        COST: (a, b) => a.card.cost - b.card.cost || setOrder(a, b),
        PRICE: (a, b) => (price(b.card.cardId) ?? -1) - (price(a.card.cardId) ?? -1) || setOrder(a, b),
        OWNED: (a, b) => b.quantity - a.quantity || setOrder(a, b),
    };
    return [...items].sort(by[sort]);
}

/** The sets in release order, read from the card data so a new set needs no code change. */
export function setsFrom(cards: Card[]): { code: string; name: string }[] {
    const seen = new Map<string, string>();
    [...cards].sort((a, b) => a.sortOrder - b.sortOrder).forEach(c => {
        if (!seen.has(c.setCode)) seen.set(c.setCode, c.setName || c.setCode);
    });
    return [...seen].map(([code, name]) => ({ code, name }));
}
