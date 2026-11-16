import { useEffect, useState } from 'react';
import { collection, getDocs, type DocumentData } from 'firebase/firestore';
import { auth, db } from '../config/firebase';
import type { Deck, DeckEntry } from '../models/deck';

/** A deck entry as stored in Firestore. Android builds before 1.1 stored the flag as "sideboard"; read either. */
export function entryFromDoc(data: DocumentData, deckId: string): DeckEntry {
    return {
        deckId: data.deckId ?? deckId,
        cardId: data.cardId,
        quantity: data.quantity ?? 0,
        isSideboard: data.isSideboard ?? data.sideboard ?? false,
    };
}

// Entries already read this session, by user and deck, with the deck's lastModified when they
// were read. Both apps bump lastModified on every edit, so a changed value means read again.
const cache = new Map<string, { lastModified: number; entries: DeckEntry[] }>();

/**
 * Every listed deck's entries, read once each and again when a deck changes. Decks not read yet
 * are missing from the map. Costs one read per entry the first time the deck list opens.
 */
export function useDeckEntries(decks: Deck[]): ReadonlyMap<string, DeckEntry[]> {
    const uid = auth.currentUser?.uid;
    const [, setLoaded] = useState(0);

    useEffect(() => {
        if (!uid) return;
        let cancelled = false;
        const stale = decks.filter(d => cache.get(`${uid}/${d.deckId}`)?.lastModified !== d.lastModified);
        if (stale.length === 0) return;
        Promise.all(stale.map(async d => {
            const snapshot = await getDocs(collection(db, 'users', uid, 'decks', d.deckId, 'entries'));
            const entries = snapshot.docs.map(doc => entryFromDoc(doc.data(), d.deckId)).filter(e => e.cardId && e.quantity > 0);
            cache.set(`${uid}/${d.deckId}`, { lastModified: d.lastModified, entries });
        }))
            .then(() => { if (!cancelled) setLoaded(n => n + 1); })
            .catch(e => console.error('Could not load deck lists', e));
        return () => { cancelled = true; };
    }, [uid, decks]);

    const loaded = new Map<string, DeckEntry[]>();
    for (const d of decks) {
        const hit = uid ? cache.get(`${uid}/${d.deckId}`) : undefined;
        if (hit) loaded.set(d.deckId, hit.entries);
    }
    return loaded;
}
