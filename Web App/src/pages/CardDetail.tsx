import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams, type Location } from 'react-router-dom';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { useCards } from '../context/CardContext';
import { usePrices } from '../context/PriceContext';
import type { Card } from '../models/card';
import { RARITY_LABEL, TYPE_LABEL, shownDomains } from '../lib/style';
import { banDate, isBanned, upcomingBan } from '../lib/bans';
import { FINISH_LABEL, countOf, finishesOf, isPromo, type Finish } from '../lib/finishes';
import { DomainBand } from '../components/rift/DomainBand';
import { RulesText } from '../components/rift/RulesText';
import { LocationPanel } from '../components/collection/LocationPanel';
import { BannedTag, DomainIcon, EnergyToken, Loading, QuantityStepper, RarityGem, RiftLabel } from '../components/rift/Rift';

interface DetailState {
    /** The page underneath when the card was opened from a grid; the detail then shows on top. */
    background?: Location;
    /** The grid's cards in order, for previous and next. */
    ids?: string[];
}

const isTyping = (target: EventTarget | null) =>
    target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));

function typeLine(card: Card): string {
    const type = (card.isSignature ? 'Signature ' : '') + TYPE_LABEL[card.type].toLowerCase();
    const extras = card.type === 'LEGEND' ? (card.championTag ? [card.championTag] : []) : card.tags ?? [];
    return [type.charAt(0).toUpperCase() + type.slice(1), ...extras].join(' · ');
}

function banSentence(card: Card): string | null {
    const listed = (format: 'STANDARD' | '2V2') => card.bannedIn?.includes(format) ?? false;
    const formats = listed('STANDARD') && listed('2V2') ? 'Standard and 2v2' : listed('STANDARD') ? 'Standard' : '2v2';
    const upcoming = upcomingBan(card);
    if (upcoming) return `Banned in ${formats} from ${banDate(upcoming)}. Legal until then.`;
    const standard = isBanned(card, 'STANDARD');
    if (!standard && !isBanned(card, '2V2')) return null;
    const since = card.bannedSince ? banDate(card.bannedSince) : null;
    return `Banned in ${formats}${since ? ` since ${since}` : ''}.${standard ? '' : ' Still legal in Standard.'}`;
}

/**
 * One version of a card that's owned and counted separately: a finish of the card (standard,
 * foil, and promos such as Nexus Night or a Summoner Skirmish Top 8), or, for an overnumbered
 * card, the plain and signature printings, which
 * are cards of their own with the same number. Same versions as the Android card detail.
 */
/** The light over the art: white for a foil, a rainbow for Nexus Night, gold for Skirmish prizes. */
const SHEEN: Record<Exclude<Finish, 'STANDARD'>, string> = {
    FOIL: 'foil-sheen',
    NEXUS_NIGHT: 'foil-sheen-nexus',
    SKIRMISH_PARTICIPATION: 'foil-sheen-gold',
    SKIRMISH_TOP_8: 'foil-sheen-gold',
    SKIRMISH_CHAMPION: 'foil-sheen-gold',
};

interface Version {
    cardId: string;
    finish: Finish;
    label: string;
}

function versionsOf(card: Card, byId: ReadonlyMap<string, Card>): Version[] {
    // "OGN-299" and its signature "OGN-299*" are shown together, the plain one first.
    const baseId = card.cardId.replace(/\*$/, '');
    const base = byId.get(baseId);
    const signature = byId.get(`${baseId}*`);
    if (base && signature) {
        return [
            { cardId: base.cardId, finish: 'STANDARD', label: 'Overnumbered' },
            { cardId: signature.cardId, finish: 'STANDARD', label: 'Signature' },
        ];
    }
    return finishesOf(card).map(finish => ({ cardId: card.cardId, finish, label: FINISH_LABEL[finish] }));
}

/**
 * One card: its art over a blurred, zoomed copy of itself, the owned count, the rules text and
 * any ban. Opened from a grid it shows on top of that page, with ← → to move between cards;
 * opened from a link it's a page of its own.
 */
