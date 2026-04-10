import { useState, useMemo } from 'react';
import { useCards } from '../context/CardContext';
import { usePrices } from '../context/PriceContext';
import { CardGrid } from '../components/ui/CardGrid';
import { FilterPanel } from '../components/ui/FilterPanel';
import { CardDetailModal } from '../components/ui/CardDetailModal';
import { SleekSearchBar } from '../components/ui/SleekSearchBar';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import type { CardType, Domain, RiftRarity, CardWithQuantity } from '../models/card';

export default function BinderPage() {
    const { binderCards, masterCards, loading } = useCards();
    const [query, setQuery] = useState('');
    const [showOwnedOnly, setShowOwnedOnly] = useState(false);
    const [activeTab, setActiveTab] = useState<'CARDS' | 'STATS'>('CARDS');
    const { prices, getCurrencySymbol, convertPrice } = usePrices();

    // Filters
    const [selectedType, setSelectedType] = useState<CardType | ''>('');
    const [selectedDomain, setSelectedDomain] = useState<Domain | ''>('');
    const [selectedRarity, setSelectedRarity] = useState<RiftRarity | ''>('');
    const [selectedSet, setSelectedSet] = useState<string | ''>('');
    const [selectedCost, setSelectedCost] = useState<number | ''>('');
    const [selectedMight, setSelectedMight] = useState<number | ''>('');

    // Modal
    const [selectedCard, setSelectedCard] = useState<CardWithQuantity | null>(null);

    // Dynamic Options from master list
    const types = useMemo(() => Array.from(new Set(masterCards.map(c => c.type))).sort(), [masterCards]);

    const domains = useMemo(() => {
        const DOMAIN_ORDER: Domain[] = ['MIND', 'CALM', 'BODY', 'ORDER', 'CHAOS', 'FURY', 'COLORLESS'];
        const unique = Array.from(new Set(masterCards.flatMap(c => c.domain)));
        return unique.sort((a, b) => {
            const indexA = DOMAIN_ORDER.indexOf(a as Domain);
            const indexB = DOMAIN_ORDER.indexOf(b as Domain);
            return (indexA === -1 ? 99 : indexA) - (indexB === -1 ? 99 : indexB);
        }) as Domain[];
    }, [masterCards]);

    const rarities = useMemo(() => {
        const RARITY_ORDER: RiftRarity[] = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'SHOWCASE'];
        const unique = Array.from(new Set(masterCards.map(c => c.rarity)));
        return unique.sort((a, b) => {
            const indexA = RARITY_ORDER.indexOf(a as RiftRarity);
            const indexB = RARITY_ORDER.indexOf(b as RiftRarity);
            return (indexA === -1 ? 99 : indexA) - (indexB === -1 ? 99 : indexB);
        }) as RiftRarity[];
    }, [masterCards]);

    const sets = useMemo(() => Array.from(new Set(masterCards.map(c => c.setCode))).sort(), [masterCards]);

    const maxMight = useMemo(() => {
        return Math.max(0, ...masterCards.map(c => c.might || 0));
    }, [masterCards]);

    const filteredCards = useMemo(() => {
        let result = [...binderCards];

        result.sort((a, b) => {
            const setComp = a.card.setCode.localeCompare(b.card.setCode);
            if (setComp !== 0) return setComp;

            const numA = parseInt(a.card.collectorNumber) || 0;
            const numB = parseInt(b.card.collectorNumber) || 0;
            return numA - numB;
        });

        if (query) {
            const lowerQ = query.toLowerCase();
            result = result.filter(c =>
                c.card.name.toLowerCase().includes(lowerQ) ||
                (c.card.artistName && c.card.artistName.toLowerCase().includes(lowerQ))
            );
        }

        if (showOwnedOnly) {
            result = result.filter(c => c.quantity > 0);
        }

        if (selectedType) {
            result = result.filter(c => c.card.type === selectedType);
        }
        if (selectedDomain) {
            result = result.filter(c => c.card.domain.includes(selectedDomain));
        }
        if (selectedRarity) {
            result = result.filter(c => c.card.rarity === selectedRarity);
        }
        if (selectedSet) {
            result = result.filter(c => c.card.setCode === selectedSet);
        }
        if (selectedCost !== '') {
            result = result.filter(c => c.card.cost === selectedCost);
        }
        if (selectedMight !== '') {
            result = result.filter(c => c.card.might === selectedMight);
        }

        return result;
    }, [binderCards, query, showOwnedOnly, selectedType, selectedDomain, selectedRarity, selectedSet, selectedCost, selectedMight]);

    const stats = useMemo(() => {
        let totalCards = 0;
        let uniqueCards = 0;
        let totalValueRaw = 0;
        const bySet: Record<string, { owned: number, total: number }> = {};
        const byType: Record<string, number> = {};
        const byRarity: Record<string, number> = {};

        // Initialize sets
        masterCards.forEach(c => {
            if (!bySet[c.setCode]) {
                bySet[c.setCode] = { owned: 0, total: 0 };
            }
            const isBaseRune = c.type === 'RUNE' && !/[a-z]/.test(c.cardId.split('-')[1] || '');
            if (!isBaseRune) {
                bySet[c.setCode].total += 1;
            }
        });

        binderCards.forEach(bc => {
            const { card, quantity } = bc;
            const isBaseRune = card.type === 'RUNE' && !/[a-z]/.test(card.cardId.split('-')[1] || '');

            if (quantity > 0) {
                if (!isBaseRune) {
                    totalCards += quantity;
                    totalValueRaw += quantity * (prices[card.cardId] || 0);
                }
                uniqueCards += 1;

                if (!byType[card.type]) byType[card.type] = 0;
                byType[card.type] += quantity;

                if (!byRarity[card.rarity]) byRarity[card.rarity] = 0;
                byRarity[card.rarity] += quantity;

                if (!isBaseRune && bySet[card.setCode]) {
                    bySet[card.setCode].owned += 1;
                }
            }
        });

        return { totalCards, uniqueCards, totalValueRaw, bySet, byType, byRarity };
    }, [binderCards, masterCards, prices]);

    // Format data for Recharts
    const rarityChartData = useMemo(() => {
        const RARITY_ORDER: string[] = ['COMMON', 'UNCOMMON', 'RARE', 'EPIC', 'SHOWCASE'];
        return Object.entries(stats.byRarity)
            .map(([name, value]) => ({ name, value }))
            .sort((a, b) => {
                const indexA = RARITY_ORDER.indexOf(a.name);
                const indexB = RARITY_ORDER.indexOf(b.name);
                return (indexA === -1 ? 99 : indexA) - (indexB === -1 ? 99 : indexB);
            });
    }, [stats.byRarity]);

    const typeChartData = useMemo(() => {
        return Object.entries(stats.byType)
            .map(([name, value]) => ({ name, value }))
            .sort((a, b) => b.value - a.value);
    }, [stats.byType]);

    const RARITY_COLORS: Record<string, string> = {
        'COMMON': '#9ca3af',
        'UNCOMMON': '#06b6d4',
        'RARE': '#c026d3',
        'EPIC': '#f97316',
        'SHOWCASE': '#eab308'
    };

    const TYPE_COLORS: Record<string, string> = {
        'UNIT': '#ef4444',
        'SPELL': '#3b82f6',
        'GEAR': '#f97316',
        'RUNE': '#a855f7',
        'BATTLEFIELD': '#22c55e',
        'LEGEND': '#eab308'
    };

    if (loading) {
        return (
            <div className="flex-1 flex flex-col items-center justify-center p-8 gap-6 h-full">
                <div className="w-16 h-16 border-4 border-riftbound-accent border-t-transparent rounded-full animate-spin" />
                <h2 className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-riftbound-accentBright to-white animate-pulse">
                    Syncing Collection...
                </h2>
            </div>
        );
    }

    return (
        <div className="flex flex-col h-full mx-auto max-w-7xl pt-6 px-4 pb-20">

            {/* Header Toolbar */}
            <div className="flex flex-col md:flex-row items-center gap-4 mb-8 glass-panel p-4 rounded-2xl relative overflow-hidden">
                {/* Decorative Background */}
                <div className="absolute top-0 right-0 w-64 h-64 bg-riftbound-accent/10 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2" />

                <div className="flex-1 w-full relative z-10">
                    <SleekSearchBar query={query} setQuery={setQuery} />
                </div>

                <div className="flex flex-wrap justify-center items-center gap-2 relative z-10 w-full md:w-auto">
                    <button
                        onClick={() => setShowOwnedOnly(!showOwnedOnly)}
                        className={`flex-1 md:flex-none px-6 py-3 rounded-xl font-medium transition-all duration-300 border whitespace-nowrap
              ${showOwnedOnly
                                ? 'bg-riftbound-accent/20 border-riftbound-accent text-white shadow-lg shadow-riftbound-accent/20'
                                : 'bg-white/5 border-white/10 text-riftbound-textDim hover:bg-white/10 hover:text-white'}`}
                    >
                        {showOwnedOnly ? 'Showing Owned' : 'Show All Cards'}
                    </button>
                    <button
                        onClick={() => {
                            setSelectedType('');
                            setSelectedDomain('');
                            setSelectedRarity('');
                            setSelectedSet('');
                            setSelectedCost('');
                            setSelectedMight('');
                        }}
                        className="px-6 py-3 rounded-xl font-medium transition-all duration-300 border bg-white/5 border-white/10 text-riftbound-textDim hover:bg-white/10 hover:text-white"
                    >
                        Reset
                    </button>

                    <div className="w-px h-8 bg-white/10 mx-2 hidden md:block"></div>

                    <div className="flex w-full md:w-auto justify-center items-center gap-1 bg-black/40 p-1 rounded-xl border border-white/5 mt-2 md:mt-0">
                        <button
                            onClick={() => setActiveTab('CARDS')}
                            className={`flex-1 md:flex-none px-6 py-2 rounded-lg font-bold transition-all ${activeTab === 'CARDS' ? 'bg-riftbound-accent text-white shadow-md' : 'text-riftbound-textDim hover:text-white'}`}
                        >
                            Cards
                        </button>
                        <button
                            onClick={() => setActiveTab('STATS')}
                            className={`flex-1 md:flex-none px-6 py-2 rounded-lg font-bold transition-all ${activeTab === 'STATS' ? 'bg-riftbound-accent text-white shadow-md' : 'text-riftbound-textDim hover:text-white'}`}
                        >
                            Stats
                        </button>
                    </div>
                </div>
            </div>

            <div className={`mb-6 -mt-6 transition-all duration-300 ${activeTab === 'STATS' ? 'opacity-0 h-0 overflow-hidden mb-0' : 'opacity-100'}`}>
                <FilterPanel
                    types={types} domains={domains} rarities={rarities} sets={sets}
                    selectedType={selectedType} setSelectedType={setSelectedType}
                    selectedDomain={selectedDomain} setSelectedDomain={setSelectedDomain}
                    selectedRarity={selectedRarity} setSelectedRarity={setSelectedRarity}
                    selectedSet={selectedSet} setSelectedSet={setSelectedSet}
                    selectedCost={selectedCost} setSelectedCost={setSelectedCost}
                    selectedMight={selectedMight} setSelectedMight={setSelectedMight}
                    maxMight={maxMight}
                />
            </div>

            {/* Content Area */}
            <div className="flex-1">
                {activeTab === 'CARDS' ? (
                    filteredCards.length > 0 ? (
                        <CardGrid cards={filteredCards} onCardClick={(item) => setSelectedCard(item)} />
                    ) : (
                        <div className="flex flex-col items-center justify-center py-20 opacity-50">
                            <h3 className="text-2xl font-bold mb-2">No cards found</h3>
                            <p>Try adjusting your search criteria</p>
                        </div>
                    )
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 animate-fade-in">

                        {/* Summary Cards */}
                        <div className="glass-panel p-6 rounded-2xl flex flex-col justify-center relative overflow-hidden group">
                            <div className="absolute inset-0 bg-gradient-to-br from-riftbound-accent/20 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                            <span className="text-riftbound-textDim font-bold text-sm uppercase tracking-wider mb-2 relative z-10">Total Value</span>
                            <span className="text-4xl font-black text-green-400 relative z-10">
                                {getCurrencySymbol()}{convertPrice(stats.totalValueRaw).toFixed(2)}
                            </span>
                        </div>

                        <div className="glass-panel p-6 rounded-2xl flex flex-col justify-center relative overflow-hidden group">
                            <div className="absolute inset-0 bg-gradient-to-br from-white/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                            <span className="text-riftbound-textDim font-bold text-sm uppercase tracking-wider mb-2 relative z-10">Total Cards</span>
                            <span className="text-4xl font-black text-white relative z-10">{stats.totalCards}</span>
                        </div>

                        <div className="glass-panel p-6 rounded-2xl flex flex-col justify-center relative overflow-hidden group">
                            <div className="absolute inset-0 bg-gradient-to-br from-white/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                            <span className="text-riftbound-textDim font-bold text-sm uppercase tracking-wider mb-2 relative z-10">Unique Prints</span>
                            <span className="text-4xl font-black text-white relative z-10">{stats.uniqueCards} <span className="text-lg text-white/40">/ {masterCards.length}</span></span>
                        </div>

                        <div className="glass-panel p-6 rounded-2xl flex flex-col justify-center relative overflow-hidden group">
                            <div className="absolute inset-0 bg-gradient-to-br from-white/5 to-transparent opacity-0 group-hover:opacity-100 transition-opacity" />
                            <span className="text-riftbound-textDim font-bold text-sm uppercase tracking-wider mb-2 relative z-10">Collection Completion</span>
                            <span className="text-4xl font-black text-white relative z-10">
                                {masterCards.length > 0 ? ((stats.uniqueCards / masterCards.length) * 100).toFixed(1) : 0}%
                            </span>
                        </div>

                        {/* Set Breakdown */}
                        <div className="glass-panel p-6 rounded-2xl col-span-1 md:col-span-2 lg:col-span-2">
                            <h3 className="text-xl font-bold mb-6 text-white border-b border-white/10 pb-4">Set Completion</h3>
                            <div className="flex flex-col gap-4">
                                {Object.entries(stats.bySet).map(([set, counts]) => {
                                    if (counts.total === 0) return null;
                                    const percentage = (counts.owned / counts.total) * 100;
                                    return (
                                        <div key={set}>
                                            <div className="flex justify-between text-sm font-bold mb-1">
                                                <span className="text-white">{set}</span>
                                                <span className="text-riftbound-textDim">{counts.owned} / {counts.total} ({percentage.toFixed(0)}%)</span>
                                            </div>
                                            <div className="h-2 bg-black/40 rounded-full overflow-hidden">
                                                <div
                                                    className="h-full bg-riftbound-accent rounded-full transition-all duration-1000 ease-out"
                                                    style={{ width: `${percentage}%` }}
                                                />
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        {/* Rarity Breakdown */}
                        <div className="glass-panel p-6 rounded-2xl col-span-1 md:col-span-1 lg:col-span-1 flex flex-col items-center">
                            <h3 className="text-xl font-bold mb-2 text-white border-b border-white/10 pb-4 w-full text-center">By Rarity</h3>
                            {rarityChartData.length > 0 ? (
                                <div className="w-full aspect-square relative -my-4">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={rarityChartData}
                                                cx="50%"
                                                cy="50%"
                                                innerRadius="60%"
                                                outerRadius="80%"
                                                paddingAngle={5}
                                                dataKey="value"
                                                stroke="none"
                                            >
                                                {rarityChartData.map((entry, index) => (
                                                    <Cell key={`cell-${index}`} fill={RARITY_COLORS[entry.name] || '#ffffff'} className="hover:opacity-80 transition-opacity outline-none" />
                                                ))}
                                            </Pie>
                                            <Tooltip
                                                formatter={(value: any, name: any) => [value, name]}
                                                contentStyle={{ backgroundColor: 'rgba(0,0,0,0.8)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px' }}
                                                itemStyle={{ color: '#fff', fontWeight: 'bold', textTransform: 'capitalize' }}
                                            />
                                            <Legend
                                                layout="horizontal"
                                                verticalAlign="bottom"
                                                align="center"
                                                wrapperStyle={{ fontSize: '12px', color: '#9ca3af' }}
                                            />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                            ) : (
                                <div className="flex-1 flex items-center justify-center text-riftbound-textDim text-sm">No data</div>
                            )}
                        </div>

                        {/* Type Breakdown */}
                        <div className="glass-panel p-6 rounded-2xl col-span-1 md:col-span-1 lg:col-span-1 flex flex-col items-center">
                            <h3 className="text-xl font-bold mb-2 text-white border-b border-white/10 pb-4 w-full text-center">By Type</h3>
                            {typeChartData.length > 0 ? (
                                <div className="w-full aspect-square relative -my-4">
                                    <ResponsiveContainer width="100%" height="100%">
                                        <PieChart>
                                            <Pie
                                                data={typeChartData}
                                                cx="50%"
                                                cy="50%"
                                                innerRadius="60%"
                                                outerRadius="80%"
                                                paddingAngle={5}
                                                dataKey="value"
                                                stroke="none"
                                            >
                                                {typeChartData.map((entry, index) => (
                                                    <Cell key={`cell-${index}`} fill={TYPE_COLORS[entry.name] || '#ffffff'} className="hover:opacity-80 transition-opacity outline-none" />
                                                ))}
                                            </Pie>
                                            <Tooltip
                                                formatter={(value: any, name: any) => [value, name]}
                                                contentStyle={{ backgroundColor: 'rgba(0,0,0,0.8)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '8px' }}
                                                itemStyle={{ color: '#fff', fontWeight: 'bold', textTransform: 'capitalize' }}
                                            />
                                            <Legend
                                                layout="horizontal"
                                                verticalAlign="bottom"
                                                align="center"
                                                wrapperStyle={{ fontSize: '12px', color: '#9ca3af' }}
                                            />
                                        </PieChart>
                                    </ResponsiveContainer>
                                </div>
                            ) : (
                                <div className="flex-1 flex items-center justify-center text-riftbound-textDim text-sm">No data</div>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* Modal */}
            {selectedCard && (
                <CardDetailModal
                    item={selectedCard}
                    onClose={() => setSelectedCard(null)}
                />
            )}
        </div>
    );
}
