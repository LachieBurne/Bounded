# Deck rules

Both apps enforce the same Riftbound constructed deckbuilding rules (core rules, section 103):

- **Android:** `Android App/app/src/main/java/com/lburne/bounded/data/rules/DeckRules.kt`
- **Web:** `Web App/src/rules/deckRules.ts`

They share one set of test cases, [`shared/deck-rules-test-cases.json`](../shared/deck-rules-test-cases.json). CI runs the cases against both apps:
- Android: `./gradlew testDebugUnitTest`
- Web: `npm test`

To change a rule:
1. Add or update a case in the JSON.
2. Change both implementations.
3. Check that both test suites pass.

## The rules

| Rule | Core rule | What the apps do |
|---|---|---|
| One legend | 103.2 | The first step. A second legend is refused. |
| Domain identity | 103.2.b | Every card's domains must all be among the legend's. Colorless cards, and cards with no domain, fit any deck. This applies to runes, battlefields and the sideboard too. |
| Chosen champion | 103.3.a.2 | A champion unit with the legend's champion tag (`championTag` in the card data), compared ignoring case. Signature units such as Tibbers can't be the champion. |
| Main deck | 103.3 | At least 40 units, spells and gear, counting the champion. There's no maximum. |
| Copy limit | 103.3.b | At most 3 copies of each card **name**, across the main deck, the champion and the sideboard. Alt arts share their name's limit. |
| Signature cards | 103.3.d | At most 3 in total, and only ones with the legend's champion tag. `isSignature` is set by `scripts/generate_card_data.py`. |
| Rune deck | 103.4 | Exactly 12 runes. |
| Battlefields | 103.5 | Exactly 3, with different names. |
| Sideboard | Tournament play | Up to 8 units, spells or gear. No legends, runes or battlefields. |
| Tokens | — | Tokens and token helpers can't go in a deck. |
| Banned cards | Ban list | Decks are checked against the Standard ban list. A banned card can still be added, but the deck is flagged as breaking a rule. Cards banned only in 2v2 (Master Yi, Wuju Bladesman) are legal. The list is `data/banned_cards.json`, which the card data generator turns into `bannedIn` and `bannedSince` on every printing of each banned card. Update it when Riot announces bans. |

## Interpretations

The rules leave some points open. Change these here and in the shared cases if official guidance differs:
- **Copy limit includes the sideboard.** Main deck plus sideboard copies count together. This is the usual TCG convention; core rule 103.3.b only mentions the main deck.
- **Signature limit includes the sideboard,** for the same reason.
- **Battlefield count is 3.** Core rule 103.5.a leaves the number to the mode of play, and 3 is the constructed standard.

## The builder's steps

`currentStep` walks a deck through these steps:
1. Legend
2. Champion
3. Runes (12)
4. Battlefields (3)
5. Main deck (40, including the champion)
6. Sideboard (8)
7. Complete

A deck counts as legal once the main deck is done. `validate` returns no issues for a deck without a sideboard. The sideboard step is offered but optional.

`checkAdd` explains why a card can't be added, and both apps show that reason to the user. `validate` lists every rule a saved deck breaks. Both editors show the rule breaks: problems such as an over-limit card name or an off-domain card, as opposed to a deck that just isn't finished yet. These mostly affect decks built before the rules were enforced.

## Firestore fields

These are the deck fields both apps read and write:
- **Deck document:** `legendCardId`, `championCardId`, `coverCardUrl`, `mainCount`, `sideCount`, `lastModified`.
  - `mainCount` and `sideCount` are the main deck and sideboard counts from the rules above.
  - Each edit writes them together with the entry, in one batch.
  - Opening a deck in either app corrects counts saved by older versions.
- **Entry documents:** stored at `entries/{cardId}_{isSideboard}`, with fields `deckId`, `cardId`, `quantity` and `isSideboard`.
  - Android builds before this change wrote the flag as `sideboard`.
  - Both apps now read either field name, and write both for older installs.
