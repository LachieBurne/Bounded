import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import type { CardType, Domain, RiftRarity } from '../../models/card';
import { EMPTY_FILTER, STAT_MAX, toggle, type CardFilter } from '../../lib/cardFilter';
import { DOMAIN_BAND, DOMAIN_LABEL, DOMAIN_SIGIL, FILTERABLE_TYPES, RARITIES, RARITY_LABEL, TYPE_LABEL } from '../../lib/style';
import { DomainIcon, RarityGem } from '../rift/Rift';

const STATS = [0, 1, 2, 3, 4, 5, 6, 7, STAT_MAX];
const statLabel = (n: number) => (n === STAT_MAX ? `${STAT_MAX}+` : String(n));

function Section({ title, children }: { title: string; children: ReactNode }) {
    return (
        <fieldset className="flex flex-col gap-2">
            <legend className="text-[13px] text-muted mb-2">{title}</legend>
            {children}
        </fieldset>
    );
}

/**
 * The collection's filters. They apply as you choose them; Reset clears everything but the
 * search box. [domains] limits the domain toggles, e.g. to a deck's legend.
 */
export function FilterPanel({ filter, onChange, sets, onClose, domains = ['FURY', 'CALM', 'MIND', 'BODY', 'CHAOS', 'ORDER', 'COLORLESS'] }: {
    filter: CardFilter;
    onChange: (f: CardFilter) => void;
    sets: { code: string; name: string }[];
    onClose?: () => void;
    domains?: Domain[];
}) {
    const set = (patch: Partial<CardFilter>) => onChange({ ...filter, ...patch });
    return (
        <div className="flex flex-col gap-5 p-[18px]">
            <div className="flex items-center justify-between">
                <span className="rift-label text-lg">Filters</span>
                <div className="flex items-center gap-1">
                    <button type="button" onClick={() => onChange({ ...EMPTY_FILTER, query: filter.query, ownership: filter.ownership })} className="text-sm text-gold hover:text-gold-bright px-2 min-h-10 cursor-pointer">
                        Reset
                    </button>
                    {onClose && (
                        <button type="button" aria-label="Close filters" onClick={onClose} className="w-10 h-10 flex items-center justify-center text-parchment hover:text-ivory cursor-pointer">
                            <X aria-hidden="true" className="w-5 h-5" />
                        </button>
                    )}
                </div>
            </div>

            <Section title="Domains">
                <div className="grid grid-cols-2 gap-1.5">
                    {domains.map(d => {
                        const on = filter.domains.includes(d);
                        return (
                            <button
                                key={d}
                                type="button"
                                aria-pressed={on}
                                onClick={() => set({ domains: toggle(filter.domains, d) })}
                                className="flex items-center gap-2 min-h-11 px-2 border text-sm font-semibold text-ivory cursor-pointer chamfer-sm"
                                style={{ background: on ? DOMAIN_BAND[d] : 'var(--color-raised)', borderColor: on ? DOMAIN_SIGIL[d] : 'var(--color-line)' }}
                            >
                                <DomainIcon domain={d} decorative className="w-6 h-6" />
                                <span className="truncate">{DOMAIN_LABEL[d]}</span>
                            </button>
                        );
                    })}
                </div>
            </Section>

            <Section title="Rarity">
                <div className="flex flex-wrap gap-1.5">
                    {RARITIES.map((r: RiftRarity) => (
                        <button key={r} type="button" aria-pressed={filter.rarities.includes(r)} onClick={() => set({ rarities: toggle(filter.rarities, r) })} className="chip">
                            <RarityGem rarity={r} />{RARITY_LABEL[r]}
                        </button>
                    ))}
                </div>
            </Section>

            <Section title="Type">
                <div className="flex flex-wrap gap-1.5">
                    {FILTERABLE_TYPES.map((t: CardType) => (
                        <button key={t} type="button" aria-pressed={filter.types.includes(t)} onClick={() => set({ types: toggle(filter.types, t) })} className="chip">
                            {TYPE_LABEL[t]}
                        </button>
                    ))}
                </div>
            </Section>

            {(['costs', 'mights'] as const).map(key => (
                <Section key={key} title={key === 'costs' ? 'Energy cost' : 'Might'}>
                    <div className="flex flex-wrap gap-1.5">
                        {STATS.map(n => {
                            const on = filter[key].includes(n);
                            return (
                                <button
                                    key={n}
                                    type="button"
                                    aria-pressed={on}
                                    aria-label={`${key === 'costs' ? 'Energy cost' : 'Might'} ${statLabel(n)}`}
                                    onClick={() => set({ [key]: toggle(filter[key], n) })}
                                    className={`w-9 h-9 rounded-full rift-number text-[15px] cursor-pointer ${on ? 'bg-ivory text-ink-text border-2 border-gold' : 'bg-raised text-ivory border border-line hover:border-line-strong'}`}
                                >
                                    {statLabel(n)}
                                </button>
                            );
                        })}
                    </div>
                </Section>
            ))}

            {sets.length > 0 && (
                <Section title="Sets">
                    <div className="flex flex-wrap gap-1.5">
                        {sets.map(s => (
                            <button key={s.code} type="button" aria-pressed={filter.sets.includes(s.code)} onClick={() => set({ sets: toggle(filter.sets, s.code) })} className="chip">
                                {s.name}
                            </button>
                        ))}
                    </div>
                </Section>
            )}

            <label className="flex items-center gap-2.5 text-sm text-parchment cursor-pointer">
                <input type="checkbox" checked={filter.hideBanned} onChange={e => set({ hideBanned: e.target.checked })} className="w-[18px] h-[18px] accent-gold" />
                Hide cards banned in Standard
            </label>
        </div>
    );
}
