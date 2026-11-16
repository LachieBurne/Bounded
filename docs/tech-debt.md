# Tech debt and things to revisit

A running list. Add to it when you leave something unfinished, and delete an item when it's done. Missing cards have their own list: [card-backlog.md](card-backlog.md).

## Card data

| Item | Why it matters | Next step |
|---|---|---|
| Promos, signatures, alt-art runes, Nexus Night and Skirmish promos are missing | Collectors can't track them, and master set binders undercount | See [card-backlog.md](card-backlog.md). Blocked on images and an ID scheme for promos that share a number. |
| No `PROMO` rarity | Existing promos are tagged `SHOWCASE`, so the showcase filter and the "include showcase" binder option also cover promos | Decide when the first same-number promo is added. Both apps need the enum value. |
| VEN alt-art runes (VEN-R0Xa) borrow the base rune's image | They look identical to the base runes in the binder | Add real art to `card_images/`. |
| The set list is hard-coded (`RiftSet` in Android, the set list in the web filters) | A new set needs a code change in both apps as well as the data | Derive the set list from `riftbound_data.json`. |
| The gallery leaves some artists blank | 45 Radiance cards had none; they're filled in from the card art by `artistName` overrides. New cards may arrive the same way. | Check [unknown-artists.md](unknown-artists.md) after each card data update and add overrides. |
| The gallery's Radiance data is unreliable | 23 cards had the wrong type (now taken from the image alt text), 30 had a missing or wrong domain (fixed by overrides, read from the printed icon), and tags may also be wrong (Meticulous Restorationist was tagged "Device" instead of "Piltover"; Nazuman Ntofo and Protector's Shield were missing "Equipment"; Shadows of the Past had an effect of "1"). Champion tags drive the deck rules. | Check the tags on the 23 retyped cards against the card art and add overrides. The generator now warns when a single-domain card's data doesn't match its printed icon. |
| Domain overrides are permanent | If the gallery later corrects a Radiance domain, the override hides any further change | Once the gallery's Radiance data agrees with the art, remove the `domain` overrides from `card_overrides.json`. |
| The ban list is kept by hand (`data/banned_cards.json`) | A new ban isn't shown until someone adds it; the last check was 2026-11-09, after the September 18 bans | Check the Riftbound Rules Hub when Riot announces bans and add them. |
| OGN-235 Karma is fixed by an override | The official gallery still tags it Vi; if the gallery is corrected, the override becomes redundant | Remove the override from `data/card_overrides.json` once the gallery is fixed. |

## Security

| Item | Why it matters | Next step |
|---|---|---|
| The old JustTCG API key is in git history | Anyone with the repo history has it | Revoke it on JustTCG (the new key lives only in the `JUSTTCG_API_KEY` secret). |

## Android

| Item | Why it matters | Next step |
|---|---|---|
| Release builds have no signing config | The minified release APK can't be installed or shared until it's signed, so the README still links a debug build | Create an upload key, keep it out of git, and sign release builds from a CI secret. |
| The minified (R8) release build hasn't been used signed in | Firestore and Gson read the model classes by reflection. The keep rule covers `data/model`, and R8's mapping shows their names kept, but syncing hasn't been tried in a release build | Sign in on a release build, change some quantities and edit a deck. Any reflected class added outside `data/model` needs its own keep rule. |
| `targetSdk` is 35 while `compileSdk` is 37 | Play requires recent targets for app updates, and raising it opts the app into newer platform behaviour | Raise it one level at a time and test each on a device. |
| Deprecated Compose APIs: `LocalClipboardManager` (deck list, deck import, deck editor) and `TabRow` (deck editor) | Build warnings now; they'll be removed in a later Compose release | Move to `LocalClipboard` and `PrimaryTabRow`. The deck editor ones can go with its restyle. |
| Room uses `fallbackToDestructiveMigration` | Fine today because the table only caches card data, but it would wipe anything else stored locally | Keep the database to card data only, or write migrations. |
| No UI or instrumented tests | Layout and navigation changes are only checked by hand | Part of the testing plan: Compose UI tests for navigation, the filter sheet and deck import. |
| Navigation uses string routes | Typos in route strings fail at runtime | Move to type-safe routes. Navigation 2.10 supports them; they need the Kotlin serialization plugin. |
| `DeckEditorScreen.kt` is about 1,000 lines | Hard to change safely | Split the stats, deck list and card picker into their own files. |
| Gilded Rift is only fully applied to Collection, the filter sheet, card detail, Decks and the navigation bar | The deck editor, deck import, settings, login and scanner pick up the palette through the Material theme but keep stock layouts | Restyle them screen by screen. The design is the "Bounded mobile redesign" Claude Design canvas. |
| Labels use the phone's Roboto Condensed instead of Barlow Condensed from the design | Close, but not the designed type | Bundle Barlow and Barlow Condensed (SIL Open Font License) in `res/font`, or use downloadable Google Fonts. |
| Keyword chip colours in rules text are only known for Action, Reaction, Deploy, Ganking and Assault | Other keywords use a neutral grey | Sample the rest from card art and add them to `KeywordColours` in `CardDetailScreen.kt`. |
| The card detail backdrop blur needs Android 12 | Older phones show the zoomed art under the dark veil, unblurred | Acceptable for now; a pre-blurred copy could be made with RenderScript-free code if it matters. |
| Binders can only be defined by filters (set, domain, rarity, type) | No hand-picked binders, such as a trade binder or a wishlist | Decide whether they're wanted. They'd need a card list on the binder document; see [binders.md](binders.md). |
| The deck editor's card picker has its own tile (`DeckBuilderGridItem`) and hard-coded white text | It looks different from the collection grid and ignores the theme | Move it onto `CardTile`/Gilded Rift colours when the deck editor is restyled. |
| Decklists can only be imported by pasting | Sharing a list from a browser to the app doesn't work | Add an `ACTION_SEND` text intent filter that opens the import screen. |
| The scanner only matches one card number per frame | Slow for bulk scanning | Revisit after the card ID scheme for promos is settled. |

## Web

| Item | Why it matters | Next step |
|---|---|---|
| Deck tiles don't show whether a deck is legal | The deck list only has each deck's counts, not its cards, so a banned card only shows inside the deck | Store a legality flag on the deck document when it's saved (both apps), or load entries for the list. |
| GitHub Pages still publishes 599 MB of old PNG card images | The APK linked from the README loads them, but they make the Pages site about 750 MB, near GitHub's 1 GB limit and slow to deploy | Replace the README's APK with a current build, then drop the PNGs from `deploy_pages.yml` (or the repository). |
| The bundle is one 770 kB chunk (mostly Firebase) | Slower first load | Split routes with `React.lazy`, or load Firestore after sign-in. |
| Collection stats charts were dropped in the redesign | The old Stats tab (cards by type and rarity) is gone; progress by binder and domain replaced it | Bring back a stats view if it's missed. |

## Prices

| Item | Why it matters | Next step |
|---|---|---|
| JustTCG's free tier (1,000 requests a month) limits refresh rates | Common cards refresh every 2 weeks | Revisit `REFRESH_DAYS` if the plan changes. |
| Most promo sets aren't priced | No prices once promos are added | See "Prices" in [card-backlog.md](card-backlog.md). |
