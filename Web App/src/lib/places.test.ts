// Runs shared/location-test-cases.json, the same cases the Android app's CardPlacesTest.kt runs.
import { describe, expect, it } from 'vitest';
import cases from '../../../shared/location-test-cases.json';
import { addPlace, knownPlaces, normalised, setCount, unplaced, type Places } from './places';

const places = (p: object) => p as Places;

describe('normalised', () => {
    it.each(cases.normalised.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(normalised(places(c.places), c.quantity)).toEqual(c.expect);
    });
});

describe('unplaced', () => {
    it.each(cases.unplaced.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(unplaced(places(c.places), c.quantity)).toBe(c.expect);
    });
});

describe('setCount', () => {
    it.each(cases.setCount.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(normalised(setCount(places(c.places), c.quantity, c.place, c.count), c.quantity)).toEqual(c.expect);
    });
});

describe('addPlace', () => {
    it.each(cases.addPlace.map(c => [c.name, c] as const))('%s', (_, c) => {
        expect(normalised(addPlace(places(c.places), c.quantity, c.place), c.quantity)).toEqual(c.expect);
    });
});

describe('knownPlaces', () => {
    it('lists names most-used first, merging case', () => {
        expect(knownPlaces([{ 'Deck box': 1 }, { 'Red binder': 2 }, { 'red binder': 1, Trade: 0 }])).toEqual(['Red binder', 'Deck box']);
    });
});
