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
    flavorText?: string | null;
}

export interface BinderEntry {
    binderId: string;
    cardId: string;
    quantity: number;
}

export interface CardWithQuantity {
    card: Card;
    quantity: number;
}
