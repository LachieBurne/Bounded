import type { Card, CardType, CardWithQuantity, RiftRarity } from '../models/card';
import { progressOf, type Progress } from './binder';
import { FINISHES, countOf, type Finish } from './finishes';
import { RARITIES } from './style';

/**
 * A binder's stats: what the progress panel shows, broken down. Every breakdown is "owned of
 * total" within the binder, so the rows add up to the headline. Mirrors BinderStats.kt in the
 * Android app; stats.test.ts and BinderStatsTest.kt run the same cases.
 */
export interface BinderStats {
    progress: Progress;
    /**
     * Units, spells and gear you own three or more copies of, out of all of them in the binder.
     * Counted by card name across printings, as the deck copy limit is.
     */
    playsets: { owned: number; total: number };
    byRarity: { rarity: RiftRarity; progress: Progress }[];
    byType: { type: CardType; progress: Progress }[];
    /** Per set, only when the binder spans more than one. */
    bySet: { setCode: string; progress: Progress }[];
    /** Copies in each finish other than standard, for finishes you own any of. */
    finishes: { finish: Finish; count: number }[];
    /** The cards you own worth the most per copy, in US dollars. */
    mostValuable: { item: CardWithQuantity; price: number }[];
}

/** Copies of a playable card that make a full playset (the deck copy limit). */
export const PLAYSET = 3;

const PLAYSET_TYPES: CardType[] = ['UNIT', 'SPELL', 'GEAR'];
const TYPE_ORDER: CardType[] = ['LEGEND', 'UNIT', 'SPELL', 'GEAR', 'RUNE', 'BATTLEFIELD'];

export function binderStats(
    items: CardWithQuantity[],
    include: (card: Card) => boolean,
    prices: Record<string, number>,
    setOrder: string[] = [],
    topCount = 5,
): BinderStats {
    const inScope = items.filter(i => include(i.card));
    const progress = (pick: (card: Card) => boolean) => progressOf(inScope, pick, prices);

    const copiesByName = new Map<string, number>();
    inScope
        .filter(i => !i.card.isToken && PLAYSET_TYPES.includes(i.card.type))
        .forEach(i => copiesByName.set(i.card.name, (copiesByName.get(i.card.name) ?? 0) + i.quantity));
    const rank = (code: string) => (setOrder.includes(code) ? setOrder.indexOf(code) : Number.MAX_SAFE_INTEGER);
    const sets = [...new Set(inScope.map(i => i.card.setCode))].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));

    return {
        progress: progress(() => true),
        playsets: { owned: [...copiesByName.values()].filter(n => n >= PLAYSET).length, total: copiesByName.size },
        byRarity: RARITIES.map(rarity => ({ rarity, progress: progress(c => c.rarity === rarity) })).filter(r => r.progress.total > 0),
        byType: TYPE_ORDER.map(type => ({ type, progress: progress(c => c.type === type) })).filter(r => r.progress.total > 0),
        bySet: sets.length > 1 ? sets.map(setCode => ({ setCode, progress: progress(c => c.setCode === setCode) })) : [],
        finishes: FINISHES.filter(f => f !== 'STANDARD')
            .map(finish => ({ finish, count: inScope.reduce((n, i) => n + countOf(i, finish), 0) }))
            .filter(f => f.count > 0),
        mostValuable: inScope
            .filter(i => i.quantity > 0 && (prices[i.card.cardId] ?? 0) > 0)
            .map(item => ({ item, price: prices[item.card.cardId] }))
            .sort((a, b) => b.price - a.price)
            .slice(0, topCount),
    };
}
