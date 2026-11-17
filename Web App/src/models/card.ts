import type { PromoKind } from '../lib/finishes';

export type CardType =
    | 'LEGEND'
    | 'UNIT'
    | 'SPELL'
    | 'GEAR'
    | 'RUNE'
    | 'BATTLEFIELD'
    | 'OTHER'; // Token helpers the gallery gives no type, e.g. "Buff"

export type Domain =
    | 'MIND'
    | 'CALM'
    | 'BODY'
    | 'ORDER'
    | 'CHAOS'
    | 'FURY'
    | 'COLORLESS';

export type RiftRarity =
    | 'COMMON'
    | 'UNCOMMON'
    | 'RARE'
    | 'EPIC'
    | 'SHOWCASE';

// Matches public/riftbound_data.json, which scripts/generate_card_data.py generates.
export interface Card {
    cardId: string;          // e.g. "OGN-001", "OGN-299*", "UNL-T01"
    name: string;            // Display name, e.g. "Master Yi, Honed" or "Master Yi - Wuju Bladesman"
    setCode: string;
    collectorNumber: number;
    artistName: string | null;
    imageUrl: string;
    domain: Domain[];
    rarity: RiftRarity;
    type: CardType;
    cost: number;
    might: number | null;
    isToken: boolean;
    baseName: string;        // Name without subtitle, e.g. "Master Yi"
    subtitle: string | null;
    setName: string;
    sortOrder: number;       // Release order across all sets
    orientation: 'portrait' | 'landscape';
    power: number | null;
    mightBonus: number | null;
    tags: string[];          // Champion and region tags, e.g. ["Master Yi", "Ionia"]
    championTag: string | null; // Legends only: the tag their champion units carry
    isSignature: boolean;    // Carries a champion's tag without being that champion, e.g. Tibbers
    text: string | null;
    effect: string | null;
    bannedIn?: ('STANDARD' | '2V2')[]; // Formats Riot bans this card in, from data/banned_cards.json
    bannedSince?: string | null;       // When the ban took effect, e.g. "2026-03-31"
    promos?: PromoKind[];              // Promo printings counted as finishes, from data/promos.json
    promoImages?: Partial<Record<PromoKind, string>>; // Checked scans of those promos, by kind
    flavorText?: string | null;
}

export interface BinderEntry {
    binderId: string;
    cardId: string;
    /** Every copy owned. */
    quantity: number;
    /** How many of those copies are in each finish other than standard, e.g. { FOIL: 1, NEXUS_NIGHT: 2 }. */
    finishes?: Record<string, number>;
    /** What builds before promo finishes wrote instead of `finishes`. Read, never written. */
    foil?: number;
    nexusNight?: number;
}

export interface CardWithQuantity {
    card: Card;
    /** Every copy owned. */
    quantity: number;
    /** How many of those copies are in each finish other than standard. Fitted to `quantity`. */
    finishes: Record<string, number>;
}
