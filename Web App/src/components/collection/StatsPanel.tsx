import type { ReactNode } from 'react';
import type { Progress } from '../../lib/binder';
import { percent } from '../../lib/binder';
import { FINISH_LABEL } from '../../lib/finishes';
import { PLAYSET, type BinderStats } from '../../lib/stats';
import { RARITY_LABEL, TYPE_LABEL } from '../../lib/style';
import { GoldProgress, RarityGem, RiftLabel, SetCodeBadge } from '../rift/Rift';

/**
 * A binder broken down: headline tiles, then owned-of-total rows by rarity, type and set, foil
 * and promo counts, and the most valuable cards you own. Bars are one gold hue; the label (and
 * the rarity gem's shape) says what each row is, and every row prints its numbers. Mirrors the
 * Android app's stats sheet.
 */
export function StatsPanel({ stats, setName, formatPrice, onOpenCard }: {
    stats: BinderStats;
    setName: (code: string) => string;
    formatPrice: (usd: number) => string;
    onOpenCard: (cardId: string) => void;
}) {
    const p = stats.progress;
    return (
        <div className="flex flex-col gap-7">
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                <Tile label="Cards owned" value={p.owned.toLocaleString()} detail={`of ${p.total.toLocaleString()} · ${percent(p)}%`} />
                <Tile label="Copies" value={p.copies.toLocaleString()} detail="including duplicates" />
                <Tile label="Value" value={p.value > 0 ? formatPrice(p.value) : '–'} detail="TCGplayer market" />
                <Tile label="Playsets" value={stats.playsets.owned.toLocaleString()} detail={`of ${stats.playsets.total.toLocaleString()} units, spells and gear with ${PLAYSET}+`} />
            </div>

            <div className="grid gap-7 lg:grid-cols-2">
                <Section title="By rarity">
                    {stats.byRarity.map(r => <Row key={r.rarity} label={RARITY_LABEL[r.rarity]} progress={r.progress} leading={<RarityGem rarity={r.rarity} />} />)}
                </Section>
                <Section title="By type">
                    {stats.byType.map(r => <Row key={r.type} label={TYPE_LABEL[r.type]} progress={r.progress} />)}
                </Section>
                {stats.bySet.length > 0 && (
                    <Section title="By set">
                        {stats.bySet.map(r => <Row key={r.setCode} label={setName(r.setCode)} progress={r.progress} leading={<SetCodeBadge code={r.setCode} className="h-[22px] text-xs" />} />)}
                    </Section>
                )}
                <div className="flex flex-col gap-7">
                    <Section title="Foils and promos">
                        {stats.finishes.length === 0 ? (
                            <p className="text-sm text-muted">None yet. Mark copies as foil or promo from a card's detail.</p>
                        ) : (
                            <div className="flex flex-wrap gap-2">
                                {stats.finishes.map(f => (
                                    <span key={f.finish} className="flex items-center gap-2.5 px-3 py-1.5 bg-raised border border-line">
                                        <span className="text-sm text-parchment">{FINISH_LABEL[f.finish]}</span>
                                        <span className="rift-number text-base">{f.count.toLocaleString()}</span>
                                    </span>
                                ))}
                            </div>
                        )}
                    </Section>
                    {stats.mostValuable.length > 0 && (
                        <Section title="Most valuable">
                            <ol className="flex flex-col">
                                {stats.mostValuable.map(({ item, price }, i) => (
                                    <li key={item.card.cardId}>
                                        <button type="button" onClick={() => onOpenCard(item.card.cardId)} className="w-full min-h-11 flex items-center gap-3 text-left border-b border-line hover:bg-raised cursor-pointer">
                                            <span className="w-4 rift-number text-muted">{i + 1}</span>
                                            <span className="flex-1 min-w-0 truncate">{item.card.name} <span className="text-sm text-muted">{item.card.cardId}</span></span>
                                            {item.quantity > 1 && <span className="text-sm text-muted">×{item.quantity}</span>}
                                            <span className="rift-number text-gold">{formatPrice(price)}</span>
                                        </button>
                                    </li>
                                ))}
                            </ol>
                        </Section>
                    )}
                </div>
            </div>
        </div>
    );
}

function Tile({ label, value, detail }: { label: string; value: string; detail: string }) {
    return (
        <div className="flex flex-col gap-1 p-4 bg-raised border border-line">
            <RiftLabel className="text-[13px] text-muted">{label}</RiftLabel>
            <span className="rift-number text-[30px] leading-tight truncate">{value}</span>
            <span className="text-[13px] text-muted">{detail}</span>
        </div>
    );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
    return (
        <section className="flex flex-col gap-3">
            <RiftLabel className="text-[15px] text-gold">{title}</RiftLabel>
            {children}
        </section>
    );
}

/** One category: its label, owned of total, percent, and a thin gold bar. */
function Row({ label, progress, leading }: { label: string; progress: Progress; leading?: ReactNode }) {
    return (
        <div className="flex flex-col gap-1.5" aria-label={`${label}: ${progress.owned} of ${progress.total} owned, ${percent(progress)}%`} role="group">
            <div className="flex items-center gap-2.5">
                {leading}
                <span className="flex-1 min-w-0 truncate">{label}</span>
                <span className="text-sm text-muted">{progress.owned.toLocaleString()} of {progress.total.toLocaleString()}</span>
                <span className="w-11 text-right rift-number">{percent(progress)}%</span>
            </div>
            <GoldProgress fraction={progress.total ? progress.owned / progress.total : 0} />
        </div>
    );
}
