import type { ReactNode } from 'react';
import type { Domain } from '../../models/card';
import { DOMAIN_BAND, KEYWORD_COLOURS } from '../../lib/style';

const TOKEN = /\[([^\]]+)]|\(([^)]*)\)/g;
const DOMAINS: Domain[] = ['FURY', 'CALM', 'MIND', 'BODY', 'CHAOS', 'ORDER'];

/**
 * A card's rules text with its markers drawn: keywords as chips, energy costs as dark tokens,
 * runes in their domain's colour and reminder text in italics. The same rules as the Android
 * app's rulesText().
 */
export function RulesText({ text }: { text: string }) {
    return <>{render(text, 'r')}</>;
}

function render(raw: string, keyPrefix: string): ReactNode[] {
    const out: ReactNode[] = [];
    let last = 0;
    let i = 0;
    for (const match of raw.matchAll(TOKEN)) {
        const at = match.index ?? 0;
        const key = `${keyPrefix}-${i++}`;
        if (at > last) out.push(...withBreaks(raw.slice(last, at), key));
        const marker = match[1];
        const reminder = match[2];
        if (reminder !== undefined) {
            out.push(<span key={key} className="italic text-ink-muted">({render(reminder, key)})</span>);
        } else if (marker === '>' || marker === '>>') {
            out.push('→');
        } else if (marker === 'Might' || marker === 'Exhaust') {
            out.push(<strong key={key}>{marker}</strong>);
        } else if (marker === 'Rune Rainbow') {
            out.push(<strong key={key}>any rune</strong>);
        } else if (marker.startsWith('Rune ')) {
            const name = marker.slice(5);
            const domain = DOMAINS.find(d => d === name.toUpperCase());
            out.push(<strong key={key} style={{ color: domain ? DOMAIN_BAND[domain] : undefined }}>{name} rune</strong>);
        } else if (marker.startsWith('Energy ')) {
            out.push(
                <span key={key} className="inline-flex items-center justify-center min-w-[21px] h-[21px] px-1 mx-0.5 align-[-3px] rounded-full bg-ink-text text-ivory rift-number text-sm">
                    {marker.slice(7)}
                </span>,
            );
        } else {
            const word = marker.split(' ')[0];
            out.push(
                <span key={key} className="inline-block px-1.5 mx-0.5 rift-label text-[15px] tracking-[0.06em] text-white" style={{ background: KEYWORD_COLOURS[word] ?? '#5E5A55' }}>
                    {marker}
                </span>,
            );
        }
        last = at + match[0].length;
    }
    if (last < raw.length) out.push(...withBreaks(raw.slice(last), `${keyPrefix}-end`));
    return out;
}

/** Plain text, keeping the card's line breaks. */
function withBreaks(text: string, key: string): ReactNode[] {
    return text.split('\n').flatMap((part, i) => (i ? [<br key={`${key}-br${i}`} />, part] : [part]));
}
