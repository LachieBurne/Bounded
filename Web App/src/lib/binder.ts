import type { Card, CardType, CardWithQuantity, Domain, RiftRarity } from '../models/card';

/*
 * Sub-binders: saved views of the collection, such as a master set binder for one set. They
 * hold no cards of their own; quantities always come from the collection (binders/main/entries).
 * Stored at users/{uid}/binders/{binderId}, the same documents the Android app writes.
 * docs/binders.md describes the format.
 */

export const MAIN_BINDER_ID = 'main';

export interface Binder {
    binderId: string;
    name: string;
    /** Set codes, e.g. ["OGN"]. Empty means every set. */
    sets: string[];
    domains: string[];
    rarities: string[];
    types: string[];
    /** Include showcase printings: alt arts, overnumbered and signed cards. */
    includeShowcase: boolean;
    createdAt: number;
}

const DOMAINS: Domain[] = ['FURY', 'CALM', 'MIND', 'BODY', 'CHAOS', 'ORDER', 'COLORLESS'];
const RARITIES: RiftRarity[] = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'SHOWCASE'];
const TYPES: CardType[] = ['LEGEND', 'UNIT', 'SPELL', 'GEAR', 'RUNE', 'BATTLEFIELD', 'OTHER'];

export function newBinder(fields: Partial<Binder> = {}): Binder {
    return {
        binderId: crypto.randomUUID(), name: '', sets: [], domains: [], rarities: [], types: [],
        includeShowcase: true, createdAt: Date.now(), ...fields,
    };
}

/** Reads a binder document, tolerating missing fields and values from a newer app. */
export function binderFrom(id: string, data: Record<string, unknown>): Binder {
    const list = (v: unknown) => (Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []);
    return {
        binderId: id,
        name: typeof data.name === 'string' ? data.name : '',
        sets: list(data.sets),
        domains: list(data.domains),
        rarities: list(data.rarities),
        types: list(data.types),
        includeShowcase: data.includeShowcase !== false,
        createdAt: typeof data.createdAt === 'number' ? data.createdAt : 0,
    };
}

/** Whether a card is in the binder's scope. Unknown values are ignored, as in the Android app. */
export function inBinder(binder: Binder | null, card: Card): boolean {
    if (!binder) return true;
    const domains = binder.domains.filter((d): d is Domain => DOMAINS.includes(d as Domain));
    const chosenRarities = binder.rarities.filter((r): r is RiftRarity => RARITIES.includes(r as RiftRarity));
    const types = binder.types.filter((t): t is CardType => TYPES.includes(t as CardType));
    // An empty list means any rarity, so leaving out showcase means listing the rest.
    const rarities = binder.includeShowcase
        ? chosenRarities
        : (chosenRarities.length ? chosenRarities : RARITIES).filter(r => r !== 'SHOWCASE');

    if (binder.sets.length && !binder.sets.includes(card.setCode)) return false;
    if (domains.length && !card.domain.some(d => domains.includes(d))) return false;
    if (rarities.length && !rarities.includes(card.rarity)) return false;
    if (types.length && !types.includes(card.type)) return false;
    return true;
}

/** With nothing chosen a binder would just repeat All cards, so the editor doesn't allow it. */
export function isSameAsAllCards(b: Pick<Binder, 'sets' | 'domains' | 'rarities' | 'types' | 'includeShowcase'>): boolean {
    return !b.sets.length && !b.domains.length && !b.rarities.length && !b.types.length && b.includeShowcase;
}

export interface Progress {
    /** Cards with at least one copy owned. */
    owned: number;
    total: number;
    copies: number;
    /** In US dollars. */
    value: number;
}

export function progressOf(
    items: CardWithQuantity[],
    include: (card: Card) => boolean,
    prices: Record<string, number>,
): Progress {
    const p: Progress = { owned: 0, total: 0, copies: 0, value: 0 };
    for (const { card, quantity } of items) {
        if (!include(card)) continue;
        p.total++;
        if (quantity > 0) {
            p.owned++;
            p.copies += quantity;
            p.value += (prices[card.cardId] ?? 0) * quantity;
        }
    }
    return p;
}

export const percent = (p: Progress) => (p.total ? Math.round((p.owned / p.total) * 100) : 0);
