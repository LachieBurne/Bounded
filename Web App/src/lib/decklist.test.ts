// Runs shared/decklist-import-cases.json, the same cases the Android app's DecklistParserTest.kt
// runs, so the two parsers can't drift apart.
import { describe, expect, it } from 'vitest';
import rules from '../../../shared/deck-rules-test-cases.json';
import imports from '../../../shared/decklist-import-cases.json';
import { parseDecklist, type DecklistCard } from './decklist';

const cards = rules.cards as unknown as DecklistCard[];

describe('parseDecklist', () => {
    it.each(imports.cases.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(parseDecklist(c.text, cards)).toEqual({
            legendId: c.expect.legendId,
            championId: c.expect.championId,
            main: c.expect.main,
            side: c.expect.side,
            unmatched: c.expect.unmatched,
        });
    });
});
