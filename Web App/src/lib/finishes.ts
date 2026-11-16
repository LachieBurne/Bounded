import type { BinderEntry, Card } from '../models/card';

/**
 * How a copy of a card is printed. A finish isn't a card of its own: it shares the card's
 * number and art, so the collection counts it on the card's entry (docs/binders.md, "Finishes").
 * Mirrors data/model/Finish.kt and BinderEntry.kt in the Android app; BinderEntryTest.kt and
 * finishes.test.ts run the same cases.
 */
export type Finish = 'STANDARD' | 'FOIL' | 'NEXUS_NIGHT';

export const FINISH_LABEL: Record<Finish, string> = {
    STANDARD: 'Standard',
    FOIL: 'Foil',
    NEXUS_NIGHT: 'Nexus Night',
};

const ALL_FINISHES: Finish[] = ['STANDARD', 'FOIL', 'NEXUS_NIGHT'];

/**
 * The finishes a card comes in. Commons and uncommons have a foil printing, and some have a
 * Nexus Night promo with a stronger foil. Runes have alternate arts instead, which are cards of
 * their own. Until there's a list of the Nexus Night promos (docs/card-backlog.md), every card
 * with a foil offers one.
 */
export function finishesOf(card: Pick<Card, 'isToken' | 'type' | 'rarity'>): Finish[] {
    const hasFoil = !card.isToken && card.type !== 'RUNE' && card.type !== 'OTHER'
        && (card.rarity === 'COMMON' || card.rarity === 'UNCOMMON');
    return hasFoil ? ALL_FINISHES : ['STANDARD'];
}

/** Copies of one card: every copy, and how many of those are foil and Nexus Night. The rest are standard. */
export interface FinishCounts {
    quantity: number;
    foil: number;
    nexusNight: number;
}

/** How many copies of [finish] the counts hold. */
export function countOf(c: FinishCounts, finish: Finish): number {
    if (finish === 'FOIL') return c.foil;
    if (finish === 'NEXUS_NIGHT') return c.nexusNight;
    return c.quantity - c.foil - c.nexusNight;
}

/** The finish counts cut down to fit the total, in case something changed only the total. */
export function normalised(e: Partial<Pick<BinderEntry, 'quantity' | 'foil' | 'nexusNight'>>): FinishCounts {
    const quantity = Math.max(0, e.quantity ?? 0);
    const nexusNight = Math.min(Math.max(0, e.nexusNight ?? 0), quantity);
    const foil = Math.min(Math.max(0, e.foil ?? 0), quantity - nexusNight);
    return { quantity, foil, nexusNight };
}

/**
 * The counts with [quantity] copies in all. New copies are standard ones; copies taken away come
 * off the standard ones first, then foil, then Nexus Night.
 */
export function withTotal(e: FinishCounts, quantity: number): FinishCounts {
    const current = normalised(e);
    const nexusNight = Math.min(current.nexusNight, quantity);
    return { quantity, nexusNight, foil: Math.min(current.foil, quantity - nexusNight) };
}

/** The counts with [delta] copies of [finish] added (or removed, when negative), or null if that finish would go below zero. */
export function adding(e: FinishCounts, finish: Finish, delta: number): FinishCounts | null {
    const current = normalised(e);
    const next = {
        standard: countOf(current, 'STANDARD') + (finish === 'STANDARD' ? delta : 0),
        foil: current.foil + (finish === 'FOIL' ? delta : 0),
        nexusNight: current.nexusNight + (finish === 'NEXUS_NIGHT' ? delta : 0),
    };
    if (next.standard < 0 || next.foil < 0 || next.nexusNight < 0) return null;
    return { quantity: next.standard + next.foil + next.nexusNight, foil: next.foil, nexusNight: next.nexusNight };
}
