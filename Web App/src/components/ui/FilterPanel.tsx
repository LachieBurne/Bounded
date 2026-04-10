import React from 'react';
import { X } from 'lucide-react';
import type { CardType, Domain, RiftRarity } from '../../models/card';

interface FilterPanelProps {
    types: CardType[];
    domains: Domain[];
    rarities: RiftRarity[];
    sets: string[];

    selectedType: CardType | '';
    setSelectedType: (v: CardType | '') => void;
    selectedDomain: Domain | '';
    setSelectedDomain: (v: Domain | '') => void;
    selectedRarity: RiftRarity | '';
    setSelectedRarity: (v: RiftRarity | '') => void;
    selectedSet: string | '';
    setSelectedSet: (v: string | '') => void;
    selectedCost: number | '';
    setSelectedCost: (v: number | '') => void;
    selectedMight: number | '';
    setSelectedMight: (v: number | '') => void;
    maxMight: number;
}

export const FilterPanel: React.FC<FilterPanelProps> = ({
    types, domains, rarities, sets,
    selectedType, setSelectedType,
    selectedDomain, setSelectedDomain,
    selectedRarity, setSelectedRarity,
    selectedSet, setSelectedSet,
    selectedCost, setSelectedCost,
    selectedMight, setSelectedMight,
    maxMight
}) => {
    return (
        <div className="flex flex-col gap-6 mt-4 bg-white/5 p-6 rounded-xl border border-white/10 glass-panel relative z-10 w-full">
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                {/* Set Filter */}
                <div className="flex flex-col gap-2">
                    <label className="text-sm font-bold tracking-wider text-riftbound-textDim uppercase">Set</label>
                    <select
                        className="bg-riftbound-darker text-white p-3 rounded-lg border border-white/10 focus:border-riftbound-accent outline-none w-full appearance-none shadow-inner"
                        value={selectedSet}
                        onChange={(e) => setSelectedSet(e.target.value)}
                    >
                        <option value="">All Sets</option>
                        {sets.map(s => <option key={s} value={s}>{s}</option>)}
                    </select>
                </div>

                {/* Type Filter */}
                <div className="flex flex-col gap-2">
                    <label className="text-sm font-bold tracking-wider text-riftbound-textDim uppercase">Card Type</label>
                    <select
                        className="bg-riftbound-darker text-white p-3 rounded-lg border border-white/10 focus:border-riftbound-accent outline-none w-full appearance-none shadow-inner"
                        value={selectedType}
                        onChange={(e) => setSelectedType(e.target.value as CardType | '')}
                    >
                        <option value="">All Types</option>
                        {types.map(t => <option key={t} value={t}>{t}</option>)}
                    </select>
                </div>

                {/* Cost Filter */}
                <div className="flex flex-col gap-2">
                    <div className="flex justify-between items-center">
                        <label className="text-sm font-bold tracking-wider text-riftbound-textDim uppercase">Cost</label>
                        <div className="flex items-center gap-1">
                            <span className="text-sm font-bold text-white bg-white/10 px-2 py-0.5 rounded outline-none border border-transparent w-12 text-center">
                                {selectedCost === '' ? 'ANY' : selectedCost}
                            </span>
                            {selectedCost !== '' && (
                                <button
                                    onClick={() => setSelectedCost('')}
                                    className="p-1 hover:bg-white/10 rounded-full transition-colors"
                                >
                                    <X className="w-4 h-4 text-riftbound-textDim hover:text-white" />
                                </button>
                            )}
                        </div>
                    </div>
                    <input
                        type="range"
                        min="-1" max="12"
                        value={selectedCost === '' ? -1 : selectedCost}
                        onChange={e => setSelectedCost(e.target.value === '-1' ? '' : Number(e.target.value))}
                        className="w-full accent-riftbound-accent mt-2"
                    />
                </div>

                {/* Might Filter */}
                <div className="flex flex-col gap-2">
                    <div className="flex justify-between items-center">
                        <label className="text-sm font-bold tracking-wider text-riftbound-textDim uppercase">Might</label>
                        <div className="flex items-center gap-1">
                            <span className="text-sm font-bold text-white bg-white/10 px-2 py-0.5 rounded outline-none border border-transparent w-12 text-center">
                                {selectedMight === '' ? 'ANY' : selectedMight}
                            </span>
                            {selectedMight !== '' && (
                                <button
                                    onClick={() => setSelectedMight('')}
                                    className="p-1 hover:bg-white/10 rounded-full transition-colors"
                                >
                                    <X className="w-4 h-4 text-riftbound-textDim hover:text-white" />
                                </button>
                            )}
                        </div>
                    </div>
                    <input
                        type="range"
                        min="-1" max={maxMight}
                        value={selectedMight === '' ? -1 : selectedMight}
                        onChange={e => setSelectedMight(e.target.value === '-1' ? '' : Number(e.target.value))}
                        className="w-full accent-[#D32F2F] mt-2"
                    />
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-4 border-t border-white/5">
                {/* Domain Filter */}
                <div className="flex flex-col gap-3">
                    <label className="text-sm font-bold tracking-wider text-riftbound-textDim uppercase">Domain</label>
                    <div className="flex flex-wrap gap-2">
                        <button
                            onClick={() => setSelectedDomain('')}
                            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all border ${selectedDomain === '' ? 'bg-riftbound-accent text-white border-riftbound-accent shadow-lg shadow-riftbound-accent/20' : 'bg-riftbound-darker text-riftbound-textDim border-white/10 hover:bg-white/5 hover:text-white'}`}
                        >
                            Any
                        </button>
                        {domains.map(d => (
                            <button
                                key={d}
                                onClick={() => setSelectedDomain(d)}
                                className={`w-10 h-10 rounded-full flex items-center justify-center transition-all p-1 border-2 ${selectedDomain === d ? 'border-riftbound-accent bg-white/10 scale-110 shadow-lg shadow-riftbound-accent/20' : 'border-transparent bg-riftbound-darker hover:bg-white/5 hover:scale-105 opacity-60 hover:opacity-100'}`}
                                title={d}
                            >
                                <img src={`https://lachieburne.github.io/Riftbounded/domain_images/${d.toLowerCase()}.png`} alt={d} className="w-full h-full object-contain" />
                            </button>
                        ))}
                    </div>
                </div>

                {/* Rarity Filter */}
                <div className="flex flex-col gap-3">
                    <label className="text-sm font-bold tracking-wider text-riftbound-textDim uppercase">Rarity</label>
                    <div className="flex flex-wrap gap-2">
                        <button
                            onClick={() => setSelectedRarity('')}
                            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all border ${selectedRarity === '' ? 'bg-riftbound-accent text-white border-riftbound-accent shadow-lg shadow-riftbound-accent/20' : 'bg-riftbound-darker text-riftbound-textDim border-white/10 hover:bg-white/5 hover:text-white'}`}
                        >
                            Any
                        </button>
                        {rarities.map(r => (
                            <button
                                key={r}
                                onClick={() => setSelectedRarity(r)}
                                className={`w-10 h-10 rounded-full flex items-center justify-center transition-all p-1 border-2 ${selectedRarity === r ? 'border-riftbound-accent bg-white/10 scale-110 shadow-lg shadow-riftbound-accent/20' : 'border-transparent bg-riftbound-darker hover:bg-white/5 hover:scale-105 opacity-60 hover:opacity-100'}`}
                                title={r}
                            >
                                <img src={`https://lachieburne.github.io/Riftbounded/rarity_images/${r.toLowerCase()}.png`} alt={r} className="w-full h-full object-contain" />
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};
