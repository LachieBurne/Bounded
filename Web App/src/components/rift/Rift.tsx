import type { ReactNode } from 'react';
import { Ban, Minus, Plus } from 'lucide-react';
import type { Card, Domain, RiftRarity } from '../../models/card';
import { DOMAIN_LABEL, RARITY_GEM, RARITY_LABEL, domainIconUrl } from '../../lib/style';

/* Small Gilded Rift building blocks, matching the Android app's RiftComponents.kt. */

/** The gold diamond that marks Bounded and the current page. */
export function Diamond({ className = 'w-3 h-3 bg-gold' }: { className?: string }) {
    return <span aria-hidden="true" className={`inline-block shrink-0 rotate-45 ${className}`} />;
}

export function RiftLabel({ children, className = '' }: { children: ReactNode; className?: string }) {
    return <span className={`rift-label ${className}`}>{children}</span>;
}

/** A set code in a gold-edged box, e.g. OGN. */
export function SetCodeBadge({ code, className = '' }: { code: string; className?: string }) {
    return (
        <span className={`inline-flex items-center h-6 px-1.5 bg-ink border border-gold-deep rift-number text-[13px] tracking-[0.06em] text-gold ${className}`}>
            {code}
        </span>
    );
}

/**
 * A red "BANNED" tag for cards banned in Standard, or an amber "2V2 BAN" for cards banned only
 * in 2v2. Draws nothing for legal cards.
 */
export function BannedTag({ card, className = '' }: { card: Card; className?: string }) {
    const standard = card.bannedIn?.includes('STANDARD');
    const twoVTwo = card.bannedIn?.includes('2V2');
    if (!standard && !twoVTwo) return null;
    return (
        <span className={`inline-flex items-center gap-1 h-5 px-1.5 text-white rift-label text-[11px] tracking-[0.1em] ${standard ? 'bg-banned' : 'bg-banned-2v2'} ${className}`}>
            <Ban aria-hidden="true" className="w-3 h-3" strokeWidth={3} />
            {standard ? 'Banned' : '2v2 ban'}
        </span>
    );
}

/** Progress in gold, with a diamond marking where you are. */
export function GoldProgress({ fraction, className = '' }: { fraction: number; className?: string }) {
    const pct = `${Math.round(Math.min(1, Math.max(0, fraction)) * 100)}%`;
    return (
        <div className={`relative h-1.5 bg-track ${className}`}>
            <div className="h-full bg-gold" style={{ width: pct }} />
            <span
                aria-hidden="true"
                className="absolute -top-[3px] w-3 h-3 rotate-45 bg-ivory shadow-[0_0_0_2px_var(--color-bar)]"
                style={{ left: `calc(${pct} - 6px)` }}
            />
        </div>
    );
}

export function DomainIcon({ domain, className = 'w-6 h-6', decorative = false }: { domain: Domain; className?: string; decorative?: boolean }) {
    return <img src={domainIconUrl(domain)} alt={decorative ? '' : DOMAIN_LABEL[domain]} className={className} loading="lazy" />;
}

export function RarityGem({ rarity, className = 'w-2.5 h-2.5' }: { rarity: RiftRarity; className?: string }) {
    return <span aria-hidden="true" title={RARITY_LABEL[rarity]} className={`inline-block shrink-0 rotate-45 ${className}`} style={{ background: RARITY_GEM[rarity] }} />;
}

/** The energy cost as an ivory token, the way the card prints it. */
export function EnergyToken({ value, className = 'w-10 h-10 text-[22px]' }: { value: number; className?: string }) {
    return (
        <span title="Energy cost" className={`inline-flex items-center justify-center shrink-0 rounded-full bg-ivory border-2 border-gold text-ink-text rift-number ${className}`}>
            {value}
        </span>
    );
}

/** − count +, with the add button in gold. */
export function QuantityStepper({
    value, onChange, label, size = 'md', min = 0,
}: { value: number; onChange: (next: number) => void; label: string; size?: 'sm' | 'md' | 'lg'; min?: number }) {
    const box = { sm: 'w-9 h-9', md: 'w-11 h-11', lg: 'w-12 h-12' }[size];
    const num = { sm: 'w-7 text-lg', md: 'w-10 text-2xl', lg: 'w-14 text-[32px]' }[size];
    return (
        <div className="flex items-center gap-1">
            <button
                type="button"
                aria-label={`Remove a copy of ${label}`}
                disabled={value <= min}
                onClick={() => onChange(value - 1)}
                className={`${box} flex items-center justify-center bg-raised text-ivory hover:bg-line disabled:opacity-35 disabled:cursor-not-allowed cursor-pointer`}
            >
                <Minus aria-hidden="true" className="w-4 h-4" />
            </button>
            <span aria-live="polite" className={`${num} text-center rift-number text-ivory`}>{value}</span>
            <button
                type="button"
                aria-label={`Add a copy of ${label}`}
                onClick={() => onChange(value + 1)}
                className={`${box} flex items-center justify-center bg-gold text-on-gold hover:bg-gold-bright cursor-pointer`}
            >
                <Plus aria-hidden="true" className="w-4 h-4" />
            </button>
        </div>
    );
}

/** A row of mutually exclusive buttons, e.g. All / Owned / Missing. */
export function Segmented<T extends string>({
    options, value, onChange, label,
}: { options: { value: T; label: string }[]; value: T; onChange: (v: T) => void; label: string }) {
    return (
        <div role="group" aria-label={label} className="flex border border-line">
            {options.map((o, i) => (
                <button
                    key={o.value}
                    type="button"
                    aria-pressed={o.value === value}
                    onClick={() => onChange(o.value)}
                    className={`min-h-10 px-3.5 text-sm font-semibold cursor-pointer ${i ? 'border-l border-line' : ''} ${o.value === value ? 'bg-gold-wash text-gold' : 'text-parchment hover:text-ivory'}`}
                >
                    {o.label}
                </button>
            ))}
        </div>
    );
}

/** A centred spinner while data loads. */
export function Loading({ label }: { label: string }) {
    return (
        <div role="status" className="flex-1 flex flex-col items-center justify-center gap-4 py-24 text-muted">
            <Diamond className="w-4 h-4 bg-gold animate-pulse" />
            <span className="rift-label text-sm">{label}</span>
        </div>
    );
}
