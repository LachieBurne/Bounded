import { useMemo, useState } from 'react';
import { Navigate, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Pencil, SlidersHorizontal, Trash2 } from 'lucide-react';
import { useCards } from '../context/CardContext';
import { useBinders } from '../context/BinderContext';
import { usePrices } from '../context/PriceContext';
import type { CardWithQuantity, Domain } from '../models/card';
import { EMPTY_FILTER, OWNERSHIP_LABELS, SORT_LABELS, activeCount, matches, setsFrom, sortCards, toggle, type CardFilter, type CardSort, type Ownership } from '../lib/cardFilter';
import { inBinder, newBinder, percent, progressOf, type Binder } from '../lib/binder';
import { DOMAIN_BAND, DOMAIN_LABEL, DOMAIN_SIGIL, PLAYABLE_DOMAINS } from '../lib/style';
import { CardTile } from '../components/collection/CardTile';
import { FilterPanel } from '../components/collection/FilterPanel';
import { BinderEditor } from '../components/collection/BinderEditor';
import { Dialog } from '../components/rift/Dialog';
import { DomainIcon, GoldProgress, Loading, RiftLabel, Segmented, SetCodeBadge } from '../components/rift/Rift';

const wideScreen = () => typeof window !== 'undefined' && window.matchMedia('(min-width: 1280px)').matches;

/**
 * The collection: All cards by default, with sub-binders in the side panel. Quantities always
 * come from the collection, so a change in one binder shows everywhere. Mirrors the Android
 * app's Collection tab.
 */
