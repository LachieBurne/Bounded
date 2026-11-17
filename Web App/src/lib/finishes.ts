import type { Card } from '../models/card';

/**
 * How a copy of a card is printed. A finish isn't a card of its own: it shares the card's
 * number and art, so the collection counts it on the card's entry (docs/binders.md, "Finishes").
 * Mirrors data/model/Finish.kt in the Android app; both run shared/finish-test-cases.json.
 *
 * The order is the order copies are taken away in when only a total changes: standard first,
 * the rarest promo last.
 */
export const FINISHES = ['STANDARD', 'FOIL', 'NEXUS_NIGHT', 'SKIRMISH_PARTICIPATION', 'SKIRMISH_TOP_8', 'SKIRMISH_CHAMPION'] as const;
export type Finish = typeof FINISHES[number];

/** Promo printings a card can have, from data/promos.json via the card data. */
export type PromoKind = Exclude<Finish, 'STANDARD' | 'FOIL'>;

export const FINISH_LABEL: Record<Finish, string> = {
    STANDARD: 'Standard',
    FOIL: 'Foil',
    NEXUS_NIGHT: 'Nexus Night',
    SKIRMISH_PARTICIPATION: 'Participation',
    SKIRMISH_TOP_8: 'Top 8',
    SKIRMISH_CHAMPION: 'Champion',
};

/** Promos carry a PROMO stamp on the card; a plain foil doesn't. */
export const isPromo = (finish: Finish) => finish !== 'STANDARD' && finish !== 'FOIL';

/**
 * The finishes a card comes in: standard; a foil for commons and uncommons (runes have
 * alternate arts instead, which are cards of their own); and the card's promo printings.
 */
export function finishesOf(card: Pick<Card, 'isToken' | 'type' | 'rarity'> & { promos?: readonly string[] }): Finish[] {
    const hasFoil = !card.isToken && card.type !== 'RUNE' && card.type !== 'OTHER'
        && (card.rarity === 'COMMON' || card.rarity === 'UNCOMMON');
    const promos = FINISHES.filter(f => isPromo(f) && card.promos?.includes(f));
    return ['STANDARD', ...(hasFoil ? ['FOIL' as const] : []), ...promos];
}

/** Copies of one card: every copy, and how many of those are in each non-standard finish. The rest are standard. */
export interface FinishCounts {
    quantity: number;
    finishes: Record<string, number>;
}

/** A collection entry as stored, including the foil and nexusNight fields older builds wrote. */
export interface StoredCounts {
    quantity?: number;
    finishes?: Record<string, number>;
    foil?: number;
    nexusNight?: number;
}

/** Which finishes to keep when the total is too small for them all: the rarest first, and kinds this build doesn't know before any. */
function keepOrder(kinds: string[]): string[] {
    const known = FINISHES.filter(f => f !== 'STANDARD') as string[];
    const unknown = kinds.filter(k => k !== 'STANDARD' && !known.includes(k)).sort().reverse();
    return [...unknown, ...[...known].reverse()];
}

/** The counts with zeros dropped and the finishes cut down to fit the total. */
export function normalised(e: StoredCounts): FinishCounts {
    const quantity = Math.max(0, e.quantity ?? 0);
    const raw = e.finishes ?? { FOIL: e.foil ?? 0, NEXUS_NIGHT: e.nexusNight ?? 0 };
    const finishes: Record<string, number> = {};
    let room = quantity;
    for (const kind of keepOrder(Object.keys(raw))) {
        const take = Math.min(Math.max(0, raw[kind] ?? 0), room);
        if (take > 0) finishes[kind] = take;
        room -= take;
    }
    return { quantity, finishes };
}

/** How many copies of [finish] there are. */
export function countOf(c: FinishCounts, finish: Finish): number {
    if (finish !== 'STANDARD') return c.finishes[finish] ?? 0;
    return c.quantity - Object.values(c.finishes).reduce((a, n) => a + n, 0);
}

/**
 * The counts with [total] copies in all. New copies are standard ones; copies taken away come
 * off the standard ones first, then each finish in FINISHES order.
 */
export function withTotal(e: StoredCounts, total: number): FinishCounts {
    return normalised({ quantity: total, finishes: normalised(e).finishes });
}

/** The counts with [delta] copies of [finish] added (or removed, when negative), or null if that finish would go below zero. */
export function adding(e: StoredCounts, finish: Finish, delta: number): FinishCounts | null {
    const current = normalised(e);
    if (countOf(current, finish) + delta < 0) return null;
    const finishes = { ...current.finishes };
    if (finish !== 'STANDARD') {
        const next = (finishes[finish] ?? 0) + delta;
        if (next > 0) finishes[finish] = next;
        else delete finishes[finish];
    }
    return { quantity: current.quantity + delta, finishes };
}
