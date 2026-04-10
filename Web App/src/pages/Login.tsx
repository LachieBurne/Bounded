import { useAuth } from '../context/AuthContext';
import { Navigate, Link } from 'react-router-dom';

export default function Login() {
    const { currentUser, signInWithGoogle } = useAuth();

    // If already logged in, redirect to the binder
    if (currentUser) {
        return <Navigate to="/" />;
    }

    return (
        <div className="min-h-screen flex items-center justify-center relative overflow-hidden bg-riftbound-darkest">

            {/* Background Decorators */}
            <div className="absolute -top-[20%] -left-[10%] w-[70vw] h-[70vw] bg-riftbound-accent/20 blur-[120px] rounded-full mix-blend-screen pointer-events-none" />
            <div className="absolute -bottom-[20%] -right-[10%] w-[60vw] h-[60vw] bg-purple-600/20 blur-[100px] rounded-full mix-blend-screen pointer-events-none" />

            {/* Main Card */}
            <div className="relative z-10 w-full max-w-md p-8 sm:p-12 mx-4 glass-panel rounded-3xl flex flex-col items-center text-center shadow-2xl border border-white/10 animate-fade-in-up">

                {/* Logo */}
                <img
                    src="/favicon.png"
                    alt="Bounded Logo"
                    className="w-24 h-24 mb-6 rotate-3 hover:rotate-6 transition-transform duration-500 drop-shadow-[0_0_40px_rgba(76,110,245,0.6)] object-contain"
                />

                <h1 className="text-3xl font-bold text-white mb-2">Bounded</h1>
                <p className="text-riftbound-textDim mb-10">Access your collection anywhere.</p>

                <button
                    onClick={signInWithGoogle}
                    className="w-full relative group overflow-hidden rounded-xl bg-white/5 border border-white/10 p-1 transition-all hover:bg-white/10 hover:border-white/20 hover:scale-[1.02] active:scale-[0.98]"
                >
                    <div className="absolute inset-0 bg-gradient-to-r from-riftbound-accent/50 to-purple-500/50 opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
                    <div className="relative flex items-center justify-center gap-3 bg-riftbound-darker py-3 px-4 rounded-lg">
                        <svg className="w-6 h-6" viewBox="0 0 24 24">
                            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
                            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
                        </svg>
                        <span className="font-semibold text-white">Continue with Google</span>
                    </div>
                </button>

            </div>

            {/* Legal Footer */}
            <div className="absolute bottom-6 flex gap-6 text-sm font-medium text-slate-500 z-10">
                <Link to="/privacy" className="transition-colors hover:text-emerald-400">Privacy Policy</Link>
                <Link to="/terms" className="transition-colors hover:text-emerald-400">Terms of Service</Link>
            </div>
        </div>
    );
}
