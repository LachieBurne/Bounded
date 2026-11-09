import { useMemo, useState } from 'react';
import type { Card, CardType, Domain, RiftRarity } from '../../models/card';
import { inBinder, isSameAsAllCards, newBinder, type Binder } from '../../lib/binder';
import { toggle } from '../../lib/cardFilter';
import { DOMAIN_LABEL, FILTERABLE_TYPES, PLAYABLE_DOMAINS, RARITIES, RARITY_LABEL, TYPE_LABEL } from '../../lib/style';
import { Dialog } from '../rift/Dialog';
import { DomainIcon, RarityGem, SetCodeBadge } from '../rift/Rift';

/**
 * Creates or edits a sub-binder, like the Android app's BinderEditorSheet. Choosing a single
 * set names it after that set until you type a name of your own.
 */
export function BinderEditor({ initial, cards, sets, suggestedSets, onSave, onQuickMasterSet, onClose }: {
    initial: Binder | null;
    cards: Card[];
    sets: { code: string; name: string }[];
    /** Sets without a master set binder, offered as one-tap binders when creating. */
    suggestedSets: { code: string; name: string }[];
    onSave: (binder: Binder) => void;
    onQuickMasterSet: (set: { code: string; name: string }) => void;
    onClose: () => void;
}) {
    const [draft, setDraft] = useState<Binder>(() => initial ?? newBinder());
    const [nameEdited, setNameEdited] = useState(initial !== null);
    const setName = (code: string) => sets.find(s => s.code === code)?.name ?? code;

    const suggestedName = () => {
        if (draft.sets.length === 1 && !draft.domains.length && !draft.rarities.length && !draft.types.length) {
            return `${setName(draft.sets[0])} master set`;
        }
        const parts = [
            ...(draft.sets.length ? [draft.sets.map(setName).join(' + ')] : []),
            ...draft.domains.map(d => DOMAIN_LABEL[d as Domain] ?? d),
            ...draft.rarities.map(r => RARITY_LABEL[r as RiftRarity] ?? r),
            ...draft.types.map(t => TYPE_LABEL[t as CardType] ?? t),
        ];
        return parts.join(' · ') || 'New binder';
    };

    const name = nameEdited ? draft.name : suggestedName();
    const count = useMemo(() => cards.filter(c => inBinder(draft, c)).length, [cards, draft]);
    const sameAsAll = isSameAsAllCards(draft);
    const update = (patch: Partial<Binder>) => setDraft(d => ({ ...d, ...patch }));

    const action = sameAsAll ? 'Pick a set or filter'
        : count === 0 ? 'No cards match'
            : initial ? `Save · ${count} cards` : `Create binder · ${count} cards`;

    return (
        <Dialog
            title={initial ? 'Edit binder' : 'New binder'}
            onClose={onClose}
            wide
            footer={
                <button
                    type="button"
                    className="btn-gold w-full"
                    disabled={sameAsAll || count === 0}
                    onClick={() => onSave({ ...draft, name: name.trim() || suggestedName() })}
                >
                    {action}
                </button>
            }
        >
            <div className="flex flex-col gap-5">
                <p className="text-sm text-muted">A binder is a view of your collection. Copies you add in any binder count everywhere.</p>

                {!initial && suggestedSets.length > 0 && (
                    <section className="flex flex-col gap-2">
                        <h3 className="text-sm font-semibold">Master set in one tap</h3>
                        <p className="text-[13px] text-muted -mt-1">Every printing in the set, showcase cards included.</p>
                        <div className="flex flex-wrap gap-2">
                            {suggestedSets.map(s => (
                                <button key={s.code} type="button" onClick={() => onQuickMasterSet(s)} className="flex items-center gap-2 min-h-10 pl-1.5 pr-3 border border-gold-deep text-ivory text-sm hover:bg-gold-wash cursor-pointer">
                                    <SetCodeBadge code={s.code} />{s.name}
                                </button>
                            ))}
                        </div>
                        <h3 className="text-sm font-semibold mt-3">Or build your own</h3>
                    </section>
                )}

                <label className="flex flex-col gap-1.5 text-sm text-muted">
                    Name
                    <input
                        type="text"
                        value={name}
                        onChange={e => { setNameEdited(true); update({ name: e.target.value }); }}
                        className="field h-11 text-base"
                    />
                </label>

                <fieldset className="flex flex-col gap-2">
                    <legend className="text-sm font-semibold mb-1">Sets</legend>
                    <p className="text-[13px] text-muted -mt-1">None selected means every set. All cards already covers everything, so pick at least one set or filter.</p>
                    <div className="flex flex-wrap gap-1.5">
                        {sets.map(s => (
                            <button key={s.code} type="button" aria-pressed={draft.sets.includes(s.code)} onClick={() => update({ sets: toggle(draft.sets, s.code) })} className="chip">{s.name}</button>
                        ))}
                    </div>
                </fieldset>

                <label className="flex items-center gap-3 cursor-pointer">
                    <input type="checkbox" checked={draft.includeShowcase} onChange={e => update({ includeShowcase: e.target.checked })} className="w-[18px] h-[18px] accent-gold" />
                    <span className="flex flex-col">
                        <span>Include showcase cards</span>
                        <span className="text-[13px] text-muted">Alt arts, overnumbered and signed cards</span>
                    </span>
                </label>

                <fieldset className="flex flex-col gap-2">
                    <legend className="text-sm font-semibold mb-1">Domains <span className="font-normal text-muted">· optional</span></legend>
                    <div className="flex flex-wrap gap-1.5">
                        {[...PLAYABLE_DOMAINS, 'COLORLESS' as Domain].map(d => (
                            <button key={d} type="button" aria-pressed={draft.domains.includes(d)} onClick={() => update({ domains: toggle(draft.domains, d) })} className="chip">
                                <DomainIcon domain={d} decorative className="w-5 h-5" />{DOMAIN_LABEL[d]}
                            </button>
                        ))}
                    </div>
                </fieldset>

                <fieldset className="flex flex-col gap-2">
                    <legend className="text-sm font-semibold mb-1">Rarities <span className="font-normal text-muted">· optional</span></legend>
                    <div className="flex flex-wrap gap-1.5">
                        {RARITIES.filter(r => r !== 'SHOWCASE').map(r => (
                            <button key={r} type="button" aria-pressed={draft.rarities.includes(r)} onClick={() => update({ rarities: toggle(draft.rarities, r) })} className="chip">
                                <RarityGem rarity={r} />{RARITY_LABEL[r]}
                            </button>
                        ))}
                    </div>
                </fieldset>

                <fieldset className="flex flex-col gap-2">
                    <legend className="text-sm font-semibold mb-1">Card types <span className="font-normal text-muted">· optional</span></legend>
                    <div className="flex flex-wrap gap-1.5">
                        {FILTERABLE_TYPES.map(t => (
                            <button key={t} type="button" aria-pressed={draft.types.includes(t)} onClick={() => update({ types: toggle(draft.types, t) })} className="chip">{TYPE_LABEL[t]}</button>
                        ))}
                    </div>
                </fieldset>
            </div>
        </Dialog>
    );
}
