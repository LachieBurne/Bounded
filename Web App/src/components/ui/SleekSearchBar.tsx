import { Search } from 'lucide-react';

interface SearchBarProps {
    query: string;
    setQuery: (q: string) => void;
    placeholder?: string;
}

export const SleekSearchBar = ({ query, setQuery, placeholder = "Search your binder..." }: SearchBarProps) => {
    return (
        <div className="relative w-full max-w-2xl group">
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                <Search className="h-5 w-5 text-riftbound-accentBright/70 group-focus-within:text-riftbound-accent transition-colors" />
            </div>
            <input
                type="text"
                className="block w-full pl-12 pr-4 py-3 bg-white/5 border border-white/10 rounded-2xl 
                   text-white placeholder-riftbound-textDim/50 backdrop-blur-md
                   focus:outline-none focus:ring-2 focus:ring-riftbound-accent/50 focus:border-riftbound-accent/50
                   transition-all duration-300 shadow-inner shadow-black/50 hover:bg-white/10"
                placeholder={placeholder}
                value={query}
                onChange={(e) => setQuery(e.target.value)}
            />

            {/* Glow Effect */}
            <div className="absolute -inset-0.5 bg-gradient-to-r from-riftbound-accent to-purple-600 rounded-2xl opacity-0 group-focus-within:opacity-20 blur transition-opacity duration-500 -z-10" />
        </div>
    );
};
