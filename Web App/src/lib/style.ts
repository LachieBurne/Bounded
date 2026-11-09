import type { CardType, Domain, RiftRarity } from '../models/card';

/*
 * Gilded Rift: the card frame as the design system, shared with the Android app
 * (ui/theme/Rift.kt and ui/common/CardStyle.kt). Domain colours were sampled from the card art:
 * the sigil is the bright icon colour, the band the darker name-banner colour.
 */

export const PLAYABLE_DOMAINS: Domain[] = ['FURY', 'CALM', 'MIND', 'BODY', 'CHAOS', 'ORDER'];

export const DOMAIN_LABEL: Record<Domain, string> = {
    FURY: 'Fury', CALM: 'Calm', MIND: 'Mind', BODY: 'Body', CHAOS: 'Chaos', ORDER: 'Order', COLORLESS: 'Colorless',
};

export const DOMAIN_SIGIL: Record<Domain, string> = {
    FURY: '#DE141F', CALM: '#508C33', MIND: '#226A98', BODY: '#E0790D', CHAOS: '#683795', ORDER: '#D4B503', COLORLESS: '#8C8577',
};

export const DOMAIN_BAND: Record<Domain, string> = {
    FURY: '#8D0409', CALM: '#285F24', MIND: '#073D69', BODY: '#BB3C13', CHAOS: '#3D2272', ORDER: '#8C6610', COLORLESS: '#4A4339',
};

export const domainIconUrl = (d: Domain) => `https://lachieburne.github.io/Bounded/domain_images/${d.toLowerCase()}.png`;

/** The domains a card shows, without Colorless unless it has nothing else. */
export function shownDomains(domains: Domain[]): Domain[] {
    const coloured = domains.filter(d => d !== 'COLORLESS');
    return coloured.length ? coloured : ['COLORLESS'];
}

/** The thin stripe under a card tile: one sigil colour, or two split on a slant. */
export function domainStripe(domains: Domain[]): string {
    const [a, b] = shownDomains(domains);
    return b ? `linear-gradient(100deg, ${DOMAIN_SIGIL[a]} 50%, ${DOMAIN_SIGIL[b]} 50%)` : DOMAIN_SIGIL[a];
}

export const RARITY_LABEL: Record<RiftRarity, string> = {
    COMMON: 'Common', UNCOMMON: 'Uncommon', RARE: 'Rare', EPIC: 'Epic', SHOWCASE: 'Showcase',
};
export const RARITY_GEM: Record<RiftRarity, string> = {
    COMMON: '#A69A88', UNCOMMON: '#3E9C8F', RARE: '#3B82C4', EPIC: '#9B5CD6', SHOWCASE: '#D3AC49',
};
export const RARITIES: RiftRarity[] = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'SHOWCASE'];

export const TYPE_LABEL: Record<CardType, string> = {
    LEGEND: 'Legend', UNIT: 'Unit', SPELL: 'Spell', GEAR: 'Gear', RUNE: 'Rune', BATTLEFIELD: 'Battlefield', OTHER: 'Other',
};
export const FILTERABLE_TYPES: CardType[] = ['UNIT', 'SPELL', 'GEAR', 'LEGEND', 'RUNE', 'BATTLEFIELD'];

/** Keyword chip colours sampled from the cards. Others use a neutral grey (see docs/tech-debt.md). */
export const KEYWORD_COLOURS: Record<string, string> = {
    Action: '#1F7A5C', Reaction: '#1F7A5C', Deploy: '#1F7A5C', Ganking: '#5E8A16', Assault: '#B02E63',
};

/** Base runes (no letter after the number) are treated as plentiful: never missing. */
export const isBaseRune = (card: { type: CardType; cardId: string }) =>
    card.type === 'RUNE' && !/[a-z]/.test(card.cardId.split('-')[1] ?? '');
