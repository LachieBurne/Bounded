import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import { collection, onSnapshot, doc, setDoc, deleteDoc } from 'firebase/firestore';
import { db, auth } from '../config/firebase';
import type { Card, CardWithQuantity, BinderEntry } from '../models/card';
import { adding, normalised, withTotal, type Finish, type FinishCounts } from '../lib/finishes';
import type { User } from 'firebase/auth';
import { QuerySnapshot } from 'firebase/firestore';
import type { DocumentData } from 'firebase/firestore';

interface CardContextType {
    masterCards: Card[];
    binderCards: CardWithQuantity[];
    loading: boolean;
    /** Sets how many copies are owned in all, keeping the finish counts (see withTotal in lib/finishes.ts). */
    updateCardQuantity: (cardId: string, quantity: number) => Promise<void>;
    /** Adds (or, when negative, removes) copies of one finish. Does nothing if that finish would go below zero. */
    changeFinish: (cardId: string, finish: Finish, delta: number) => Promise<void>;
}

const CardContext = createContext<CardContextType>({} as CardContextType);

// eslint-disable-next-line react-refresh/only-export-components
export const useCards = () => useContext(CardContext);

export const CardProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [masterCards, setMasterCards] = useState<Card[]>([]);
    const [binderCards, setBinderCards] = useState<CardWithQuantity[]>([]);
    const [loading, setLoading] = useState(true);
    // The latest counts by card, for edits to build on without waiting for a re-render.
    const counts = useRef(new Map<string, FinishCounts>());

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
                const entries = new Map<string, FinishCounts>();
                snapshot.forEach((doc: DocumentData) => {
                    const data = doc.data() as BinderEntry;
                    entries.set(data.cardId, normalised(data));
                });
                counts.current = entries;

                const combined = masterCards.map(card => ({
                    card,
                    ...(entries.get(card.cardId) ?? { quantity: 0, finishes: {} }),
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

    const save = async (cardId: string, next: FinishCounts) => {
        const user = auth.currentUser;
        if (!user) return;
        const docRef = doc(db, 'users', user.uid, 'binders', 'main', 'entries', cardId);
        counts.current.set(cardId, next);
        if (next.quantity > 0) {
            await setDoc(docRef, { binderId: 'main', cardId, ...next });
        } else {
            await deleteDoc(docRef);
        }
    };

    const current = (cardId: string) => counts.current.get(cardId) ?? { quantity: 0, finishes: {} };

    const updateCardQuantity = async (cardId: string, quantity: number) => {
        await save(cardId, withTotal(current(cardId), Math.max(0, quantity)));
    };

    const changeFinish = async (cardId: string, finish: Finish, delta: number) => {
        const next = adding(current(cardId), finish, delta);
        if (next) await save(cardId, next);
    };

    const value = {
        masterCards,
        binderCards,
        loading,
        updateCardQuantity,
        changeFinish,
    };

    return (
        <CardContext.Provider value={value}>
            {children}
        </CardContext.Provider>
    );
};
