import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { useDecks } from '../context/DeckContext';
import { useCards } from '../context/CardContext';
import type { Deck } from '../models/deck';
import type { Card } from '../models/card';
import { contentsOf, parseDecklist } from '../lib/decklist';
import { ISSUE_MESSAGES, RULE_BREAK_CODES, counts, validate } from '../rules/deckRules';
import { shownDomains } from '../lib/style';
import { DomainBand } from '../components/rift/DomainBand';
import { Dialog } from '../components/rift/Dialog';
import { DomainIcon, Loading, RiftLabel } from '../components/rift/Rift';

const newDeck = (name: string, fields: Partial<Deck> = {}): Deck => ({
    deckId: crypto.randomUUID(),
    name,
    format: 'CONSTRUCTED',
    legendCardId: null,
    championCardId: null,
    coverCardUrl: null,
    mainCount: 0,
    sideCount: 0,
    lastModified: Date.now(),
    ...fields,
});

function edited(at: number, now: number): string {
    const days = Math.floor((now - at) / 86_400_000);
    if (days <= 0) return 'Edited today';
    if (days === 1) return 'Edited yesterday';
    if (days < 7) return `Edited ${days} days ago`;
    if (days < 30) return `Edited ${Math.floor(days / 7)} wk ago`;
    return `Edited ${new Date(at).toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })}`;
}

