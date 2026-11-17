# Binders

The collection is one set of quantities. Sub-binders, such as a master set binder for Origins, are saved views of it: they choose which cards to show and never hold quantities of their own. Adding a copy in any binder updates the collection, so a card can't be counted twice.

In the Android app the Collection tab opens on All cards. Sub-binders sit in a tab strip under the title and swap the grid in place; "+ New binder" at the end of the strip creates one, with one-tap master sets for sets that don't have one yet. When the grid is in set order and spans more than one set, it's split into a section per set.

## Firestore

Everything lives under `users/{uid}`.

| Path | What it holds |
|---|---|
| `binders/main/entries/{cardId}` | The collection: `binderId` (`"main"`), `cardId`, `quantity`, and `finishes`, the copies in each finish other than standard (below). An entry is deleted when its quantity reaches 0. |
| `binders/{binderId}` | A sub-binder definition, below. There's no `main` document, only its `entries`. |
| `locations/{cardId}` | Where a card's copies are kept: `cardId` and `places` (below). Only for cards someone has placed. |

### Finishes

A foil, or a promo printed with the same number and art as the standard card (a Nexus Night promo, or a Summoner Skirmish prize), isn't a card of its own. The entry counts it instead:

- `quantity` is every copy owned, whatever its finish. Deck "missing" counts, binder progress and filters all read this.
- `finishes` maps a finish to how many of those copies are in it, e.g. `{"FOIL": 1, "NEXUS_NIGHT": 2}`. The rest are standard. It's empty or absent for most cards.
- The finishes, in the order copies are taken away: `FOIL`, `NEXUS_NIGHT`, `SKIRMISH_PARTICIPATION`, `SKIRMISH_TOP_8`, `SKIRMISH_CHAMPION`.
- Changing only the total adds standard copies, and removes standard copies first, then the finishes in that order.
- If the finish counts add up to more than `quantity`, apps cut them down to fit when they read the entry, keeping the rarest first.
- Apps keep finish names they don't know, so a newer app can add finishes without an older one losing them.
- Builds before promo finishes stored `foil` and `nexusNight` fields instead. Apps still read those when `finishes` is missing, and drop them the next time they save the entry.

Which finishes a card has:
- **Foil:** commons and uncommons, other than runes.
- **Promos:** the cards listed in `data/promos.json`. The card data generator copies them into each card's `promos` list. Nexus Night isn't limited to commons: some rares and epics, such as the Proving Grounds starter cards, have one too.

Card detail shows a promo's own scan when `data/promos.json` has a checked one: the generator saves it as `card_images/<cardId>-<finish>.webp` and lists it in the card's `promoImages`. Without one, the apps draw a PROMO badge over the standard art.

Runes have alternate arts instead, which are cards of their own (the Nexus Night runes are `OGN-007b` and the like). The promos not added yet are in [card-backlog.md](card-backlog.md).

Printings with their own number, such as alternate arts (`OGN-027a`) and signatures (`OGN-299*`), are separate cards with entries of their own.

The Android app implements this in `data/model/BinderEntry.kt` and `data/model/Finish.kt`, and the web app in `Web App/src/lib/finishes.ts`; their tests run the same cases. `shared/finish-test-cases.json` holds the cases both run. Card detail in both apps counts each finish separately: Android swipes between the versions, and the web app has a bar of them under the art. Promo finishes show their scan, or a PROMO badge on the standard art.

### Locations

Optional, per card: where its copies are kept, e.g. `{"Red binder": 2, "Deck box": 1}`. Copies in no place are unplaced, so a card with no `locations` document has all its copies unplaced.

- **Kept apart from the collection entry on purpose.** Every app rewrites an entry whole when a quantity changes, including builds already installed that know nothing about locations. A location stored on the entry would be erased the next time anyone pressed + or −.
- **Fitted to what you own when read.** Names are trimmed, blank names and empty counts dropped, and names that differ only in case merged. Places are taken in name order until their counts reach the copies owned, so removing copies never leaves more placed than owned. Apps save the fitted version the next time the card's places change.
- **Setting a count** is limited to the copies not placed elsewhere; zero removes the place. **Adding a place** puts every unplaced copy there.
- Locations count copies of the card, whatever their finish.
- Deleting a card's last copy leaves its `locations` document, so adding the card back restores its places; the apps ignore places for cards you don't own.

Card detail in both apps has a Location section for this, suggesting places you've used before. The rules are in `data/model/CardPlaces.kt` (Android) and `Web App/src/lib/places.ts`; both run `shared/location-test-cases.json`.

The Firestore rules (`Web App/firestore.rules`) must allow `users/{uid}/locations/{cardId}`; see "Firestore rules" in [deploying.md](deploying.md).

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
