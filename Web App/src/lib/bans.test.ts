import { describe, expect, it } from 'vitest';
import { banDate, isBanned, localDate, upcomingBan } from './bans';

const both = { bannedIn: ['STANDARD', '2V2'] as ('STANDARD' | '2V2')[], bannedSince: '2026-11-23' };
const twoVTwoOnly = { bannedIn: ['2V2'] as ('STANDARD' | '2V2')[], bannedSince: '2026-07-24' };
const legal = { bannedIn: [] as ('STANDARD' | '2V2')[], bannedSince: null };

describe('bans', () => {
    it('starts a ban on its date, not before', () => {
        expect(isBanned(both, 'STANDARD', '2026-11-22')).toBe(false);
        expect(isBanned(both, 'STANDARD', '2026-11-23')).toBe(true);
        expect(isBanned(both, '2V2', '2026-12-01')).toBe(true);
    });

    it('only bans the listed formats', () => {
        expect(isBanned(twoVTwoOnly, 'STANDARD', '2026-12-01')).toBe(false);
        expect(isBanned(twoVTwoOnly, '2V2', '2026-12-01')).toBe(true);
        expect(isBanned(legal, 'STANDARD', '2026-12-01')).toBe(false);
    });

    it('treats a ban with no date as already started', () => {
        expect(isBanned({ bannedIn: ['STANDARD'], bannedSince: null }, 'STANDARD', '2000-01-01')).toBe(true);
    });

    it('reports an announced ban as upcoming until it starts', () => {
        expect(upcomingBan(both, '2026-11-17')).toBe('2026-11-23');
        expect(upcomingBan(both, '2026-11-23')).toBeNull();
        expect(upcomingBan(legal, '2026-11-17')).toBeNull();
    });

    it('formats dates', () => {
        expect(localDate(new Date(2026, 10, 7))).toBe('2026-11-07');
        expect(banDate('2026-11-23')).toBe('23 November 2026');
        expect(banDate('2026-11-23', true)).toBe('23 Nov');
    });
});
