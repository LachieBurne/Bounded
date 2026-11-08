# Tech debt and things to revisit

A running list. Add to it when you leave something unfinished, and delete an item when it's done. Missing cards have their own list: [card-backlog.md](card-backlog.md).

## Card data

| Item | Why it matters | Next step |
|---|---|---|
| Promos, signatures, alt-art runes, Nexus Night and Skirmish promos are missing | Collectors can't track them, and master set binders undercount | See [card-backlog.md](card-backlog.md). Blocked on images and an ID scheme for promos that share a number. |
| No `PROMO` rarity | Existing promos are tagged `SHOWCASE`, so the showcase filter and the "include showcase" binder option also cover promos | Decide when the first same-number promo is added. Both apps need the enum value. |
| VEN alt-art runes (VEN-R0Xa) borrow the base rune's image | They look identical to the base runes in the binder | Add real art to `card_images/`. |
| The set list is hard-coded (`RiftSet` in Android, the set list in the web filters) | A new set needs a code change in both apps as well as the data | Derive the set list from `riftbound_data.json`. |
| OGN-235 Karma is fixed by an override | The official gallery still tags it Vi; if the gallery is corrected, the override becomes redundant | Remove the override from `data/card_overrides.json` once the gallery is fixed. |

## Security

| Item | Why it matters | Next step |
|---|---|---|
| The old JustTCG API key is in git history | Anyone with the repo history has it | Revoke it on JustTCG (the new key lives only in the `JUSTTCG_API_KEY` secret). |

## Android

| Item | Why it matters | Next step |
|---|---|---|
| Kotlin 1.9.22 with Compose compiler 1.5.10 | Newer Compose and AndroidX libraries need Kotlin 2, which blocks upgrades | Move to Kotlin 2.x and the Compose compiler Gradle plugin. |
| `gradle/libs.versions.toml` disagrees with the root `build.gradle.kts` (AGP 8.3.1 / Kotlin 1.9.0 vs 8.13.2 / 1.9.22) | It isn't clear which versions are real | Pick one place for versions, ideally the catalog. |
| Room `2.7.0-alpha01`, Firebase BOM 32.8.0, Accompanist permissions | Alpha or outdated dependencies; Accompanist permissions is deprecated | Upgrade together with Kotlin 2. |
| Java 8 bytecode target | Old target, no benefit | Move to Java 17 with the Kotlin upgrade. |
| Release builds aren't minified (`isMinifyEnabled = false`) | Bigger APK | Turn on R8 and add keep rules for Gson models. |
| OkHttp is used by `PriceRepository` but only arrives transitively | A dependency change could remove it | Declare it in `app/build.gradle.kts`. |
| Room uses `fallbackToDestructiveMigration` | Fine today because the table only caches card data, but it would wipe anything else stored locally | Keep the database to card data only, or write migrations. |
| No UI or instrumented tests | Layout and navigation changes are only checked by hand | Part of the testing plan: Compose UI tests for navigation, the filter sheet and deck import. |
| Navigation uses string routes | Typos in route strings fail at runtime | Move to type-safe routes when upgrading navigation. |
| `DeckEditorScreen.kt` is about 1,000 lines | Hard to change safely | Split the stats, deck list and card picker into their own files. |
| The theme uses dynamic colour on Android 12+ and forces dark mode | The app looks different on every phone, and the light scheme is never used | Decide on a brand palette with the web redesign. |
| Binders can only be defined by filters (set, domain, rarity, type) | No hand-picked binders, such as a trade binder or a wishlist | Decide whether they're wanted. They'd need a card list on the binder document; see [binders.md](binders.md). |
| The deck editor's card picker has its own tile (`DeckBuilderGridItem`) and hard-coded white text | It looks different from the binder grids and ignores the theme | Move it onto `CardImage`/theme colours when the theme is settled. |
| Decklists can only be imported by pasting | Sharing a list from a browser to the app doesn't work | Add an `ACTION_SEND` text intent filter that opens the import screen. |
| The scanner only matches one card number per frame | Slow for bulk scanning | Revisit after the card ID scheme for promos is settled. |

## Web

| Item | Why it matters | Next step |
|---|---|---|
| 8 existing ESLint errors outside the deck editor; CI doesn't run lint | Lint can't be added to CI until they're fixed | Fix them, then add `npm run lint` to `checks.yml`. |
| No sub-binders or deck import yet | The Android app has them; the web app ignores binder documents and can't import a decklist | Add both in the web redesign. The binder document format is in [binders.md](binders.md). |
| The redesign hasn't started | | Next after the Android UX work. |

## Prices

| Item | Why it matters | Next step |
|---|---|---|
| JustTCG's free tier (1,000 requests a month) limits refresh rates | Common cards refresh every 2 weeks | Revisit `REFRESH_DAYS` if the plan changes. |
| Most promo sets aren't priced | No prices once promos are added | See "Prices" in [card-backlog.md](card-backlog.md). |
