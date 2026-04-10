import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { TopNavigation } from './TopNavigation';

export const ProtectedLayout: React.FC = () => {
    const { currentUser, loading } = useAuth();

    if (loading) {
        return (
            <div className="min-h-screen bg-riftbound-darkest flex items-center justify-center">
                <div className="w-16 h-16 border-4 border-white/10 border-t-riftbound-accent rounded-full animate-spin" />
            </div>
        );
    }

    if (!currentUser) {
        return <Navigate to="/login" replace />;
    }

    return (
        <div className="min-h-screen bg-riftbound-darkest flex flex-col relative overflow-hidden">
            {/* Global Abstract Background */}
            <div className="fixed inset-0 pointer-events-none mix-blend-screen opacity-40">
                <div className="absolute top-0 right-0 w-[500px] h-[500px] bg-riftbound-accent/10 rounded-full blur-[100px] -translate-y-1/2 translate-x-1/2" />
                <div className="absolute bottom-0 left-0 w-[600px] h-[600px] bg-purple-900/10 rounded-full blur-[120px] translate-y-1/3 -translate-x-1/3" />
            </div>

            <TopNavigation />

            <main className="flex-1 relative z-10">
                <Outlet />
            </main>
        </div>
    );
};
