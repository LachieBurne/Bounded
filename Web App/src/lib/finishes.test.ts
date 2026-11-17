// Runs shared/finish-test-cases.json, the same cases the Android app's FinishTest.kt runs.
import { describe, expect, it } from 'vitest';
import cases from '../../../shared/finish-test-cases.json';
import type { Card } from '../models/card';
import { adding, countOf, finishesOf, normalised, withTotal, type Finish, type StoredCounts } from './finishes';

const entry = (e: object) => e as StoredCounts;

describe('finishesOf', () => {
    it.each(cases.finishesOf.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(finishesOf(c.card as Pick<Card, 'isToken' | 'type' | 'rarity'> & { promos?: string[] })).toEqual(c.expect);
    });
});

describe('normalised', () => {
    it.each(cases.normalised.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(normalised(entry(c.entry))).toEqual(c.expect);
    });
});

describe('withTotal', () => {
    it.each(cases.withTotal.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(withTotal(entry(c.entry), c.total)).toEqual(c.expect);
    });
});

describe('adding', () => {
    it.each(cases.adding.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(adding(entry(c.entry), c.finish as Finish, c.delta)).toEqual(c.expect);
    });
});

describe('countOf', () => {
    it.each(cases.countOf.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(countOf(normalised(entry(c.entry)), c.finish as Finish)).toBe(c.expect);
    });
});