/** Your decks as tiles, with a panel to paste in a decklist from elsewhere. */
const DeckList = () => {
    const { decks, loadingDecks, createDeck, createDeckWithEntries, deleteDeck } = useDecks();
    const { masterCards } = useCards();
    const navigate = useNavigate();
    const cardsById = useMemo(() => new Map(masterCards.map(c => [c.cardId, c])), [masterCards]);
    const [creating, setCreating] = useState(false);
    const [newName, setNewName] = useState('');
    const [deleting, setDeleting] = useState<Deck | null>(null);
    const [now] = useState(() => Date.now());

    const create = async () => {
        if (!newName.trim()) return;
        const deck = newDeck(newName.trim());
        await createDeck(deck);
        setCreating(false);
        navigate(`/decks/${deck.deckId}`);
    };

    if (loadingDecks) return <Loading label="Loading decks" />;

    return (
        <div className="w-full max-w-[1600px] mx-auto px-4 sm:px-8 pt-7 pb-16 flex flex-wrap items-start gap-7">
            <div className="flex-[999_1_560px] min-w-0 flex flex-col gap-5">
                <div className="flex flex-wrap items-center gap-3">
                    <h1 className="flex-1 rift-label text-[40px] leading-none tracking-[0.05em]">Decks</h1>
                    <button type="button" className="btn-gold" onClick={() => { setNewName(''); setCreating(true); }}>+ New deck</button>
                </div>

                {decks.length === 0 ? (
                    <div className="panel p-10 flex flex-col gap-2 items-center text-center">
                        <span className="rift-label text-lg">No decks yet</span>
                        <span className="text-muted max-w-md">Start one from scratch with New deck, or paste a list from elsewhere into the import panel.</span>
                    </div>
                ) : (
                    <div className="grid grid-cols-[repeat(auto-fill,minmax(290px,1fr))] gap-[18px]">
                        {decks.map(deck => {
                            const legend = deck.legendCardId ? cardsById.get(deck.legendCardId) : undefined;
                            const champion = deck.championCardId ? cardsById.get(deck.championCardId) : undefined;
                            const art = legend?.imageUrl ?? champion?.imageUrl ?? deck.coverCardUrl;
                            const complete = (deck.mainCount ?? 0) >= 40;
                            return (
                                <div key={deck.deckId} className="relative group panel flex flex-col hover:border-gold-deep">
                                    <button type="button" onClick={() => navigate(`/decks/${deck.deckId}`)} className="flex flex-col text-left cursor-pointer">
                                        <div className="relative h-44 overflow-hidden bg-raised">
                                            {art && <img src={art} alt="" loading="lazy" className="w-full h-full object-cover object-[50%_22%]" />}
                                            <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-b from-transparent from-30% to-ink/85" />
                                            {legend && (
                                                <span className="absolute right-2.5 top-2.5 flex gap-0.5">
                                                    {shownDomains(legend.domain).filter(d => d !== 'COLORLESS').map(d => <DomainIcon key={d} domain={d} decorative className="w-[26px] h-[26px]" />)}
                                                </span>
                                            )}
                                        </div>
                                        {legend ? (
                                            <DomainBand domains={legend.domain} className="px-3.5 py-2.5">
                                                <DeckTitle deck={deck} />
                                            </DomainBand>
                                        ) : (
                                            <div className="band-cut bg-raised px-3.5 py-2.5"><DeckTitle deck={deck} /></div>
                                        )}
                                        <div className="flex flex-wrap items-center gap-2 px-3.5 py-3">
                                            <span className={`h-6 inline-flex items-center px-2 rift-label text-[13px] tracking-[0.1em] text-white ${complete ? 'bg-[#285F24]' : 'bg-ink-muted'}`}>
                                                {complete ? 'Complete' : 'In progress'}
                                            </span>
                                            <span className="flex-1 text-sm text-muted">{legend ? legend.name : 'No legend yet'}</span>
                                            <span className="text-[13px] text-muted">{edited(deck.lastModified, now)}</span>
                                        </div>
                                    </button>
                                    <button
                                        type="button"
                                        aria-label={`Delete ${deck.name}`}
                                        onClick={() => setDeleting(deck)}
                                        className="absolute left-2.5 top-2.5 w-9 h-9 flex items-center justify-center bg-ink/80 border border-line text-parchment hover:text-white hover:bg-banned opacity-0 group-hover:opacity-100 focus:opacity-100 cursor-pointer"
                                    >
                                        <Trash2 aria-hidden="true" className="w-4 h-4" />
                                    </button>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            <ImportPanel
                cards={masterCards}
                cardsById={cardsById}
                onImport={async (deck, main, side) => {
                    await createDeckWithEntries(deck, main, side);
                    navigate(`/decks/${deck.deckId}`);
                }}
            />

            {creating && (
                <Dialog
                    title="New deck"
                    onClose={() => setCreating(false)}
                    footer={<button type="button" className="btn-gold w-full" disabled={!newName.trim()} onClick={create}>Create deck</button>}
                >
                    <form onSubmit={e => { e.preventDefault(); create(); }}>
                        <label className="flex flex-col gap-1.5 text-sm text-muted">
                            Deck name
                            <input type="text" value={newName} onChange={e => setNewName(e.target.value)} className="field h-11 text-base" />
                        </label>
                    </form>
                </Dialog>
            )}
            {deleting && (
                <Dialog
                    title={`Delete "${deleting.name}"?`}
                    onClose={() => setDeleting(null)}
                    footer={
                        <div className="flex justify-end gap-2">
                            <button type="button" className="btn-quiet" onClick={() => setDeleting(null)}>Cancel</button>
                            <button type="button" className="btn-quiet border-banned bg-banned text-white hover:bg-banned/80" onClick={() => { deleteDeck(deleting.deckId); setDeleting(null); }}>
                                Delete deck
                            </button>
                        </div>
                    }
                >
                    <p className="text-parchment">The deck and its list are deleted. Your collection isn't affected.</p>
                </Dialog>
            )}
        </div>
    );
};

function DeckTitle({ deck }: { deck: Deck }) {
    return (
        <span className="flex items-center gap-2.5">
            <span className="flex-1 font-bold text-[19px] text-white truncate">{deck.name}</span>
            <span className="rift-number text-base text-white">{deck.mainCount ?? 0} / 40</span>
        </span>
    );
}

/** Paste a decklist; each line is matched to a card as you type. Same parser as the Android app. */
function ImportPanel({ cards, cardsById, onImport }: {
    cards: Card[];
    cardsById: Map<string, Card>;
    onImport: (deck: Deck, main: Record<string, number>, side: Record<string, number>) => Promise<void>;
}) {
    const [text, setText] = useState('');
    const [name, setName] = useState('');
    const [busy, setBusy] = useState(false);
    const parsed = useMemo(() => (text.trim() ? parseDecklist(text, cards) : null), [text, cards]);
    const legend = parsed?.legendId ? cardsById.get(parsed.legendId) : undefined;
    const matched = parsed ? Object.values(parsed.main).reduce((a, n) => a + n, 0) + Object.values(parsed.side).reduce((a, n) => a + n, 0) : 0;
    const issues = parsed ? validate(contentsOf(parsed), cardsById).filter(i => RULE_BREAK_CODES.has(i.code)) : [];
    const suggestedName = legend ? `${legend.championTag ?? legend.name} deck` : 'Imported deck';

    const run = async () => {
        if (!parsed || matched === 0) return;
        setBusy(true);
        const c = counts(contentsOf(parsed), cardsById);
        const deck = newDeck(name.trim() || suggestedName, {
            legendCardId: parsed.legendId,
            championCardId: parsed.championId,
            coverCardUrl: legend?.imageUrl ?? null,
            mainCount: c.mainDeck,
            sideCount: c.sideboard,
        });
        try {
            await onImport(deck, parsed.main, parsed.side);
        } finally {
            setBusy(false);
        }
    };

    return (
        <aside aria-label="Import a decklist" className="flex-[1_1_300px] lg:max-w-[380px] panel p-5 flex flex-col gap-3.5">
            <RiftLabel className="text-lg">Import a decklist</RiftLabel>
            <p className="text-sm text-muted">Paste a list from a deck site, a tournament page or a friend. Bounded matches each line to a card as you paste.</p>
            <label className="flex flex-col gap-1.5 text-[13px] text-muted">
                Decklist
                <textarea
                    rows={11}
                    value={text}
                    onChange={e => setText(e.target.value)}
                    placeholder={'Legend:\n1 Master Yi - Wuju Master\n\nMainDeck:\n3 Defy\n…'}
                    className="field py-2.5 resize-y font-mono text-[13px] leading-normal"
                />
            </label>
            {parsed && (
                <div className="flex flex-col gap-1.5 p-3 bg-ink border border-line text-sm" aria-live="polite">
                    <span className="flex justify-between"><span className="text-parchment">Legend</span><span className={legend ? 'text-ivory' : 'text-warn'}>{legend?.name ?? 'Not found'}</span></span>
                    <span className="flex justify-between"><span className="text-parchment">Matched</span><span className="text-gold font-semibold">{matched} cards</span></span>
                    {parsed.unmatched.length > 0 && (
                        <>
                            <span className="flex justify-between"><span className="text-parchment">Not recognised</span><span className="text-warn font-semibold">{parsed.unmatched.length} {parsed.unmatched.length === 1 ? 'line' : 'lines'}</span></span>
                            <ul className="text-[13px] text-muted list-disc pl-5">
                                {parsed.unmatched.slice(0, 5).map((l, i) => <li key={i} className="truncate">{l}</li>)}
                                {parsed.unmatched.length > 5 && <li>and {parsed.unmatched.length - 5} more</li>}
                            </ul>
                        </>
                    )}
                    {issues.length > 0 && (
                        <ul className="text-[13px] text-banned-2v2 list-disc pl-5">
                            {issues.slice(0, 4).map(i => (
                                <li key={`${i.code}:${i.subject ?? ''}`}>{ISSUE_MESSAGES[i.code]}{i.subject ? ` (${cardsById.get(i.subject)?.name ?? i.subject})` : ''}</li>
                            ))}
                        </ul>
                    )}
                </div>
            )}
            <label className="flex flex-col gap-1.5 text-[13px] text-muted">
                Deck name
                <input type="text" value={name} placeholder={suggestedName} onChange={e => setName(e.target.value)} className="field h-10 text-[15px]" />
            </label>
            <button type="button" className="btn-gold" disabled={!parsed || matched === 0 || busy} onClick={run}>
                {matched ? `Import ${matched} cards` : 'Import'}
            </button>
        </aside>
    );
}

export default DeckList;
