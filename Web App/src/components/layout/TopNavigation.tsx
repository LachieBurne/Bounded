import { useAuth } from '../../context/AuthContext';
import { LogOut, Book, LayoutDashboard } from 'lucide-react';
import { NavLink } from 'react-router-dom';

export const TopNavigation = () => {
    const { currentUser, logout } = useAuth();

    return (
        <nav className="sticky top-0 z-50 w-full glass-panel border-b border-white/10 py-3 px-6">
            <div className="max-w-7xl mx-auto flex items-center justify-between">

                {/* Brand */}
                <div className="flex items-center gap-2">
                    <img src="/favicon.png" alt="Bounded Logo" className="w-8 h-8 rounded drop-shadow-lg object-contain" />
                    <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-white to-gray-400">
                        Bounded
                    </span>
                </div>

                {/* Links */}
                <div className="flex gap-2">
                    <NavLink
                        to="/"
                        className={({ isActive }) => `nav-btn ${isActive ? 'nav-btn-active' : 'text-riftbound-textDim'}`}
                    >
                        <Book className="w-4 h-4" />
                        <span className="hidden sm:inline">Binder</span>
                    </NavLink>
                    <NavLink
                        to="/decks"
                        className={({ isActive }) => `nav-btn ${isActive ? 'nav-btn-active' : 'text-riftbound-textDim'}`}
                    >
                        <LayoutDashboard className="w-4 h-4" />
                        <span className="hidden sm:inline">Decks</span>
                    </NavLink>
                </div>

                {/* User Actions & Legal */}
                <div className="flex items-center gap-4">
                    <div className="hidden md:flex items-center gap-4 mr-2 text-[11px] font-medium text-slate-500 uppercase tracking-wider">
                        <NavLink to="/privacy" className="hover:text-emerald-400 transition-colors">Privacy</NavLink>
                        <NavLink to="/terms" className="hover:text-emerald-400 transition-colors">Terms</NavLink>
                    </div>

                    {currentUser && (
                        <div className="flex items-center gap-4">
                            <div className="flex items-center gap-2">
                                <img src={currentUser.photoURL || ''} alt="Profile" className="w-8 h-8 rounded-full border border-white/20" />
                                <span className="text-sm font-medium hidden sm:block">{currentUser.displayName}</span>
                            </div>
                            <button
                                onClick={logout}
                                className="p-2 text-riftbound-textDim hover:text-white hover:bg-white/10 rounded-lg transition-colors"
                            >
                                <LogOut className="w-5 h-5" />
                            </button>
                        </div>
                    )}
                </div>

            </div>
        </nav>
    );
};
