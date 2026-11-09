import { useEffect, useRef, type ReactNode } from 'react';
import type { Domain } from '../../models/card';
import { DOMAIN_BAND, shownDomains } from '../../lib/style';

/**
 * The band behind a name: one domain's band colour, or for two domains the first giving way to
 * the second on a "/" slant, its edge sprayed back over the first as grain, the way a
 * dual-domain card prints its banner. The same drawing as the Android app's Modifier.domainBand.
 */
export function DomainBand({ domains, className = '', children }: { domains: Domain[]; className?: string; children: ReactNode }) {
    const [a, b] = shownDomains(domains);
    return (
        <div className={`band-cut relative overflow-hidden ${className}`} style={{ background: DOMAIN_BAND[b ?? a] }}>
            {b && <BandCanvas first={DOMAIN_BAND[a]} second={DOMAIN_BAND[b]} />}
            <div className="relative">{children}</div>
        </div>
    );
}

/** A small seeded random generator, so the grain is the same on every render. */
function seeded(seed: number) {
    let s = seed >>> 0;
    return () => {
        s = (s + 0x6D2B79F5) >>> 0;
        let t = s;
        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

const hash = (s: string) => [...s].reduce((h, c) => (Math.imul(h, 31) + c.charCodeAt(0)) | 0, 7);

function BandCanvas({ first, second }: { first: string; second: string }) {
    const ref = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = ref.current;
        if (!canvas) return;
        const draw = () => {
            const dpr = window.devicePixelRatio || 1;
            const w = canvas.clientWidth;
            const h = canvas.clientHeight;
            if (!w || !h) return;
            canvas.width = Math.round(w * dpr);
            canvas.height = Math.round(h * dpr);
            const ctx = canvas.getContext('2d');
            if (!ctx) return;
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

            // The second colour takes over on a "/" slant a little left of centre.
            const lean = h * 0.8;
            const cx = w * 0.45;
            const cy = h / 2;
            const len = Math.hypot(h, lean);
            const nx = h / len;
            const ny = lean / len;
            const soft = 1.5;
            const edge = ctx.createLinearGradient(cx - nx * soft, cy - ny * soft, cx + nx * soft, cy + ny * soft);
            edge.addColorStop(0, first);
            edge.addColorStop(1, second);
            ctx.fillStyle = edge;
            ctx.fillRect(0, 0, w, h);

            // Its edge is sprayed back over the first colour as grain, with a few flecks the other way.
            const dot = 1;
            const sprayIn = 40;
            const sprayOut = 8;
            const random = seeded(hash(first + second));
            const left = cx - lean / 2 - sprayIn / nx;
            const right = cx + lean / 2 + sprayOut / nx;
            const count = Math.min(40000, Math.floor(((right - left) * h / (dot * dot)) * 1.6));
            for (let i = 0; i < count; i++) {
                const px = left + random() * (right - left);
                const py = random() * h;
                const across = (px - cx) * nx + (py - cy) * ny;
                const r = random();
                if (across < 0) {
                    const near = 1 + across / sprayIn;
                    if (near > 0 && r < Math.pow(near, 1.6)) { ctx.fillStyle = second; ctx.fillRect(px, py, dot, dot); }
                } else {
                    const near = 1 - across / sprayOut;
                    if (near > 0 && r < 0.35 * near * near) { ctx.fillStyle = first; ctx.fillRect(px, py, dot, dot); }
                }
            }
        };
        draw();
        const observer = new ResizeObserver(draw);
        observer.observe(canvas);
        return () => observer.disconnect();
    }, [first, second]);

    return <canvas ref={ref} aria-hidden="true" className="absolute inset-0 w-full h-full" />;
}
