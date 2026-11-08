// Runs shared/deck-rules-test-cases.json, the same cases the Android app's DeckRulesTest.kt
// runs, so the two implementations can't drift apart.
import { describe, expect, it } from 'vitest';
import cases from '../../../shared/deck-rules-test-cases.json';
import {
    ADD_RESULT_MESSAGES, ISSUE_MESSAGES, canBeChampion, checkAdd, currentStep, isCandidate, issueKey, validate,
    type DeckContents, type DeckSection, type DeckStep, type RulesCard,
} from './deckRules';

const cards = new Map(cases.cards.map(c => [c.cardId, c as unknown as RulesCard]));
const card = (id: string) => {
    const found = cards.get(id);
    if (!found) throw new Error(`no fixture card ${id}`);
    return found;
};
const deckOf = (d: { legendId: string | null; championId: string | null; main: object; side: object }): DeckContents => ({
    legendId: d.legendId,
    championId: d.championId,
    main: d.main as Record<string, number>,
    side: d.side as Record<string, number>,
});

describe('currentStep', () => {
    it.each(cases.step.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(currentStep(deckOf(c.deck), cards)).toBe(c.expect);
    });
});

describe('checkAdd', () => {
    it.each(cases.checkAdd.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(checkAdd(deckOf(c.deck), c.cardId, c.section as DeckSection, cards)).toBe(c.expect);
    });
});

describe('canBeChampion', () => {
    it.each(cases.canBeChampion.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(canBeChampion(card(c.cardId), card(c.legendId))).toBe(c.expect);
    });
});

describe('isCandidate', () => {
    it.each(cases.isCandidate.map(c => [c.name, c] as const))('%s', (_, c) => {
        const legend = c.legendId ? card(c.legendId) : null;
        expect(isCandidate(card(c.cardId), c.step as DeckStep, legend)).toBe(c.expect);
    });
});

describe('validate', () => {
    it.each(cases.validate.map(c => [c.name, c] as const))('%s', (_, c) => {
        const actual = validate(deckOf(c.deck), cards).map(issueKey).sort();
        expect(actual).toEqual([...c.expect].sort());
    });
});

describe('messages', () => {
    it('every result and issue has a message', () => {
        for (const [code, message] of Object.entries(ADD_RESULT_MESSAGES)) {
            if (code !== 'OK') expect(message).not.toBe('');
        }
        for (const message of Object.values(ISSUE_MESSAGES)) expect(message).not.toBe('');
    });
});
