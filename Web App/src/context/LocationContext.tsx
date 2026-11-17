import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { collection, deleteDoc, doc, onSnapshot, setDoc } from 'firebase/firestore';
import type { User } from 'firebase/auth';
import { auth, db } from '../config/firebase';
import { knownPlaces, normalised, type Places } from '../lib/places';

/**
 * Where you keep each card's copies, from users/{uid}/locations/{cardId}: { cardId, places }.
 * Kept apart from the collection entries, which every app rewrites whole, so a build that only
 * knows quantities can't erase a location (docs/binders.md, Locations).
 */
interface LocationContextType {
    /** Stored places by card ID. Fit them to the copies owned with normalised() before showing them. */
    places: ReadonlyMap<string, Places>;
    /** Place names used anywhere, most-used first, for suggestions. */
    known: string[];
    /** Saves a card's places, or removes them when there are none. Throws if the save fails. */
    savePlaces: (cardId: string, places: Places, quantity: number) => Promise<void>;
}

const LocationContext = createContext<LocationContextType>({ places: new Map(), known: [], savePlaces: async () => {} });

// eslint-disable-next-line react-refresh/only-export-components
export const useLocations = () => useContext(LocationContext);

export const LocationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [places, setPlaces] = useState<ReadonlyMap<string, Places>>(new Map());

    useEffect(() => {
        let unsubscribe: (() => void) | undefined;
        const unsubscribeAuth = auth.onAuthStateChanged((user: User | null) => {
            unsubscribe?.();
            unsubscribe = undefined;
            setPlaces(new Map());
            if (!user) return;
            unsubscribe = onSnapshot(
                collection(db, 'users', user.uid, 'locations'),
                snapshot => {
                    const next = new Map<string, Places>();
                    snapshot.forEach(d => {
                        const data = d.data() as { cardId?: string; places?: Places };
                        if (data.places) next.set(data.cardId ?? d.id, data.places);
                    });
                    setPlaces(next);
                },
                // Before the Firestore rule for this path is deployed, reads are refused. Locations
                // are optional, so the app carries on without them.
                error => console.warn('Card locations unavailable', error),
            );
        });
        return () => {
            unsubscribeAuth();
            unsubscribe?.();
        };
    }, []);

    const known = useMemo(() => knownPlaces(places.values()), [places]);

    const savePlaces = async (cardId: string, next: Places, quantity: number) => {
        const user = auth.currentUser;
        if (!user) return;
        const ref = doc(db, 'users', user.uid, 'locations', cardId);
        const fitted = Object.fromEntries(normalised(next, quantity));
        if (Object.keys(fitted).length === 0) await deleteDoc(ref);
        else await setDoc(ref, { cardId, places: fitted });
    };

    return <LocationContext.Provider value={{ places, known, savePlaces }}>{children}</LocationContext.Provider>;
};
