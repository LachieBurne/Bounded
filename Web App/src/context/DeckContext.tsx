import React, { createContext, useContext, useEffect, useState } from 'react';
import { collection, onSnapshot, doc, setDoc, deleteDoc, updateDoc, query, orderBy } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import type { Deck } from '../models/deck';
import type { User } from 'firebase/auth';
import type { QuerySnapshot, DocumentData } from 'firebase/firestore';

interface DeckContextType {
    decks: Deck[];
    loadingDecks: boolean;
    createDeck: (deck: Deck) => Promise<void>;
    updateDeck: (deck: Deck) => Promise<void>;
    deleteDeck: (deckId: string) => Promise<void>;
    renameDeck: (deckId: string, newName: string) => Promise<void>;
}

const DeckContext = createContext<DeckContextType>({} as DeckContextType);

export const useDecks = () => useContext(DeckContext);

export const DeckProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [decks, setDecks] = useState<Deck[]>([]);
    const [loadingDecks, setLoadingDecks] = useState(true);

    useEffect(() => {
        let unsubscribeSnapshot: () => void;

        const unsubscribeAuth = auth.onAuthStateChanged((user: User | null) => {
            if (!user) {
                setDecks([]);
                setLoadingDecks(false);
                if (unsubscribeSnapshot) unsubscribeSnapshot();
                return;
            }

            setLoadingDecks(true);
            const uid = user.uid;
            const decksRef = collection(db, 'users', uid, 'decks');
            // Fetch decks ordered by lastModified
            const q = query(decksRef, orderBy('lastModified', 'desc'));

            unsubscribeSnapshot = onSnapshot(q, (snapshot: QuerySnapshot<DocumentData>) => {
                const fetchedDecks = snapshot.docs.map(doc => doc.data() as Deck);
                setDecks(fetchedDecks);
                setLoadingDecks(false);
            });
        });

        return () => {
            unsubscribeAuth();
            if (unsubscribeSnapshot) unsubscribeSnapshot();
        };
    }, []);

    const createDeck = async (deck: Deck) => {
        const user = auth.currentUser;
        if (!user) return;
        const deckRef = doc(db, 'users', user.uid, 'decks', deck.deckId);
        await setDoc(deckRef, deck);
    };

    const updateDeck = async (deck: Deck) => {
        const user = auth.currentUser;
        if (!user) return;
        const deckRef = doc(db, 'users', user.uid, 'decks', deck.deckId);
        await setDoc(deckRef, deck);
    };

    const deleteDeck = async (deckId: string) => {
        const user = auth.currentUser;
        if (!user) return;

        try {
            // Manually fetch entries collection to delete them first
            const entriesRef = collection(db, 'users', user.uid, 'decks', deckId, 'entries');
            // Normally you would get docs, loop & delete but realistically we just need to hit the backend or delete manually without batch limit issues for a small app.
            // Using a simple workaround since this relies on client-side cleanup
            import('firebase/firestore').then(async ({ getDocs, writeBatch }) => {
                const snapshot = await getDocs(entriesRef);
                const batch = writeBatch(db);
                snapshot.docs.forEach(doc => {
                    batch.delete(doc.ref);
                });
                const deckRef = doc(db, 'users', user.uid, 'decks', deckId);
                batch.delete(deckRef);
                await batch.commit();
            });
        } catch (e) {
            console.error("Error deleting deck", e);
            const deckRef = doc(db, 'users', user.uid, 'decks', deckId);
            await deleteDoc(deckRef);
        }
    };

    const renameDeck = async (deckId: string, newName: string) => {
        const user = auth.currentUser;
        if (!user) return;
        const deckRef = doc(db, 'users', user.uid, 'decks', deckId);
        await updateDoc(deckRef, {
            name: newName,
            lastModified: Date.now()
        });
    };

    const value = {
        decks,
        loadingDecks,
        createDeck,
        updateDeck,
        deleteDeck,
        renameDeck
    };

    return (
        <DeckContext.Provider value={value}>
            {children}
        </DeckContext.Provider>
    );
};
