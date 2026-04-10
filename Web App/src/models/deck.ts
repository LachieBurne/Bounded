export type DeckFormat = 'CONSTRUCTED';

export interface Deck {
    deckId: string;
    name: string;
    format: DeckFormat;
    legendCardId: string | null;
    championCardId: string | null;
    coverCardUrl: string | null;
    mainCount: number;
    sideCount: number;
    lastModified: number;
}

export interface DeckEntry {
    deckId: string;
    cardId: string;
    quantity: number;
    isSideboard: boolean;
}