export default function CollectionPage() {
    const { binderId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const [params] = useSearchParams();
    const { binderCards, masterCards, loading, updateCardQuantity } = useCards();
    const { binders, loaded, saveBinder, deleteBinder } = useBinders();
    const { prices, formatPrice } = usePrices();

    const [filter, setFilter] = useState<Omit<CardFilter, 'query'>>(EMPTY_FILTER);
    const [sort, setSort] = useState<CardSort>('SET');
    const [filtersOpen, setFiltersOpen] = useState(wideScreen);
    const [editing, setEditing] = useState<{ binder: Binder | null } | null>(null);
    const [confirmDelete, setConfirmDelete] = useState(false);

    const query = params.get('q') ?? '';
    const fullFilter: CardFilter = { ...filter, query };
    const binder = binderId ? binders.find(b => b.binderId === binderId) ?? null : null;
    const sets = useMemo(() => setsFrom(masterCards), [masterCards]);
    const setName = (code: string) => sets.find(s => s.code === code)?.name ?? code;

    const inScope = useMemo(() => (c: CardWithQuantity['card']) => inBinder(binder, c), [binder]);
    const progress = useMemo(() => progressOf(binderCards, inScope, prices), [binderCards, inScope, prices]);
    const domainProgress = useMemo(() => PLAYABLE_DOMAINS
        .map(d => ({ domain: d, progress: progressOf(binderCards, c => inScope(c) && c.domain.includes(d), prices) }))
        .filter(x => x.progress.total > 0), [binderCards, inScope, prices]);
    const binderProgress = useMemo(() => new Map(binders.map(b => [b.binderId, progressOf(binderCards, c => inBinder(b, c), prices)])), [binders, binderCards, prices]);
    const allProgress = useMemo(() => progressOf(binderCards, () => true, prices), [binderCards, prices]);

    const shown = useMemo(() => sortCards(
        binderCards.filter(i => inScope(i.card) && matches(fullFilter, i.card, i.quantity)),
        sort,
        id => prices[id],
        // fullFilter is rebuilt each render from filter and query.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    ), [binderCards, inScope, filter, query, sort, prices]);

    const sections = useMemo(() => {
        if (sort !== 'SET') return null;
        const bySet = new Map<string, CardWithQuantity[]>();
        shown.forEach(i => bySet.set(i.card.setCode, [...(bySet.get(i.card.setCode) ?? []), i]));
        return bySet.size > 1 ? [...bySet].map(([code, cards]) => ({ code, cards })) : null;
    }, [shown, sort]);

    const coveredSets = new Set(binders.filter(b => b.sets.length === 1).map(b => b.sets[0]));
    const suggestedSets = sets.filter(s => !coveredSets.has(s.code));

    if (binderId && loaded && !binder) return <Navigate to="/" replace />;
    if (loading || (binderId && !loaded)) return <Loading label="Syncing collection" />;

    const openCard = (cardId: string) => navigate(`/cards/${cardId}`, {
        state: { background: location, ids: shown.map(i => i.card.cardId) },
    });
    const save = async (b: Binder) => {
        setEditing(null);
        await saveBinder(b);
        navigate(`/binders/${b.binderId}`);
    };
    const quickMasterSet = (s: { code: string; name: string }) => save(newBinder({ name: `${s.name} master set`, sets: [s.code] }));
    const setDomains = (domains: Domain[]) => setFilter(f => ({ ...f, domains }));

    const renderGrid = (cards: CardWithQuantity[]) => (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] sm:grid-cols-[repeat(auto-fill,minmax(150px,1fr))] gap-x-3.5 gap-y-4">
            {cards.map(i => (
                <CardTile key={i.card.cardId} item={i} onOpen={() => openCard(i.card.cardId)} onQuantity={n => updateCardQuantity(i.card.cardId, Math.max(0, n))} />
            ))}
        </div>
    );

    const binderButton = (id: string | null, name: string, p: typeof progress, scope: string, code?: string) => {
        const current = (binder?.binderId ?? null) === id;
        return (
            <button
                key={id ?? 'all'}
                type="button"
                aria-current={current ? 'page' : undefined}
                onClick={() => navigate(id ? `/binders/${id}${location.search}` : `/${location.search}`)}
                className={`min-w-[220px] lg:min-w-0 flex flex-col gap-2 p-3 border text-left cursor-pointer ${current ? 'bg-gold-wash border-gold-deep' : 'bg-surface border-line hover:border-line-strong'}`}
            >
                <span className="flex items-center gap-2 w-full">
                    {code && <SetCodeBadge code={code} />}
                    <span className="flex-1 font-semibold text-[15px] truncate">{name}</span>
                    <span className="rift-number text-[15px] text-gold">{percent(p)}%</span>
                </span>
                <span className="w-full h-[3px] bg-track"><span className="block h-full bg-gold" style={{ width: `${percent(p)}%` }} /></span>
                <span className="text-[13px] text-muted">{p.owned} of {p.total} · {scope}</span>
            </button>
        );
    };

    const scopeOf = (b: Binder) => [
        b.sets.length ? b.sets.map(setName).join(', ') : 'every set',
        ...(b.domains.length ? [b.domains.map(d => DOMAIN_LABEL[d as Domain] ?? d).join(', ')] : []),
        ...(b.includeShowcase ? [] : ['no showcase']),
    ].join(' · ');

    return (
        <div className="w-full max-w-[1600px] mx-auto px-4 sm:px-8 pt-6 pb-16 flex flex-wrap lg:flex-nowrap items-start gap-6">
            <aside aria-label="Binders" className="w-full lg:w-[260px] lg:shrink-0 flex flex-col gap-2 lg:sticky lg:top-24">
                <RiftLabel className="text-sm text-muted tracking-[0.14em]">Binders</RiftLabel>
                <div className="flex lg:flex-col gap-2 overflow-x-auto lg:overflow-visible pb-1 lg:pb-0">
                    {binderButton(null, 'All cards', allProgress, 'every set')}
                    {binders.map(b => binderButton(b.binderId, b.name, binderProgress.get(b.binderId) ?? progress, scopeOf(b), b.sets.length === 1 ? b.sets[0] : undefined))}
                    <button type="button" onClick={() => setEditing({ binder: null })} className="min-w-[160px] lg:min-w-0 min-h-11 border border-dashed border-line-strong text-gold rift-number text-base tracking-[0.08em] uppercase hover:bg-gold-wash cursor-pointer">
                        + New binder
                    </button>
                </div>
                {suggestedSets.length > 0 && (
                    <div className="hidden lg:flex flex-col gap-2 mt-3 pt-4 border-t border-line">
                        <span className="text-[13px] text-muted">Master set in one tap</span>
                        <div className="flex flex-wrap gap-1.5">
                            {suggestedSets.map(s => (
                                <button key={s.code} type="button" onClick={() => quickMasterSet(s)} className="flex items-center gap-1.5 min-h-8 pl-1 pr-2.5 border border-line bg-surface text-[13px] text-parchment hover:border-gold-deep cursor-pointer">
                                    <SetCodeBadge code={s.code} className="h-[22px] text-xs" />{s.name}
                                </button>
                            ))}
                        </div>
                    </div>
                )}
            </aside>

            <div className="flex-1 min-w-0 flex flex-col gap-4">
                <div className="flex flex-wrap items-center gap-3">
                    <h1 className="flex-1 rift-label text-[34px] sm:text-[40px] leading-none tracking-[0.05em]">{binder?.name ?? 'All cards'}</h1>
                    {binder && (
                        <div className="flex gap-2">
                            <button type="button" onClick={() => setEditing({ binder })} className="btn-quiet"><Pencil aria-hidden="true" className="w-4 h-4" />Edit binder</button>
                            <button type="button" onClick={() => setConfirmDelete(true)} className="btn-quiet"><Trash2 aria-hidden="true" className="w-4 h-4" />Delete</button>
                        </div>
                    )}
                </div>

                <section aria-label="Progress" className="flex flex-wrap items-center gap-x-8 gap-y-5 p-5 border border-line bg-gradient-to-b from-[#1C1612] to-bar">
                    <div className="flex-[1_1_280px] flex flex-col gap-2.5">
                        <div className="flex items-baseline gap-2.5">
                            <span className="rift-number text-[44px]">{progress.owned}</span>
                            <span className="flex-1 text-muted">of {progress.total} owned{progress.value > 0 ? ` · worth ${formatPrice(progress.value)}` : ''}</span>
                            <span className="rift-number text-2xl text-gold">{percent(progress)}%</span>
                        </div>
                        <GoldProgress fraction={progress.total ? progress.owned / progress.total : 0} />
                    </div>
                    <div role="group" aria-label="Show one domain" className="flex flex-wrap gap-1.5">
                        {domainProgress.map(({ domain, progress: p }) => {
                            const on = filter.domains.includes(domain);
                            return (
                                <button
                                    key={domain}
                                    type="button"
                                    aria-pressed={on}
                                    onClick={() => setDomains(toggle(filter.domains, domain))}
                                    className="w-[76px] flex flex-col items-center gap-1 pt-2 pb-1.5 border cursor-pointer"
                                    style={{ background: on ? DOMAIN_BAND[domain] : 'var(--color-surface)', borderColor: on ? DOMAIN_SIGIL[domain] : 'var(--color-line)' }}
                                >
                                    <DomainIcon domain={domain} decorative className="w-[26px] h-[26px]" />
                                    <span className="rift-number text-base">{percent(p)}%</span>
                                    <span className="text-[11px] text-parchment">{DOMAIN_LABEL[domain]}</span>
                                </button>
                            );
                        })}
                    </div>
                </section>

                <div className="flex flex-wrap items-center gap-2.5">
                    <button
                        type="button"
                        aria-expanded={filtersOpen}
                        onClick={() => setFiltersOpen(o => !o)}
                        className={`btn-outline min-h-10 ${filtersOpen ? 'bg-gold-wash' : ''}`}
                    >
                        <SlidersHorizontal aria-hidden="true" className="w-4 h-4" />Filters
                        {activeCount(fullFilter) > 0 && <span className="min-w-5 h-5 px-1 flex items-center justify-center bg-gold text-on-gold font-sans text-xs not-italic">{activeCount(fullFilter)}</span>}
                    </button>
                    <Segmented<Ownership>
                        label="Ownership"
                        value={filter.ownership}
                        onChange={ownership => setFilter(f => ({ ...f, ownership }))}
                        options={(Object.keys(OWNERSHIP_LABELS) as Ownership[]).map(o => ({ value: o, label: OWNERSHIP_LABELS[o] }))}
                    />
                    <label className="flex items-center gap-2 min-h-10 px-3 border border-line text-sm text-muted">
                        Sort
                        <select value={sort} onChange={e => setSort(e.target.value as CardSort)} className="bg-transparent text-ivory outline-none cursor-pointer">
                            {(Object.keys(SORT_LABELS) as CardSort[]).map(s => <option key={s} value={s} className="bg-surface">{SORT_LABELS[s]}</option>)}
                        </select>
                    </label>
                    <span className="ml-auto text-sm text-muted" aria-live="polite">{shown.length} {shown.length === 1 ? 'card' : 'cards'}</span>
                </div>

                {shown.length === 0 ? (
                    <div className="panel p-10 text-center flex flex-col gap-2">
                        <span className="rift-label text-lg">No cards match</span>
                        <span className="text-muted">Try a different search, or clear some filters.</span>
                    </div>
                ) : sections ? (
                    sections.map(s => (
                        <section key={s.code} className="flex flex-col gap-3 [content-visibility:auto]">
                            <div className="flex items-center gap-3 pt-2">
                                <SetCodeBadge code={s.code} />
                                <h2 className="rift-label text-xl tracking-[0.1em]">{setName(s.code)}</h2>
                                <span className="flex-1 h-px bg-line" />
                                <span className="text-sm text-muted">{s.cards.length} shown</span>
                            </div>
                            {renderGrid(s.cards)}
                        </section>
                    ))
                ) : renderGrid(shown)}
            </div>

            {filtersOpen && (
                <div className="fixed inset-0 z-40 bg-black/60 xl:static xl:z-auto xl:bg-transparent xl:w-[290px] xl:shrink-0" onMouseDown={e => { if (e.target === e.currentTarget) setFiltersOpen(false); }}>
                    <aside aria-label="Filters" className="absolute right-0 top-0 h-full w-[min(320px,90vw)] overflow-y-auto panel xl:static xl:h-auto xl:w-full xl:sticky xl:top-24 xl:max-h-[calc(100vh-7rem)]">
                        <FilterPanel
                            filter={fullFilter}
                            onChange={({ query: _q, ...rest }) => { void _q; setFilter(rest); }}
                            sets={sets}
                            onClose={wideScreen() ? undefined : () => setFiltersOpen(false)}
                        />
                    </aside>
                </div>
            )}

            {editing && (
                <BinderEditor
                    initial={editing.binder}
                    cards={masterCards}
                    sets={sets}
                    suggestedSets={suggestedSets}
                    onSave={save}
                    onQuickMasterSet={quickMasterSet}
                    onClose={() => setEditing(null)}
                />
            )}
            {confirmDelete && binder && (
                <Dialog
                    title={`Delete "${binder.name}"?`}
                    onClose={() => setConfirmDelete(false)}
                    footer={
                        <div className="flex justify-end gap-2">
                            <button type="button" className="btn-quiet" onClick={() => setConfirmDelete(false)}>Cancel</button>
                            <button
                                type="button"
                                className="btn-quiet border-banned text-white bg-banned hover:bg-banned/80"
                                onClick={async () => { setConfirmDelete(false); navigate('/'); await deleteBinder(binder.binderId); }}
                            >
                                Delete binder
                            </button>
                        </div>
                    }
                >
                    <p className="text-parchment">Only the binder is deleted. Your cards stay in your collection.</p>
                </Dialog>
            )}
        </div>
    );
}
