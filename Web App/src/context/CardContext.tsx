import React, { createContext, useContext, useEffect, useState } from 'react';
import { collection, onSnapshot, doc, setDoc, deleteDoc } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import type { Card, CardWithQuantity, BinderEntry } from '../models/card';
import type { User } from 'firebase/auth';
import { QuerySnapshot } from 'firebase/firestore';
import type { DocumentData } from 'firebase/firestore';

interface CardContextType {
    masterCards: Card[];
    binderCards: CardWithQuantity[];
    loading: boolean;
    updateCardQuantity: (cardId: string, quantity: number) => Promise<void>;
}

const CardContext = createContext<CardContextType>({} as CardContextType);

// eslint-disable-next-line react-refresh/only-export-components
export const useCards = () => useContext(CardContext);

export const CardProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [masterCards, setMasterCards] = useState<Card[]>([]);
    const [binderCards, setBinderCards] = useState<CardWithQuantity[]>([]);
    const [loading, setLoading] = useState(true);

    // 1. Fetch Local Master List
    useEffect(() => {
        fetch('/riftbound_data.json')
            .then(res => res.json())
            .then(data => setMasterCards(data))
            .catch(console.error);
    }, []);

    // 2. Stream Firebase Binder Quantities
    useEffect(() => {
        if (masterCards.length === 0) return;

        // The binder listener for the signed-in user. Kept here (not returned from the auth
        // callback, where it was ignored) so it is removed on sign-out and on unmount.
        let unsubscribeSnapshot: (() => void) | undefined;

        const unsubscribeAuth = auth.onAuthStateChanged((user: User | null) => {
            unsubscribeSnapshot?.();
            unsubscribeSnapshot = undefined;

            if (!user) {
                setBinderCards([]);
                setLoading(false);
                return;
            }

            setLoading(true);
            const entriesRef = collection(db, 'users', user.uid, 'binders', 'main', 'entries');

            unsubscribeSnapshot = onSnapshot(entriesRef, (snapshot: QuerySnapshot<DocumentData, DocumentData>) => {
                const entriesMap = new Map<string, number>();
                snapshot.forEach((doc: DocumentData) => {
                    const data = doc.data() as BinderEntry;
                    entriesMap.set(data.cardId, data.quantity);
                });

                const combined = masterCards.map(card => ({
                    card,
                    quantity: entriesMap.get(card.cardId) || 0
                }));

                setBinderCards(combined);
                setLoading(false);
            });
        });

        return () => {
            unsubscribeAuth();
            unsubscribeSnapshot?.();
        };
    }, [masterCards]);

    const updateCardQuantity = async (cardId: string, quantity: number) => {
        const user = auth.currentUser;
        if (!user) return;

        const docRef = doc(db, 'users', user.uid, 'binders', 'main', 'entries', cardId);

        if (quantity > 0) {
            await setDoc(docRef, { binderId: 'main', cardId, quantity });
        } else {
            await deleteDoc(docRef);
        }
    };

    const value = {
        masterCards,
        binderCards,
        loading,
        updateCardQuantity
    };

    return (
        <CardContext.Provider value={value}>
            {children}
        </CardContext.Provider>
    );
};
