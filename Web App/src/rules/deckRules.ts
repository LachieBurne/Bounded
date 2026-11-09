/*
 * Riftbound constructed deckbuilding rules (core rules section 103).
 *
 * The Android app has the same rules in data/rules/DeckRules.kt. Both must pass every
 * case in shared/deck-rules-test-cases.json; change the two together.
 * docs/deck-rules.md explains each rule.
 */
import type { Card, CardType } from '../models/card';

/** Only the fields the rules read, so tests can use small fixtures. */
export type RulesCard = Pick<Card, 'cardId' | 'name' | 'type' | 'domain' | 'tags' | 'championTag' | 'isToken' | 'isSignature' | 'bannedIn'>;
export type CardLookup = ReadonlyMap<string, RulesCard>;

/** The deck builder's steps, in order. */
export const DECK_STEPS = ['LEGEND', 'CHAMPION', 'RUNES', 'BATTLEFIELDS', 'MAIN', 'SIDEBOARD', 'COMPLETE'] as const;
export type DeckStep = typeof DECK_STEPS[number];

export type DeckSection = 'MAIN' | 'SIDEBOARD';

/**
 * A deck's contents. `main` holds every card outside the sideboard, including the
 * legend, the chosen champion's copy, runes and battlefields, keyed by card ID.
 */
export interface DeckContents {
    legendId: string | null;
    championId: string | null;
    main: Record<string, number>;
    side: Record<string, number>;
}

export interface DeckCounts {
    runes: number;
    battlefields: number;
    /** Units, spells and gear in the main deck, including the chosen champion. */
    mainDeck: number;
    sideboard: number;
}

/** Why a card can or can't be added, checked in this order. */
export const ADD_RESULT_MESSAGES = {
    OK: '',
    UNKNOWN_CARD: "That card isn't in the card list.",
    NOT_ALLOWED_IN_SIDEBOARD: 'Only units, spells and gear can go in the sideboard.',
    LEGEND_ALREADY_CHOSEN: 'Your deck already has a legend.',
    NO_LEGEND: 'Choose a legend first.',
    NOT_PLAYABLE: "Tokens can't go in a deck.",
    OUTSIDE_DOMAIN_IDENTITY: "That card's domains don't match your legend.",
    RUNE_DECK_FULL: 'Your rune deck already has 12 runes.',
    DUPLICATE_BATTLEFIELD: 'Your deck already has a battlefield with that name.',
    BATTLEFIELDS_FULL: 'Your deck already has 3 battlefields.',
    SIGNATURE_WRONG_CHAMPION: "Signature cards must match your legend's champion.",
    COPY_LIMIT: 'A deck can have at most 3 copies of a card name.',
    SIGNATURE_LIMIT: 'A deck can have at most 3 signature cards.',
    SIDEBOARD_FULL: 'Your sideboard already has 10 cards.',
} as const;
export type AddResult = keyof typeof ADD_RESULT_MESSAGES;

export const ISSUE_MESSAGES = {
    NO_LEGEND: 'No legend chosen.',
    NO_CHAMPION: 'No champion chosen.',
    INVALID_CHAMPION: "The chosen champion doesn't match the legend or isn't in the main deck.",
    RUNE_COUNT: 'The rune deck needs exactly 12 runes.',
    BATTLEFIELD_COUNT: 'The deck needs exactly 3 battlefields.',
    MAIN_DECK_TOO_SMALL: 'The main deck needs at least 40 cards, including the champion.',
    MAIN_DECK_TOO_LARGE: 'The main deck has more than 40 cards. Tournaments need exactly 40.',
    SIDEBOARD_TOO_LARGE: 'The sideboard can have at most 10 cards.',
    COPY_LIMIT: 'More than 3 copies of a card name.',
    SIGNATURE_LIMIT: 'More than 3 signature cards.',
    SIGNATURE_WRONG_CHAMPION: "A signature card doesn't match the legend's champion.",
    OUTSIDE_DOMAIN_IDENTITY: "A card's domains don't match the legend.",
    DUPLICATE_BATTLEFIELD: 'Two battlefields share a name.',
    NOT_PLAYABLE: 'A token is in the deck.',
    NOT_ALLOWED_IN_SIDEBOARD: 'Only units, spells and gear can go in the sideboard.',
    UNKNOWN_CARD: 'A card is no longer in the card list.',
    BANNED: 'A card is banned in Standard.',
} as const;
export type IssueCode = keyof typeof ISSUE_MESSAGES;

