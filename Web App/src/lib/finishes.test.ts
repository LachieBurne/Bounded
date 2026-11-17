// The same cases as the Android app's BinderEntryTest.kt.
import { describe, expect, it } from 'vitest';
import type { Card } from '../models/card';
import { adding, countOf, finishesOf, normalised, withTotal, type FinishCounts } from './finishes';

const entry = (quantity: number, foil = 0, nexusNight = 0): FinishCounts => ({ quantity, foil, nexusNight });

describe('finishes', () => {
    it('makes new copies from a total change standard', () => {
        expect(withTotal(entry(3, 1, 1), 5)).toEqual(entry(5, 1, 1));
    });

    it('lowers the total by standard copies first, then foil, then Nexus Night', () => {
        const start = entry(4, 2, 1);
        expect(withTotal(start, 3)).toEqual(entry(3, 2, 1));
        expect(withTotal(start, 2)).toEqual(entry(2, 1, 1));
        expect(withTotal(start, 1)).toEqual(entry(1, 0, 1));
        expect(withTotal(start, 0)).toEqual(entry(0));
    });

    it('raises the total with copies of a finish', () => {
        expect(adding(entry(2), 'FOIL', 1)).toEqual(entry(3, 1));
        expect(adding(entry(2), 'NEXUS_NIGHT', 1)).toEqual(entry(3, 0, 1));
        expect(adding(entry(2), 'STANDARD', 1)).toEqual(entry(3));
    });

    it("does nothing when removing a copy of a finish you don't have", () => {
        expect(adding(entry(2), 'FOIL', -1)).toBeNull();
        expect(adding(entry(2, 2), 'STANDARD', -1)).toBeNull();
        expect(adding(entry(2, 2), 'FOIL', -1)).toEqual(entry(1, 1));
    });

    it('cuts finish counts that exceed the total down to fit', () => {
        // An app that only knows the total can leave the finish counts behind.
        expect(normalised({ quantity: 2, foil: 3, nexusNight: 1 })).toEqual(entry(2, 1, 1));
        expect(adding(entry(2, 3, 1), 'STANDARD', 1)).toEqual(entry(3, 1, 1));
        expect(normalised({ quantity: 3 })).toEqual(entry(3));
    });

    it('counts each finish', () => {
        expect(countOf(entry(5, 2, 1), 'STANDARD')).toBe(2);
        expect(countOf(entry(5, 2, 1), 'FOIL')).toBe(2);
        expect(countOf(entry(5, 2, 1), 'NEXUS_NIGHT')).toBe(1);
    });

    it("gives foil finishes only to commons and uncommons that aren't runes", () => {
        const card = (rarity: Card['rarity'], type: Card['type'] = 'UNIT') => ({ rarity, type, isToken: false });
        expect(finishesOf(card('COMMON'))).toEqual(['STANDARD', 'FOIL', 'NEXUS_NIGHT']);
        expect(finishesOf(card('UNCOMMON', 'BATTLEFIELD'))).toEqual(['STANDARD', 'FOIL', 'NEXUS_NIGHT']);
        expect(finishesOf(card('COMMON', 'RUNE'))).toEqual(['STANDARD']);
        expect(finishesOf(card('RARE'))).toEqual(['STANDARD']);
        expect(finishesOf(card('SHOWCASE'))).toEqual(['STANDARD']);
        expect(finishesOf({ rarity: 'COMMON', type: 'UNIT', isToken: true })).toEqual(['STANDARD']);
    });
});
