import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { LogOut, Search } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { CURRENCIES, usePrices } from '../../context/PriceContext';
import { Diamond } from '../rift/Rift';

const isTyping = (target: EventTarget | null) =>
    target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));

const navClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center px-2 sm:px-3.5 border-b-2 rift-number text-base sm:text-[17px] tracking-[0.1em] uppercase ${isActive ? 'border-gold text-gold' : 'border-transparent text-parchment hover:text-ivory'}`;

/**
 * The bar across every signed-in page: Bounded, Collection and Decks, card search (press /)
 * and the account menu, which holds the currency setting.
 */
export const TopNavigation = () => {
    const { currentUser, logout } = useAuth();
    const { currency, setCurrency } = usePrices();
    const location = useLocation();
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const searchRef = useRef<HTMLInputElement>(null);
    const [menuOpen, setMenuOpen] = useState(false);
    const menuRef = useRef<HTMLDivElement>(null);

    const onCollection = location.pathname === '/' || location.pathname.startsWith('/binders/');
    const query = onCollection ? params.get('q') ?? '' : '';

    const setQuery = (value: string) => {
        if (onCollection) {
            setParams(prev => {
                const next = new URLSearchParams(prev);
                if (value) next.set('q', value); else next.delete('q');
                return next;
            }, { replace: true });
        } else {
            navigate(`/?q=${encodeURIComponent(value)}`);
        }
    };

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => {
            if (e.key === '/' && !isTyping(e.target) && !e.metaKey && !e.ctrlKey) {
                e.preventDefault();
                searchRef.current?.focus();
            }
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, []);

    useEffect(() => {
        if (!menuOpen) return;
        const close = (e: MouseEvent | KeyboardEvent) => {
            if (e instanceof KeyboardEvent ? e.key === 'Escape' : !menuRef.current?.contains(e.target as Node)) setMenuOpen(false);
        };
        window.addEventListener('mousedown', close);
        window.addEventListener('keydown', close);
        return () => {
            window.removeEventListener('mousedown', close);
            window.removeEventListener('keydown', close);
        };
    }, [menuOpen]);

    const initials = (currentUser?.displayName ?? currentUser?.email ?? '?')
        .split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase();

    return (
        <header className="sticky top-0 z-50 bg-bar border-b border-line">
            <div className="flex flex-wrap items-center gap-x-3 sm:gap-x-7 gap-y-2 px-4 sm:px-8 min-h-16 py-2 sm:py-0">
                <NavLink to="/" className="flex items-center gap-2.5 text-ivory">
                    <Diamond />
                    <span className="rift-label text-lg sm:text-[22px] tracking-[0.14em]">Bounded</span>
                </NavLink>

                <nav aria-label="Main" className="flex self-stretch gap-1 min-h-12">
                    <NavLink to="/" end className={({ isActive }) => navClass({ isActive: isActive || onCollection })}>Collection</NavLink>
                    <NavLink to="/decks" className={navClass}>Decks</NavLink>
                </nav>

                <label className="order-last sm:order-none basis-full sm:basis-auto flex-1 sm:max-w-xl flex items-center gap-2.5 h-10 px-3 bg-surface border border-line text-muted focus-within:border-gold-deep">
                    <Search aria-hidden="true" className="w-[18px] h-[18px] shrink-0" />
                    <span className="sr-only">Search cards</span>
                    <input
                        ref={searchRef}
                        type="search"
                        value={query}
                        onChange={e => setQuery(e.target.value)}
                        placeholder="Search cards by name, tag, artist or ID"
                        className="flex-1 min-w-0 bg-transparent outline-none text-ivory placeholder:text-muted text-[15px]"
                    />
                    <kbd className="hidden sm:inline px-1.5 border border-line text-xs text-muted font-sans">/</kbd>
                </label>

                <div ref={menuRef} className="relative ml-auto">
                    <button
                        type="button"
                        aria-haspopup="true"
                        aria-expanded={menuOpen}
                        aria-label="Account and settings"
                        onClick={() => setMenuOpen(o => !o)}
                        className="w-10 h-10 flex items-center justify-center border border-gold-deep bg-gold-wash text-gold rift-number text-base cursor-pointer overflow-hidden"
                    >
                        {currentUser?.photoURL ? <img src={currentUser.photoURL} alt="" className="w-full h-full object-cover" /> : initials}
                    </button>
                    {menuOpen && (
                        <div className="absolute right-0 top-12 w-72 panel shadow-2xl shadow-black/60 p-4 flex flex-col gap-4">
                            {currentUser && (
                                <div className="flex flex-col">
                                    <span className="font-semibold">{currentUser.displayName}</span>
                                    <span className="text-sm text-muted truncate">{currentUser.email}</span>
                                </div>
                            )}
                            <label className="flex flex-col gap-1.5 text-sm text-muted">
                                Prices in
                                <select value={currency} onChange={e => setCurrency(e.target.value)} className="field h-10 text-[15px]">
                                    {CURRENCIES.map(c => <option key={c.code} value={c.code}>{c.code} · {c.name}</option>)}
                                </select>
                                <span className="text-xs">Converted from TCGplayer's US prices.</span>
                            </label>
                            <div className="flex gap-4 text-sm">
                                <NavLink to="/privacy" className="text-muted hover:text-gold">Privacy</NavLink>
                                <NavLink to="/terms" className="text-muted hover:text-gold">Terms</NavLink>
                            </div>
                            <button type="button" onClick={logout} className="btn-quiet justify-start">
                                <LogOut aria-hidden="true" className="w-4 h-4" /> Sign out
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
};
