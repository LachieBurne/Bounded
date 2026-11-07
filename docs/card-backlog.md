# Card data backlog

Cards that exist but aren't in `riftbound_data.json` yet. The official gallery only lists main-set cards, so everything here goes in `data/extra_cards.json`.

These notes come from TCGplayer's product list (the source of JustTCG's data), checked 2026-11-08. Tokens are out of scope.

`python scripts/update_prices.py --plan` lists every card JustTCG prices that our data lacks. Use it to check this list against reality.

## Blockers that apply to most of this

1. **Images.**
   - The gallery has no images for these cards.
   - TCGplayer's image server refuses automated downloads.
   - To add a card's art: save it as `card_images/<ID>.webp` and set `imageFile` in the extras entry. Until then, an entry can borrow the base card's image, as the VEN alt-art runes do now.
2. **Card IDs for same-number promos.**
   - Most promos are printed with the same number as the base card. For example, the Nexus Night *Blazing Scorcher* is `001/298`, the same as OGN-001.
   - Our ID format (`SET-[prefix]NUM[suffix]`, `ID_RE` in `scripts/generate_card_data.py`) can't tell them apart.
   - Pick a scheme before adding these, e.g. `OGN-001-nn`, `SFD-051-top8`, `SFD-051-champ`, `OGN-247-metal`. Then update:
     - `ID_RE` and the sort order;
     - the scanner's card-number matching;
     - `our_card_id()` in `scripts/update_prices.py`.
   - Alt-art promos with an `a`/`b` suffix already fit the current scheme. They need no ID decision.
3. **Rarity.** We have no `PROMO` rarity, and the existing promos use `SHOWCASE`. Decide whether to add `PROMO` to `RiftRarity` in both apps.
4. **Prices.**
   - `update_prices.py` only matches promos ending in `b` from JustTCG's *Organized Play Promotional Cards* set.
   - Other promo sets need adding to `SETS`: *Riftbound Promotional Cards*, *Judge Promotional Cards*, *Worlds Bundle 2025*, *T1 2025 Worlds Champion Bundles* and *Secret Garden*.
   - Each new promo type needs a matching rule. JustTCG puts the variant in the card ID slug, e.g. `...-signature-showcase`, `...-alternate-art-showcase`.

## Alternate-art promos

These fit the current ID scheme, so they can be added as soon as there are images.

| Card | Name | Where it's from |
|---|---|---|
| OGN-036a | Vi, Destructive | Riftbound Promotional Cards |
| VEN-041a | Riven, Shattered | Riftbound Promotional Cards |
| OGN-068a | Caitlyn, Patrolling | Riftbound Promotional Cards |
| RAD-110a | Lulu, Whimsical | Riftbound Promotional Cards |
| OGN-111a | Heimerdinger, Inventor | Riftbound Promotional Cards |
| OGN-159a | Warwick, Hunter | Riftbound Promotional Cards |
| OGN-246b | Viktor, Leader | Riftbound Promotional Cards |
| VEN-069b | Mel, Newly Awakened | Organized Play promos |
| OGN-151b | Lee Sin, Centered | Organized Play promos |
| SFD-118a | Boneshiver | Organized Play promos |
| UNL-178a | Poppy, Defender of the Meek | Organized Play promos |
| OGN-263a | Teemo, Swift Scout | Worlds Bundle 2025 |
| OGN-276a | Aspirant's Climb | Worlds Bundle 2025 |
| OGN-278a | Bandle Tree | Worlds Bundle 2025 |
| OGN-293a | The Grand Plaza | Worlds Bundle 2025 |

Two more promos share their base card's number, so they wait on the ID decision:
- OGN-066 *Ahri, Alluring (Launch Exclusive)*;
- RAD-132 *Heimerdinger, Academy Instructor*.

Already in: OGN-007b, 042b, 089b, 126b, 166b, 214b (Nexus Night runes) and OGN-197b (Teemo, GG EZ).

## Signatures

