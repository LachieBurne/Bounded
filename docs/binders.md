# Binders

The collection is one set of quantities. Sub-binders, such as a master set binder for Origins, are saved views of it: they choose which cards to show and never hold quantities of their own. Adding a copy in any binder updates the collection, so a card can't be counted twice.

In the Android app the Collection tab opens on All cards. Sub-binders sit in a tab strip under the title and swap the grid in place; "+ New binder" at the end of the strip creates one, with one-tap master sets for sets that don't have one yet. When the grid is in set order and spans more than one set, it's split into a section per set.

## Firestore

Everything lives under `users/{uid}/binders`.

| Path | What it holds |
|---|---|
| `binders/main/entries/{cardId}` | The collection: `binderId` (`"main"`), `cardId`, `quantity`, and the finish counts `foil` and `nexusNight` (below). An entry is deleted when its quantity reaches 0. |
| `binders/{binderId}` | A sub-binder definition, below. There's no `main` document, only its `entries`. |

### Finishes

A foil, or a Nexus Night promo, has the same number and art as the standard card, so it isn't a card of its own. The entry counts it instead:

- `quantity` is every copy owned, whatever its finish. Deck "missing" counts, binder progress and filters all read this.
- `foil` and `nexusNight` say how many of those copies are in that finish. The rest are standard. Both are 0 or absent for most cards.
- Changing only the total adds standard copies, and removes standard copies first, then foil, then Nexus Night.
- If the finish counts add up to more than `quantity`, apps cut them down to fit when they read the entry (Nexus Night is kept first).

Commons and uncommons other than runes have finishes. There's no list yet of which cards have a Nexus Night promo, so every card with a foil offers one; see [card-backlog.md](card-backlog.md).

Printings with their own number, such as alternate arts (`OGN-027a`) and signatures (`OGN-299*`), are separate cards with entries of their own.

The Android app implements this in `data/model/BinderEntry.kt` and `data/model/Finish.kt`, and the web app in `Web App/src/lib/finishes.ts`; their tests run the same cases. Card detail in both apps counts each finish separately: Android swipes between the versions, and the web app has a bar of them under the art.

Sub-binder fields:

| Field | Type | Meaning |
|---|---|---|
| `binderId` | string | Same as the document ID. |
| `name` | string | Shown on the binder's tab. |
| `sets` | string[] | Set codes, e.g. `["OGN"]`. Empty means every set. |
| `domains` | string[] | `Domain` names, e.g. `["FURY"]`. Empty means any. |
| `rarities` | string[] | `RiftRarity` names. Empty means any. |
| `types` | string[] | `CardType` names. Empty means any. |
| `includeShowcase` | boolean | `false` leaves out showcase printings (alt arts, overnumbered and signed cards). |
| `createdAt` | number | Milliseconds since the epoch. Binders are listed oldest first. |

Within one field a card matches any of the values; across fields it must match all of them. Apps ignore values they don't recognise, so a newer app can add values without breaking older ones.

A binder's progress is the number of its cards with at least one copy owned, out of all its cards.

The Android app implements this in `data/model/Binder.kt` and `data/filter/CardFilter.kt`. The web app implements it in `Web App/src/lib/binder.ts` and `Web App/src/lib/cardFilter.ts`.
