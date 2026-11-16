import type { Card } from '../models/card';

/**
 * Riot's bans, from data/banned_cards.json. A ban takes effect on its `bannedSince` date; until
 * then it's upcoming, and the card is still legal. Dates are compared in the viewer's own time
 * zone, which is near enough for a ban that starts on a calendar day.
 */
type BanFields = Pick<Card, 'bannedIn' | 'bannedSince'>;
export type BanFormat = 'STANDARD' | '2V2';

/** Today as YYYY-MM-DD in local time, the format `bannedSince` uses. */
export function localDate(at: Date = new Date()): string {
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`;
}

/** Banned in [format] as of [on]: listed for that format and the ban has started. */
export function isBanned(card: BanFields, format: BanFormat = 'STANDARD', on: string = localDate()): boolean {
    return (card.bannedIn?.includes(format) ?? false) && (!card.bannedSince || card.bannedSince <= on);
}

/** The date a ban starts, when it's been announced but hasn't started yet. */
export function upcomingBan(card: BanFields, on: string = localDate()): string | null {
    return card.bannedIn?.length && card.bannedSince && card.bannedSince > on ? card.bannedSince : null;
}

/** "23 November 2026", or "23 Nov" when short. */
export function banDate(iso: string, short = false): string {
    return new Date(`${iso}T00:00:00Z`).toLocaleDateString('en-AU', short
        ? { day: 'numeric', month: 'short', timeZone: 'UTC' }
        : { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' });
}