/** Issues that mean the deck breaks a rule, as opposed to simply being unfinished. */
export const RULE_BREAK_CODES: ReadonlySet<IssueCode> = new Set<IssueCode>([
    'INVALID_CHAMPION', 'COPY_LIMIT', 'SIGNATURE_LIMIT', 'SIGNATURE_WRONG_CHAMPION', 'OUTSIDE_DOMAIN_IDENTITY',
    'DUPLICATE_BATTLEFIELD', 'NOT_PLAYABLE', 'NOT_ALLOWED_IN_SIDEBOARD', 'SIDEBOARD_TOO_LARGE', 'UNKNOWN_CARD',
    'MAIN_DECK_TOO_LARGE',
    'BANNED',
]);

/** A rule the deck breaks. `subject` is the card ID or, for name-wide rules, the card name. */
export interface DeckIssue {
    code: IssueCode;
    subject?: string;
}

export const issueKey = (issue: DeckIssue) => (issue.subject === undefined ? issue.code : `${issue.code}:${issue.subject}`);

export const MAIN_DECK_MIN = 40;
/** Tournament rules need exactly 40. More is allowed while building, but flagged. */
export const MAIN_DECK_MAX = 40;
export const RUNE_DECK_SIZE = 12;
export const BATTLEFIELD_COUNT = 3;
/** Raised from 8 in the July 2026 tournament rules. */
export const SIDEBOARD_MAX = 10;
export const COPY_LIMIT = 3;
export const SIGNATURE_LIMIT = 3;

const PLAYABLE_TYPES: ReadonlySet<CardType> = new Set<CardType>(['UNIT', 'SPELL', 'GEAR']);

/** Units, spells and gear: the cards that make up the main deck and sideboard. */
export const isPlayable = (card: RulesCard) => !card.isToken && PLAYABLE_TYPES.has(card.type);

const hasTag = (card: RulesCard, tag: string) => card.tags.some(t => t.toLowerCase() === tag.toLowerCase());

/** Rule 103.2.b: every domain on the card must be one of the legend's. Colorless cards fit any deck. */
export const fitsDomainIdentity = (card: RulesCard, legend: RulesCard) =>
    card.domain.filter(d => d !== 'COLORLESS').every(d => legend.domain.includes(d));

/** Rule 103.3.d.2: signature cards must carry the legend's champion tag. */
export const signatureMatches = (card: RulesCard, legend: RulesCard) =>
    legend.championTag != null && hasTag(card, legend.championTag);

/** Rule 103.3.a.2: a champion unit (not a signature unit) with the legend's champion tag. */
export function canBeChampion(card: RulesCard, legend: RulesCard | null | undefined): boolean {
    const tag = legend?.championTag;
    if (!tag) return false;
    return card.type === 'UNIT' && !card.isToken && !card.isSignature && hasTag(card, tag);
}

const entries = (section: Record<string, number>) => Object.entries(section);

export function counts(deck: DeckContents, cards: CardLookup): DeckCounts {
    let runes = 0, battlefields = 0, mainDeck = 0;
    for (const [id, qty] of entries(deck.main)) {
        const card = cards.get(id);
        if (!card) continue;
        if (card.type === 'RUNE') runes += qty;
        else if (card.type === 'BATTLEFIELD') battlefields += qty;
        else if (isPlayable(card)) mainDeck += qty;
    }
    const sideboard = entries(deck.side).reduce((sum, [, qty]) => sum + qty, 0);
    return { runes, battlefields, mainDeck, sideboard };
}

