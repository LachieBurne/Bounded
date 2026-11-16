import { ISSUE_MESSAGES, RULE_BREAK_CODES, validate, type CardLookup, type DeckContents } from '../rules/deckRules';
import { localDate, upcomingBan } from './bans';

/**
 * What a deck tile says about a deck. A deck that breaks a rule is NOT_LEGAL whatever else is
 * true; one that only lacks cards is IN_PROGRESS; one with no issues at all is LEGAL.
 */
export interface DeckStatus {
    kind: 'NOT_LEGAL' | 'IN_PROGRESS' | 'LEGAL';
    /** Why it isn't legal, one line per rule it breaks, e.g. "A card is banned in Standard. (Baited Hook)". */
    reasons: string[];
    /** The earliest announced ban, not yet started, on a card in the deck. */
    upcomingBan: string | null;
}

export function deckStatus(contents: DeckContents, cards: CardLookup, on: string = localDate()): DeckStatus {
    const issues = validate(contents, cards);
    const breaks = issues.filter(i => RULE_BREAK_CODES.has(i.code));
    const reasons = breaks.map(i => {
        const subject = i.subject === undefined ? '' : ` (${cards.get(i.subject)?.name ?? i.subject})`;
        return `${ISSUE_MESSAGES[i.code]}${subject}`;
    });

    const upcoming = [...Object.keys(contents.main), ...Object.keys(contents.side)]
        .map(id => cards.get(id))
        .map(card => (card ? upcomingBan(card, on) : null))
        .filter((date): date is string => date !== null)
        .sort()[0] ?? null;

    const kind = breaks.length > 0 ? 'NOT_LEGAL' : issues.length > 0 ? 'IN_PROGRESS' : 'LEGAL';
    return { kind, reasons, upcomingBan: upcoming };
}
