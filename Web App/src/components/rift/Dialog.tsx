import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

/**
 * A modal panel. Escape or a click outside closes it, focus moves into it when it opens and
 * back to where it was when it closes. [footer] stays pinned below the scrolling body.
 */
export function Dialog({
    title, onClose, children, footer, wide = false,
}: { title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
    const panel = useRef<HTMLDivElement>(null);
    const titleId = useId();

    useEffect(() => {
        const previous = document.activeElement as HTMLElement | null;
        const first = panel.current?.querySelector<HTMLElement>('input, select, textarea, button:not([data-close])');
        (first ?? panel.current)?.focus();
        const onKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', onKey);
        document.body.style.overflow = 'hidden';
        return () => {
            window.removeEventListener('keydown', onKey);
            document.body.style.overflow = '';
            previous?.focus();
        };
    }, [onClose]);

    return (
        <div className="fixed inset-0 z-[100] flex items-end sm:items-center justify-center bg-black/70 p-0 sm:p-6" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
            <div
                ref={panel}
                role="dialog"
                aria-modal="true"
                aria-labelledby={titleId}
                tabIndex={-1}
                className={`w-full ${wide ? 'sm:max-w-2xl' : 'sm:max-w-md'} max-h-[92vh] flex flex-col panel shadow-2xl shadow-black/70 outline-none`}
            >
                <div className="flex items-center gap-3 px-5 pt-5 pb-3">
                    <h2 id={titleId} className="flex-1 rift-label text-[22px]">{title}</h2>
                    <button type="button" data-close aria-label="Close" onClick={onClose} className="w-10 h-10 flex items-center justify-center text-parchment hover:text-ivory cursor-pointer">
                        <X aria-hidden="true" className="w-5 h-5" />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto px-5 pb-5">{children}</div>
                {footer && <div className="px-5 py-4 border-t border-line">{footer}</div>}
            </div>
        </div>
    );
}