export function currentStep(deck: DeckContents, cards: CardLookup): DeckStep {
    const c = counts(deck, cards);
    if (deck.legendId == null) return 'LEGEND';
    if (deck.championId == null) return 'CHAMPION';
    if (c.runes < RUNE_DECK_SIZE) return 'RUNES';
    if (c.battlefields < BATTLEFIELD_COUNT) return 'BATTLEFIELDS';
    if (c.mainDeck < MAIN_DECK_MIN) return 'MAIN';
    if (c.sideboard < SIDEBOARD_MAX) return 'SIDEBOARD';
    return 'COMPLETE';
}

/** Whether the card picker shows `card` at `step`. */
export function isCandidate(card: RulesCard, step: DeckStep, legend: RulesCard | null | undefined): boolean {
    switch (step) {
        case 'LEGEND':
            return card.type === 'LEGEND';
        case 'CHAMPION':
            return canBeChampion(card, legend);
        case 'RUNES':
            return !!legend && card.type === 'RUNE' && fitsDomainIdentity(card, legend);
        case 'BATTLEFIELDS':
            return !!legend && card.type === 'BATTLEFIELD' && fitsDomainIdentity(card, legend);
        case 'MAIN':
        case 'SIDEBOARD':
            return !!legend && isPlayable(card) && fitsDomainIdentity(card, legend) &&
                (!card.isSignature || signatureMatches(card, legend));
        case 'COMPLETE':
            return false;
    }
}

const allEntries = (deck: DeckContents) => [...entries(deck.main), ...entries(deck.side)];

const copiesOfName = (name: string, deck: DeckContents, cards: CardLookup) =>
    allEntries(deck).reduce((sum, [id, qty]) => sum + (cards.get(id)?.name === name ? qty : 0), 0);

const signatureTotal = (deck: DeckContents, cards: CardLookup) =>
    allEntries(deck).reduce((sum, [id, qty]) => sum + (cards.get(id)?.isSignature ? qty : 0), 0);

/** Whether one more copy of `cardId` can go in `section`. */
export function checkAdd(deck: DeckContents, cardId: string, section: DeckSection, cards: CardLookup): AddResult {
    const card = cards.get(cardId);
    if (!card) return 'UNKNOWN_CARD';
    const toSideboard = section === 'SIDEBOARD';

    if (card.type === 'LEGEND') {
        if (toSideboard) return 'NOT_ALLOWED_IN_SIDEBOARD';
        return deck.legendId != null ? 'LEGEND_ALREADY_CHOSEN' : 'OK';
    }
    const legend = deck.legendId != null ? cards.get(deck.legendId) : undefined;
    if (!legend) return 'NO_LEGEND';
    if (card.isToken || card.type === 'OTHER') return 'NOT_PLAYABLE';
    if (!fitsDomainIdentity(card, legend)) return 'OUTSIDE_DOMAIN_IDENTITY';

    const c = counts(deck, cards);
    if (card.type === 'RUNE') {
        if (toSideboard) return 'NOT_ALLOWED_IN_SIDEBOARD';
        return c.runes >= RUNE_DECK_SIZE ? 'RUNE_DECK_FULL' : 'OK';
    }
    if (card.type === 'BATTLEFIELD') {
        if (toSideboard) return 'NOT_ALLOWED_IN_SIDEBOARD';
        const sameName = entries(deck.main).some(([id, qty]) => {
            const other = cards.get(id);
            return qty > 0 && other?.type === 'BATTLEFIELD' && other.name === card.name;
        });
        if (sameName) return 'DUPLICATE_BATTLEFIELD';
        return c.battlefields >= BATTLEFIELD_COUNT ? 'BATTLEFIELDS_FULL' : 'OK';
    }

    if (card.isSignature && !signatureMatches(card, legend)) return 'SIGNATURE_WRONG_CHAMPION';
    if (copiesOfName(card.name, deck, cards) >= COPY_LIMIT) return 'COPY_LIMIT';
    if (card.isSignature && signatureTotal(deck, cards) >= SIGNATURE_LIMIT) return 'SIGNATURE_LIMIT';
    if (toSideboard && c.sideboard >= SIDEBOARD_MAX) return 'SIDEBOARD_FULL';
    return 'OK';
}