Main-set signatures are done: OGN 12, SFD 12, UNL 12, VEN 9, RAD 15.

The 15 RAD ones reuse their overnumbered card's art, which is the same apart from the signature. Swap in the real art when there's a source.

Left:
- **RAD-176\*** (Jarvan IV, Exemplar of Demacia). It's in our data, but JustTCG doesn't list it (TCGplayer misnumbers it as 179\*), so it has no price.
- **T1 2025 Worlds Champion bundles**, 5 cards in each of three editions:
  - Player Bundle (`T1A 001–005`);
  - Signature Edition (`T1S 001–005`);
  - a serial-numbered Signature Edition.

  The cards are Ambessa, The Wolf; Xin Zhao, Vigilant; Galio, Indefatigable; Miss Fortune, Buccaneer; Seraphine, Not Alone. They need new set codes (e.g. `T1A`, `T1S`) added to `RiftSet` and the web set list.

## Alt-art runes

- **VEN-R01a–R06a**: in our data, but showing the standard rune art as a placeholder. They need real images.
- **Unleashed runes (18 cards)**: the gallery lists no Unleashed runes at all. They are:
  - base UNL-R01–R06;
  - alt art UNL-R01a–R06a;
  - UNL-R01b–R06b.

  JustTCG already prices some of them, e.g. UNL-R02a and UNL-R05b.
- **SFD-R01–R06** (base Spiritforged runes) are on TCGplayer but not in our data. We have only the SFD-R0Xa/b extras.

## Nexus Night runes

- Origins: done (OGN-007b, 042b, 089b, 126b, 166b, 214b).
- Spiritforged: SFD-R01b–R06b are in (artist 华锐). Check that these are the Nexus Night printings.
- **Vendetta**: R01b–R06b, listed as "Fury Rune (Vendetta)" etc. in the Organized Play promos.
- **R01c–R06c**: also in the Organized Play promos. TCGplayer doesn't say which set they're from; check before choosing IDs.

## Nexus Night promo cards

The Organized Play promo set has about 143 Nexus Night-style promos across every set:
- `/024` OGS
- `/298` OGN
- `/221` SFD
- `/219` UNL
- `/166` VEN
- `/167` RAD

Nearly all share their base card's number, so they wait on the ID decision (blocker 2). TCGplayer also sells a *Nexus Night Promo Pack* for OGN, SFD, UNL and VEN, which shows which cards came in each pack.

Similar small sets:
- **Judge promos:** SFD-059 Svellsongur, VEN-086 Gangplank, UNL-087 Blue Sentinel, OGN-111 Heimerdinger.
- **Secret Garden:** Lillia, Bashful Bloom; Ivern, Green Father; Ultrasoft Poro (numbered `001–003/003`).

## Skirmish Top 8 and Champion promos

Same number as the base card, so they wait on the ID decision.

| Base card | Name | Top 8 | Champion |
|---|---|---|---|
| SFD-022 | Long Sword | ✓ | |
| SFD-051 | Guardian Angel | ✓ | ✓ |
| SFD-139 | Edge of Night | ✓ | ✓ |
| VEN-109 | Illaoi, Prophet of the Great Kraken | ✓ | ✓ |
| VEN-134 | Kayle, Justified | ✓ | ✓ |
| UNL-058 | Lillia, Protector of Dreams | | ✓ |
| UNL-120 | Rengar, Trophy Hunter | | ✓ |

## Also spotted

- **Metal cards:** about 86, in "Prize Wall" and "Best Of" versions. They are legends and champions from every set, e.g. *Kai'Sa, Daughter of the Void (Metal)*, and they share the base number.
- **Legacy:** a new set on TCGplayer, with only sealed products listed so far. The weekly card data workflow picks it up when the gallery adds it. Then update:
  - `SETS` and `SET_NUMBERS` in `scripts/update_prices.py`;
  - `RiftSet` in the Android app.
- **Oversized battlefields:** OGS-279, 288, 294 and 296, and the Worlds Bundle ones. Probably not worth tracking.
