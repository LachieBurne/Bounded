// The same cases as the Android app's BinderStatsTest.kt.
import { describe, expect, it } from 'vitest';
import type { Card, CardType, CardWithQuantity, RiftRarity } from '../models/card';
import { binderStats } from './stats';

const card = (cardId: string, rarity: RiftRarity, type: CardType) =>
    ({ cardId, name: cardId, setCode: cardId.split('-')[0], rarity, type, isToken: false, domain: [] }) as unknown as Card;
const item = (c: Card, quantity: number, finishes: Record<string, number> = {}): CardWithQuantity => ({ card: c, quantity, finishes });

const items = [
    item(card('OGN-001', 'COMMON', 'UNIT'), 3, { FOIL: 1 }),
    item(card('OGN-002', 'COMMON', 'SPELL'), 1),
    item(card('OGN-003', 'RARE', 'UNIT'), 0),
    item(card('SFD-001', 'EPIC', 'GEAR'), 4, { NEXUS_NIGHT: 2 }),
    item(card('SFD-002', 'COMMON', 'RUNE'), 12),
];
const prices = { 'OGN-001': 0.5, 'OGN-003': 9, 'SFD-001': 2 };
const all = () => true;

describe('binderStats', () => {
    it('counts the headline and playsets', () => {
        const s = binderStats(items, all, prices, ['OGN', 'SFD']);
        expect([s.progress.owned, s.progress.total, s.progress.copies]).toEqual([4, 5, 20]);
        expect(s.progress.value).toBeCloseTo(0.5 * 3 + 2 * 4);
        // Units, spells and gear only; runes don't count towards playsets.
        expect(s.playsets).toEqual({ owned: 2, total: 4 });
    });

    it('breaks down owned of total by rarity, type and set', () => {
        const s = binderStats(items, all, prices, ['OGN', 'SFD']);
        expect(s.byRarity.map(r => [r.rarity, r.progress.owned, r.progress.total])).toEqual([['COMMON', 3, 3], ['RARE', 0, 1], ['EPIC', 1, 1]]);
        expect(s.byType.map(r => [r.type, r.progress.owned, r.progress.total])).toEqual([['UNIT', 1, 2], ['SPELL', 1, 1], ['GEAR', 1, 1], ['RUNE', 1, 1]]);
        expect(s.bySet.map(r => [r.setCode, r.progress.owned, r.progress.total])).toEqual([['OGN', 2, 3], ['SFD', 2, 2]]);
    });

    it('counts finishes and lists the most valuable cards you own', () => {
        const s = binderStats(items, all, prices, ['OGN', 'SFD']);
        expect(s.finishes).toEqual([{ finish: 'FOIL', count: 1 }, { finish: 'NEXUS_NIGHT', count: 2 }]);
        // Unowned cards aren't listed, however valuable.
        expect(s.mostValuable.map(v => v.item.card.cardId)).toEqual(['SFD-001', 'OGN-001']);
    });

    it('counts playsets by card name across printings', () => {
        const showcase = { ...card('OGN-001a', 'SHOWCASE', 'UNIT'), name: 'OGN-002' } as Card;
        const s = binderStats([item(card('OGN-002', 'COMMON', 'SPELL'), 2), item(showcase, 1)], all, {});
        expect(s.playsets).toEqual({ owned: 1, total: 1 });
    });

    it('is scoped to the binder', () => {
        const s = binderStats(items, c => c.setCode === 'SFD', prices);
        expect([s.progress.owned, s.progress.total]).toEqual([2, 2]);
        expect(s.bySet).toEqual([]); // One set: no per-set rows.
    });
});