/** Every rule the deck breaks. An empty list means the deck is legal. */
export function validate(deck: DeckContents, cards: CardLookup): DeckIssue[] {
    const issues = new Map<string, DeckIssue>();
    const add = (code: IssueCode, subject?: string) => {
        const issue: DeckIssue = subject === undefined ? { code } : { code, subject };
        issues.set(issueKey(issue), issue);
    };

    const legend = deck.legendId != null ? cards.get(deck.legendId) : undefined;
    if (!legend) add('NO_LEGEND');

    if (deck.championId == null) {
        add('NO_CHAMPION');
    } else if (legend) {
        const champion = cards.get(deck.championId);
        if (!champion || (deck.main[deck.championId] ?? 0) < 1 || !canBeChampion(champion, legend)) {
            add('INVALID_CHAMPION');
        }
    }

    const c = counts(deck, cards);
    if (c.runes !== RUNE_DECK_SIZE) add('RUNE_COUNT');
    if (c.battlefields !== BATTLEFIELD_COUNT) add('BATTLEFIELD_COUNT');
    if (c.mainDeck < MAIN_DECK_MIN) add('MAIN_DECK_TOO_SMALL');
    if (c.mainDeck > MAIN_DECK_MAX) add('MAIN_DECK_TOO_LARGE');
    if (c.sideboard > SIDEBOARD_MAX) add('SIDEBOARD_TOO_LARGE');

    const copiesByName = new Map<string, number>();
    const battlefieldNames = new Map<string, number>();
    let signatures = 0;
    const sections: [DeckSection, Record<string, number>][] = [['MAIN', deck.main], ['SIDEBOARD', deck.side]];
    for (const [section, sectionEntries] of sections) {
        for (const [id, qty] of entries(sectionEntries)) {
            if (qty <= 0) continue;
            const card = cards.get(id);
            if (!card) {
                add('UNKNOWN_CARD', id);
                continue;
            }
            if (section === 'SIDEBOARD' && !isPlayable(card) && !card.isToken) {
                add('NOT_ALLOWED_IN_SIDEBOARD', id);
                continue;
            }
            if (card.bannedIn?.includes('STANDARD')) add('BANNED', id);
            if (card.type === 'LEGEND') continue;
            if (card.isToken || card.type === 'OTHER') {
                add('NOT_PLAYABLE', id);
                continue;
            }
            if (legend && !fitsDomainIdentity(card, legend)) add('OUTSIDE_DOMAIN_IDENTITY', id);
            if (card.type === 'BATTLEFIELD') {
                battlefieldNames.set(card.name, (battlefieldNames.get(card.name) ?? 0) + qty);
                continue;
            }
            if (card.type === 'RUNE') continue;
            copiesByName.set(card.name, (copiesByName.get(card.name) ?? 0) + qty);
            if (card.isSignature) {
                signatures += qty;
                if (legend && !signatureMatches(card, legend)) add('SIGNATURE_WRONG_CHAMPION', id);
            }
        }
    }
    copiesByName.forEach((n, name) => { if (n > COPY_LIMIT) add('COPY_LIMIT', name); });
    battlefieldNames.forEach((n, name) => { if (n > 1) add('DUPLICATE_BATTLEFIELD', name); });
    if (signatures > SIGNATURE_LIMIT) add('SIGNATURE_LIMIT');
    return [...issues.values()];
}

/** Builds DeckContents from Firestore deck entries. */
export function deckContents(
    deck: { legendCardId: string | null; championCardId: string | null },
    deckEntries: { cardId: string; quantity: number; isSideboard: boolean }[],
): DeckContents {
    const main: Record<string, number> = {};
    const side: Record<string, number> = {};
    for (const e of deckEntries) {
        if (e.quantity <= 0) continue;
        const target = e.isSideboard ? side : main;
        target[e.cardId] = (target[e.cardId] ?? 0) + e.quantity;
    }
    return { legendId: deck.legendCardId, championId: deck.championCardId, main, side };
}