export default function CardDetailPage() {
    const { cardId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();
    const state = useMemo(() => (location.state ?? {}) as DetailState, [location.state]);
    const overlay = Boolean(state.background);
    const { binderCards, masterCards, loading, changeFinish } = useCards();
    const { prices, formatPrice } = usePrices();

    const itemsById = useMemo(() => new Map(binderCards.map(i => [i.card.cardId, i])), [binderCards]);
    const cardsById = useMemo(() => new Map(masterCards.map(c => [c.cardId, c])), [masterCards]);
    const item = cardId ? itemsById.get(cardId) : undefined;
    const card = useMemo(() => item?.card ?? (cardId ? cardsById.get(cardId) : undefined), [item, cardId, cardsById]);
    const ids = useMemo(() => state.ids ?? [], [state]);
    const index = cardId ? ids.indexOf(cardId) : -1;

    // The finish being looked at and counted. Opening another card goes back to standard.
    const [picked, setPicked] = useState<{ cardId?: string; finish: Finish }>({ finish: 'STANDARD' });
    const versions = useMemo(() => (card ? versionsOf(card, cardsById) : []), [card, cardsById]);
    const finish: Finish = picked.cardId === cardId && versions.some(v => v.finish === picked.finish) ? picked.finish : 'STANDARD';
    const ownedOf = (v: Version) => {
        const owned = itemsById.get(v.cardId);
        return owned ? countOf(owned, v.finish) : 0;
    };
    const quantity = item ? countOf(item, finish) : 0;
    const total = item?.quantity ?? 0;
    // Alternate arts and other printings with their own art. Versions in the bar aren't repeated here.
    const printings = useMemo(
        () => (card ? masterCards.filter(c => c.name === card.name && c.cardId !== card.cardId && !versions.some(v => v.cardId === c.cardId)) : []),
        [card, masterCards, versions],
    );

    const close = useCallback(() => {
        if (overlay) navigate(-1);
        else navigate('/');
    }, [overlay, navigate]);
    const go = useCallback((offset: number) => {
        const next = ids[index + offset];
        if (next) navigate(`/cards/${next}`, { replace: true, state });
    }, [ids, index, navigate, state]);

    useEffect(() => {
        if (!card) return;
        const onKey = (e: KeyboardEvent) => {
            if (isTyping(e.target) || e.metaKey || e.ctrlKey || e.altKey) return;
            if (e.key === 'ArrowLeft') go(-1);
            else if (e.key === 'ArrowRight') go(1);
            else if (e.key === 'Escape' && overlay) close();
            else if (e.key === '+' || e.key === '=') changeFinish(card.cardId, finish, 1);
            else if (e.key === '-' && quantity > 0) changeFinish(card.cardId, finish, -1);
            else return;
            e.preventDefault();
        };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [card, finish, quantity, go, close, overlay, changeFinish]);

    useEffect(() => {
        if (!overlay) return;
        document.body.style.overflow = 'hidden';
        return () => { document.body.style.overflow = ''; };
    }, [overlay]);

    if (!card) {
        if (loading || masterCards.length === 0) return <Loading label="Loading card" />;
        return (
            <div className="p-10 text-center flex flex-col gap-3 items-center">
                <span className="rift-label text-lg">Card not found</span>
                <Link to="/">Back to your collection</Link>
            </div>
        );
    }

    const price = prices[card.cardId];
    const hasFinishes = finishesOf(card).length > 1;
    // A checked scan of the promo printing, when there is one (data/promos.json).
    const promoScan = isPromo(finish) ? card.promoImages?.[finish as keyof NonNullable<Card['promoImages']>] : undefined;
    const ban = banSentence(card);
    const rules = card.text && card.text !== '[NO TEXT]' ? card.text : null;
    // Equipment prints what it gives the unit it's attached to in a box of its own.
    const granted = card.type === 'GEAR' && card.effect && card.effect !== card.text ? card.effect : null;
    const might = card.might !== null && card.type !== 'GEAR' ? `${card.might} Might` : card.mightBonus ? `+${card.mightBonus} Might` : null;

    const content = (
        // overflow-clip, not hidden: the zoomed backdrop is wider than the page, and a hidden
        // overflow can still be scrolled sideways by focus or scrollIntoView.
        <div className="relative min-h-full overflow-clip">
            <img src={card.imageUrl} alt="" aria-hidden="true" className="absolute inset-0 w-full h-full object-cover object-[50%_28%] scale-150 blur-[28px] saturate-[1.25]" />
            <div aria-hidden="true" className="absolute inset-0 bg-gradient-to-b from-ink/45 via-ink/70 to-ink" />

            <div className="relative max-w-[1280px] mx-auto px-4 sm:px-8 pt-6 pb-14 flex flex-col gap-6">
                <div className="flex flex-wrap items-center gap-3">
                    <button type="button" onClick={close} className="flex items-center gap-2 text-parchment hover:text-ivory cursor-pointer min-h-10">
                        {overlay ? <X aria-hidden="true" className="w-5 h-5" /> : <ChevronLeft aria-hidden="true" className="w-5 h-5" />}
                        {overlay ? 'Close' : 'Your collection'}
                    </button>
                    <span className="flex-1" />
                    {ids.length > 1 && index >= 0 && (
                        <>
                            <span className="hidden sm:inline text-sm text-muted">
                                Card {index + 1} of {ids.length} · <kbd className="px-1.5 border border-line-strong font-sans text-xs">←</kbd> <kbd className="px-1.5 border border-line-strong font-sans text-xs">→</kbd> to browse
                            </span>
                            <button type="button" aria-label="Previous card" disabled={index === 0} onClick={() => go(-1)} className="w-10 h-10 flex items-center justify-center border border-line-strong bg-ink/60 hover:border-gold-deep disabled:opacity-35 cursor-pointer">
                                <ChevronLeft aria-hidden="true" className="w-5 h-5" />
                            </button>
                            <button type="button" aria-label="Next card" disabled={index === ids.length - 1} onClick={() => go(1)} className="w-10 h-10 flex items-center justify-center border border-line-strong bg-ink/60 hover:border-gold-deep disabled:opacity-35 cursor-pointer">
                                <ChevronRight aria-hidden="true" className="w-5 h-5" />
                            </button>
                        </>
                    )}
                </div>

                <div className="flex flex-wrap gap-10 items-start">
                    <div className="flex-[0_1_400px] min-w-[240px] flex flex-col gap-3">
                        <a href={card.imageUrl} target="_blank" rel="noreferrer" title="Open the full-size image" className="relative block overflow-hidden border-2 border-gold-deep shadow-[0_24px_60px_rgba(0,0,0,0.6)]">
                            <img
                                src={promoScan ?? card.imageUrl}
                                alt={finish === 'STANDARD' ? card.name : `${card.name}, ${FINISH_LABEL[finish]}`}
                                className={`block w-full ${card.orientation === 'landscape' ? '' : 'aspect-[744/1039] object-cover'}`}
                            />
                            {finish !== 'STANDARD' && <span aria-hidden="true" className={SHEEN[finish]} />}
                            {/* No scan of this promo yet: say PROMO along the bottom, as the card does. */}
                            {isPromo(finish) && !promoScan && (
                                <span className="absolute left-1/2 -translate-x-1/2 bottom-[3.2%] px-2 py-px bg-ink/85 border border-gold text-gold rift-label text-[11px] tracking-[0.18em]">
                                    Promo
                                </span>
                            )}
                        </a>
                        {versions.length > 1 && (
                            <div role="group" aria-label="Version" className="flex border border-line-strong bg-ink/70">
                                {versions.map(v => {
                                    const current = v.cardId === card.cardId && v.finish === finish;
                                    const owned = ownedOf(v);
                                    return (
                                        <button
                                            key={`${v.cardId}-${v.finish}`}
                                            type="button"
                                            aria-pressed={current}
                                            onClick={() => {
                                                if (v.cardId !== card.cardId) navigate(`/cards/${v.cardId}`, { replace: true, state });
                                                else setPicked({ cardId: card.cardId, finish: v.finish });
                                            }}
                                            className={`flex-1 min-w-0 min-h-11 px-2 flex items-center justify-center gap-1.5 rift-label text-[13px] tracking-[0.08em] border-l border-line first:border-l-0 cursor-pointer ${current ? 'bg-gold-wash text-gold shadow-[inset_0_-2px_0_var(--color-gold)]' : 'text-parchment hover:text-ivory hover:bg-raised'}`}
                                        >
                                            <span className="truncate">{v.label}</span>
                                            {owned > 0 && <span className={`rift-number text-xs ${current ? 'text-gold' : 'text-muted'}`}>×{owned}</span>}
                                        </button>
                                    );
                                })}
                            </div>
                        )}
                        {printings.length > 0 && (
                            <div className="flex flex-col gap-2">
                                <span className="text-[13px] text-muted">Other printings</span>
                                <div className="flex flex-wrap gap-2">
                                    {printings.map(p => (
                                        <Link key={p.cardId} to={`/cards/${p.cardId}`} replace state={state} title={p.cardId} className="w-14 border border-line hover:border-gold">
                                            <img src={p.imageUrl} alt={`${p.name}, ${p.cardId}`} loading="lazy" className="w-full" />
                                        </Link>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="flex-[999_1_420px] min-w-0 max-w-[680px] flex flex-col gap-4">
                        <div className="flex flex-col gap-1.5">
                            <RiftLabel className="text-[15px] text-gold">{typeLine(card)}</RiftLabel>
                            <DomainBand domains={card.domain} className="px-5 py-3">
                                <h1 className="font-bold text-[30px] sm:text-[34px] leading-tight text-white">{card.name}</h1>
                            </DomainBand>
                        </div>

                        {ban && (
                            <div role="note" className={`flex flex-wrap items-center gap-3 px-3.5 py-3 border ${isBanned(card) ? 'border-banned bg-banned/15' : 'border-banned-2v2 bg-banned-2v2/15'}`}>
                                <BannedTag card={card} className="h-6 text-[13px]" />
                                <span className="flex-1 text-parchment">{ban}</span>
                            </div>
                        )}

                        <div className="flex flex-wrap items-center gap-[18px]">
                            {['UNIT', 'SPELL', 'GEAR'].includes(card.type) && <EnergyToken value={card.cost} />}
                            {might && <span className="rift-number text-[22px]">{might}</span>}
                            <span className="flex items-center gap-2 text-parchment"><RarityGem rarity={card.rarity} className="w-4 h-4" />{RARITY_LABEL[card.rarity]}</span>
                            <span className="flex-1" />
                            {shownDomains(card.domain).filter(d => d !== 'COLORLESS').map(d => <DomainIcon key={d} domain={d} className="w-[34px] h-[34px]" />)}
                        </div>

                        <div className="flex flex-wrap items-center gap-4 py-3 pl-[18px] pr-3 bg-surface/90 border border-gold-deep">
                            <div className="flex-[1_1_200px] flex flex-col gap-0.5">
                                <RiftLabel className="text-sm text-muted">{hasFinishes ? `Owned · ${FINISH_LABEL[finish]}` : 'Owned'}</RiftLabel>
                                <span className={price ? 'text-gold' : 'text-muted'}>{price ? `${formatPrice(price)} each · TCGplayer market` : 'No price yet'}</span>
                                {hasFinishes && total !== quantity && <span className="text-sm text-muted">{total} {total === 1 ? 'copy' : 'copies'} in all</span>}
                            </div>
                            <QuantityStepper
                                value={quantity}
                                onChange={n => changeFinish(card.cardId, finish, Math.max(0, n) - quantity)}
                                label={hasFinishes ? `${card.name} (${FINISH_LABEL[finish]})` : card.name}
                                size="lg"
                            />
                        </div>

                        <LocationPanel cardId={card.cardId} cardName={card.name} quantity={total} />

                        {(rules || granted) && (
                            <div className="flex flex-col gap-3.5 px-[22px] py-[18px] bg-ivory text-ink-text text-[17px] leading-relaxed">
                                {rules && <p><RulesText text={rules} /></p>}
                                {granted && (
                                    <>
                                        {rules && <div className="h-px bg-ink-muted/35" />}
                                        <RiftLabel className="text-sm text-ink-muted">Equipped unit has</RiftLabel>
                                        <p><RulesText text={granted} /></p>
                                    </>
                                )}
                            </div>
                        )}

                        <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-muted">
                            <span>{card.setName || card.setCode} · {card.cardId}</span>
                            {card.artistName && <span>Art by {card.artistName}</span>}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );

    if (!overlay) return content;
    return (
        <div role="dialog" aria-modal="true" aria-label={card.name} className="fixed inset-0 z-[70] overflow-y-auto bg-ink">
            {content}
        </div>
    );
}
