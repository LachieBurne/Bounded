import React, { createContext, useContext, useEffect, useState } from 'react';
import { auth } from '../config/firebase';
import { onAuthStateChanged, GoogleAuthProvider, signInWithPopup, signOut } from 'firebase/auth';
import type { User } from 'firebase/auth';

interface AuthContextType {
    currentUser: User | null;
    loading: boolean;
    signInWithGoogle: () => Promise<void>;
    logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({} as AuthContextType);

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
    return useContext(AuthContext);
};

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [currentUser, setCurrentUser] = useState<User | null>(null);
    const [loading, setLoading] = useState(true);

    const signInWithGoogle = async () => {
        const provider = new GoogleAuthProvider();
        await signInWithPopup(auth, provider);
    };

    const logout = async () => {
        await signOut(auth);
    };

    useEffect(() => {
        const unsubscribe = onAuthStateChanged(auth, (user: User | null) => {
            setCurrentUser(user);
            setLoading(false);
        });

        return unsubscribe;
    }, []);

    const value = {
        currentUser,
        loading,
        signInWithGoogle,
        logout
    };

    return (
        <AuthContext.Provider value={value}>
            {loading ? (
                <div role="status" className="min-h-screen bg-ink flex flex-col items-center justify-center gap-4 text-muted">
                    <span aria-hidden="true" className="w-4 h-4 rotate-45 bg-gold animate-pulse" />
                    <span className="rift-label text-sm">Bounded</span>
                </div>
            ) : children}
        </AuthContext.Provider>
    );
};
