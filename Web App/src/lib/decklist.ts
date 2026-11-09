import { canBeChampion, type DeckContents, type RulesCard } from '../rules/deckRules';

/*
 * Reads pasted decklists: the apps' own export and the similar lists other Riftbound sites
 * use. A port of the Android app's DecklistParser.kt; both pass every case in
 * shared/decklist-import-cases.json, so change them together.
 *
 *   Legend:
 *   1 Master Yi - Wuju Bladesman
 *   Champion:
 *   1 Master Yi, Honed
 *   MainDeck:
 *   3x Twin Stance
 *
 * Section headers are optional. A line is "count name" ("3 Name" or "3x Name"), or a name on
 * its own for one copy. A card can be named by its card ID instead. Names ignore case,
 * punctuation and the "Name - Title" / "Name, Title" difference. When several printings share
 * a name, the base printing is used.
 */

export type DecklistCard = RulesCard & { baseName?: string; sortOrder?: number };

export interface ParsedDecklist {
    legendId: string | null;
    championId: string | null;
    /** Main deck card IDs and quantities, including the legend, champion, runes and battlefields. */
    main: Record<string, number>;
    side: Record<string, number>;
    unmatched: string[];
}

type Section = 'NONE' | 'LEGEND' | 'CHAMPION' | 'MAIN' | 'SIDEBOARD';

const HEADERS: Record<string, Section> = {
    'legend': 'LEGEND',
    'champion': 'CHAMPION',
    'chosen champion': 'CHAMPION',
    'maindeck': 'MAIN',
    'main deck': 'MAIN',
    'main': 'MAIN',
    'deck': 'MAIN',
    'runes': 'MAIN',
    'rune deck': 'MAIN',
    'battlefields': 'MAIN',
    'battlefield': 'MAIN',
    'sideboard': 'SIDEBOARD',
    'side deck': 'SIDEBOARD',
    'side': 'SIDEBOARD',
};

// "Main Deck (40):" -> "main deck"
const HEADER_RE = /^([A-Za-z ]+?)\s*(\(\d+\))?\s*:?$/;
const COUNT_RE = /^(\d+)\s*[xX]?\s+(.+)$/;
const VARIANT_RE = /[a-z*]$/;

export function normalizeName(name: string): string {
    return name.toLowerCase().replace(/[’'`]/g, '').replace(/[^a-z0-9]+/g, ' ').trim();
}

export function parseDecklist(text: string, cards: Iterable<DecklistCard>): ParsedDecklist {
    const all = [...cards];
    const byId = new Map(all.map(c => [c.cardId.toLowerCase(), c]));
    // Base printings first, so "Master Yi, Honed" means YI-1 rather than its alt art YI-1a.
    const ordered = [...all].sort((a, b) =>
        Number(VARIANT_RE.test(a.cardId)) - Number(VARIANT_RE.test(b.cardId))
        || (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
        || (a.cardId < b.cardId ? -1 : a.cardId > b.cardId ? 1 : 0));
    const byName = new Map<string, DecklistCard>();
    const byBaseName = new Map<string, DecklistCard[]>();
    for (const c of ordered) {
        const key = normalizeName(c.name);
        if (!byName.has(key)) byName.set(key, c);
        if (c.baseName && c.baseName.trim()) {
            const base = normalizeName(c.baseName);
            byBaseName.set(base, [...(byBaseName.get(base) ?? []), c]);
        }
    }

    const resolve = (name: string, section: Section): DecklistCard | undefined => {
        const id = byId.get(name.trim().toLowerCase());
        if (id) return id;
        const key = normalizeName(name);
        const named = byName.get(key);
        if (named) return named;
        // Some sites list the legend or champion by champion name alone ("Master Yi").
        if (section === 'LEGEND') {
            return ordered.find(c => c.type === 'LEGEND'
                && (normalizeName(c.championTag ?? '') === key || normalizeName(c.baseName ?? '') === key));
        }
        if (section === 'CHAMPION') return byBaseName.get(key)?.find(c => c.type === 'UNIT');
        return undefined;
    };

    let section: Section = 'NONE';
    let legendId: string | null = null;
    let championId: string | null = null;
    const main: Record<string, number> = {};
    const side: Record<string, number> = {};
    const unmatched: string[] = [];

    for (const raw of text.split(/\r\n|\r|\n/)) {
        const line = raw.trim();
        if (!line || line.startsWith('#') || line.startsWith('//')) continue;

        const headerMatch = HEADER_RE.exec(line);
        const header = headerMatch ? HEADERS[headerMatch[1].trim().toLowerCase()] : undefined;
        if (header) {
            section = header;
            continue;
        }

        const countMatch = COUNT_RE.exec(line);
        const quantity = countMatch ? parseInt(countMatch[1], 10) : 1;
        const name = countMatch ? countMatch[2] : line;
        const card = resolve(name, section);
        if (!card || quantity <= 0) {
            unmatched.push(line);
            continue;
        }

        if (card.type === 'LEGEND') {
            // One legend; a second one can't be placed anywhere.
            if (legendId === null && section !== 'SIDEBOARD') {
                legendId = card.cardId;
                main[card.cardId] = 1;
            } else {
                unmatched.push(line);
            }
        } else if (section === 'SIDEBOARD') {
            side[card.cardId] = (side[card.cardId] ?? 0) + quantity;
        } else {
            if (section === 'CHAMPION' && championId === null) championId = card.cardId;
            main[card.cardId] = (main[card.cardId] ?? 0) + quantity;
        }
    }

    // No "Champion:" section: choose the first card that can be the legend's champion.
    if (championId === null && legendId !== null) {
        const legend = byId.get(legendId.toLowerCase());
        championId = Object.keys(main).find(id => {
            const c = byId.get(id.toLowerCase());
            return c ? canBeChampion(c, legend) : false;
        }) ?? null;
    }

    return { legendId, championId, main, side, unmatched };
}

export function contentsOf(p: ParsedDecklist): DeckContents {
    return { legendId: p.legendId, championId: p.championId, main: p.main, side: p.side };
}
