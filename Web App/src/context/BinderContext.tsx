import React, { createContext, useContext, useEffect, useState } from 'react';
import { collection, deleteDoc, doc, onSnapshot, setDoc } from 'firebase/firestore';
import type { User } from 'firebase/auth';
import { auth, db } from '../config/firebase';
import { MAIN_BINDER_ID, binderFrom, type Binder } from '../lib/binder';

interface BinderContextType {
    /** Sub-binders, oldest first. The collection itself ("main") isn't one of them. */
    binders: Binder[];
    /** False until the first snapshot arrives, so a binder link doesn't look missing. */
    loaded: boolean;
    saveBinder: (binder: Binder) => Promise<void>;
    deleteBinder: (binderId: string) => Promise<void>;
}

const BinderContext = createContext<BinderContextType>({} as BinderContextType);

// eslint-disable-next-line react-refresh/only-export-components
export const useBinders = () => useContext(BinderContext);

/** Follows users/{uid}/binders, the documents the Android app reads and writes too. */
export const BinderProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [binders, setBinders] = useState<Binder[]>([]);
    const [loaded, setLoaded] = useState(false);

    useEffect(() => {
        let unsubscribeSnapshot: (() => void) | undefined;
        const unsubscribeAuth = auth.onAuthStateChanged((user: User | null) => {
            unsubscribeSnapshot?.();
            unsubscribeSnapshot = undefined;
            if (!user) {
                setBinders([]);
                setLoaded(false);
                return;
            }
            unsubscribeSnapshot = onSnapshot(collection(db, 'users', user.uid, 'binders'), snapshot => {
                setBinders(snapshot.docs
                    .filter(d => d.id !== MAIN_BINDER_ID)
                    .map(d => binderFrom(d.id, d.data()))
                    .sort((a, b) => a.createdAt - b.createdAt));
                setLoaded(true);
            }, error => {
                console.error('Failed to load binders', error);
                setLoaded(true);
            });
        });
        return () => {
            unsubscribeAuth();
            unsubscribeSnapshot?.();
        };
    }, []);

    const saveBinder = async (binder: Binder) => {
        const user = auth.currentUser;
        if (!user || binder.binderId === MAIN_BINDER_ID) return;
        await setDoc(doc(db, 'users', user.uid, 'binders', binder.binderId), { ...binder });
    };

    /** Deletes a sub-binder. Its cards stay in the collection. */
    const deleteBinder = async (binderId: string) => {
        const user = auth.currentUser;
        if (!user || binderId === MAIN_BINDER_ID) return;
        await deleteDoc(doc(db, 'users', user.uid, 'binders', binderId));
    };

    return <BinderContext.Provider value={{ binders, loaded, saveBinder, deleteBinder }}>{children}</BinderContext.Provider>;
};
