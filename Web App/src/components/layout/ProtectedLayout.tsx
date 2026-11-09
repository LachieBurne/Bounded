import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Loading } from '../rift/Rift';
import { TopNavigation } from './TopNavigation';

export const ProtectedLayout: React.FC = () => {
    const { currentUser, loading } = useAuth();

    if (loading) {
        return <div className="min-h-screen bg-ink flex"><Loading label="Loading" /></div>;
    }

    if (!currentUser) {
        return <Navigate to="/login" replace />;
    }

    return (
        <div className="min-h-screen bg-ink flex flex-col">
            <a href="#content" className="sr-only focus:not-sr-only focus:absolute focus:z-[60] focus:m-2 focus:p-2 focus:bg-gold focus:text-on-gold">
                Skip to content
            </a>
            <TopNavigation />
            <main id="content" className="flex-1 flex flex-col">
                <Outlet />
            </main>
        </div>
    );
};
