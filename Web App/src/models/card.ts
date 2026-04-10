export type CardType =
    | 'LEGEND'
    | 'UNIT'
    | 'SPELL'
    | 'GEAR'
    | 'RUNE'
    | 'BATTLEFIELD';

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
    | 'SHOWCASE'
    | 'TOKEN';

export interface Card {
    cardId: string;
    name: string;
    type: CardType;
    domain: Domain[];
    cost: number;
    might: number;
    text: string;
    flavorText: string | null;
    power: number | null;
    health: number | null;
    rarity: RiftRarity;
    artistName: string | null;
    setCode: string;
    collectorNumber: string;
    imageUrl: string;
    foilImageUrl: string | null;
    isAlternativeArt: boolean;
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
