// Uses the shared deck-rules fixtures (shared/deck-rules-test-cases.json).
import { describe, expect, it } from 'vitest';
import cases from '../../../shared/deck-rules-test-cases.json';
import type { DeckContents, RulesCard } from '../rules/deckRules';
import { deckStatus } from './deckStatus';

const cards = new Map(cases.cards.map(c => [c.cardId, c as unknown as RulesCard]));
const deck = (name: string): DeckContents => {
    const found = cases.validate.find(c => c.name === name);
    if (!found) throw new Error(`no validate case "${name}"`);
    return found.deck as unknown as DeckContents;
};

describe('deckStatus', () => {
    it('calls a complete deck with no issues legal', () => {
        expect(deckStatus(deck('complete legal deck'), cards, '2026-11-17')).toEqual({ kind: 'LEGAL', reasons: [], upcomingBan: null });
    });

    it('calls an unfinished deck in progress', () => {
        expect(deckStatus(deck('empty deck'), cards, '2026-11-17').kind).toBe('IN_PROGRESS');
    });

    it('calls a deck with a banned card not legal, naming the card', () => {
        const status = deckStatus(deck('card banned in Standard'), cards, '2026-11-17');
        expect(status.kind).toBe('NOT_LEGAL');
        expect(status.reasons).toEqual(['A card is banned in Standard. (Banned Calm Spell)']);
    });

    it('calls an over-40 deck not legal', () => {
        expect(deckStatus(deck('main deck over 40'), cards, '2026-11-17').kind).toBe('NOT_LEGAL');
    });

    it('keeps a deck legal before its ban starts, and says when it starts', () => {
        expect(deckStatus(deck('announced ban that has not started is legal'), cards, '2026-11-17'))
            .toEqual({ kind: 'LEGAL', reasons: [], upcomingBan: '2999-01-01' });
    });
});
