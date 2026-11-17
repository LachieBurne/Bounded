# Card data backlog

Cards that exist but aren't in `riftbound_data.json` yet. The official gallery only lists main-set cards, so everything here goes in `data/extra_cards.json`.

These notes come from TCGplayer's product list (the source of JustTCG's data), checked 2026-11-08. Tokens are out of scope.

`python scripts/update_prices.py --plan` lists every card JustTCG prices that our data lacks. Use it to check this list against reality.

## Blockers that apply to most of this

1. **Images.**
   - The gallery has no images for these cards.
   - TCGplayer's image server refuses automated downloads.
   - DotGG (riftbound.gg's card database) has scans of most of them, by card ID: `https://static.dotgg.gg/riftbound/cards/<ID>.webp`, with promos as `<ID>-P.webp`. The full list, with names, is `https://api.dotgg.gg/cgfw/getcards?game=riftbound&mode=indexed&part=cards`.
   - To add a card's art: set `sourceImage` in the extras entry to the image's URL and `imageFile` to its ID, and the generator downloads it into `card_images/`. Or save it there by hand. Until then, an entry can borrow the base card's image.
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
| OGN-111a | Heimerdinger, Inventor | Riftbound Promotional Cards |
| OGN-159a | Warwick, Hunter | Riftbound Promotional Cards |
| OGN-246b | Viktor, Leader | Riftbound Promotional Cards |
| SFD-118a | Boneshiver | Organized Play promos |
| UNL-178a | Poppy, Defender of the Meek | Organized Play promos |
| OGN-263a | Teemo, Swift Scout | Worlds Bundle 2025 |
| OGN-276a | Aspirant's Climb | Worlds Bundle 2025 |
| OGN-278a | Bandle Tree | Worlds Bundle 2025 |
| OGN-293a | The Grand Plaza | Worlds Bundle 2025 |

Two more promos share their base card's number, so they wait on the ID decision:
- OGN-066 *Ahri, Alluring (Launch Exclusive)*;
- RAD-132 *Heimerdinger, Academy Instructor*.

Already in: OGN-007b, 042b, 089b, 126b, 166b, 214b (Nexus Night runes), OGN-197b (Teemo, GG EZ), and the Nexus Night alt arts OGN-151b (Lee Sin, Centered), VEN-069b (Mel, Newly Awakened) and RAD-110a (Lulu, Whimsical).

**Rengar, Pouncing (Unleashed Nexus Night):** an alt-art promo of SFD-025, not a finish. DotGG only has the Chinese printing (`SFD-025-P`). Add it as a card (e.g. `SFD-025a`, if that number is free) once there's an English image.

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

- Done: VEN-R01a–R06a (real art, Greg Ghielmetti) and UNL-R01a–R06a (Envar Studio), from DotGG.
- **Base Unleashed runes (UNL-R01–R06)**: the gallery lists no Unleashed runes at all, and these aren't in our data. DotGG has them.
- **SFD-R01–R06** (base Spiritforged runes) are on TCGplayer and DotGG but not in our data. We have only the SFD-R0Xa/b extras.

## Nexus Night runes

All done: Origins (OGN-007b and the other five), Spiritforged (SFD-R01b–R06b, 华锐), Unleashed (UNL-R01b–R06b, the same art as Spiritforged), Vendetta (VEN-R01b–R06b, Pixelverse) and Radiance (RAD-R01b–R06b, Jincheng Guo).

Not added:
- **Unleashed Regional promo runes, UNL-R01c–R06c.** DotGG has them as `UNL-R0Xc-P`.
- **Vendetta Chinese promo runes.** DotGG has them as `VEN-R0X-CN-P`.

## Nexus Night promo cards

Done: `data/promos.json` lists 113 cards with a Nexus Night promo, counted as a finish on the card's entry (`NEXUS_NIGHT`, see [binders.md](binders.md)): OGN 18, OGS 16, RAD 24, SFD 19, UNL 18, VEN 18. The apps only offer a Nexus Night version for those cards. Sources: DotGG's card database, riftbound.gg's promo list and TCGplayer (2026-11-17). Add new ones there as sets come out.

For reference, the Organized Play promo set has about 143 Nexus Night-style promos across every set:
- `/024` OGS
- `/298` OGN
- `/221` SFD
- `/219` UNL
- `/166` VEN
- `/167` RAD

Any Nexus Night promos with their own art still share their base card's number, so they wait on the ID decision (blocker 2). TCGplayer also sells a *Nexus Night Promo Pack* for OGN, SFD, UNL and VEN, which shows which cards came in each pack.

Similar small sets:
- **Judge promos:** SFD-059 Svellsongur, VEN-086 Gangplank, UNL-087 Blue Sentinel, OGN-111 Heimerdinger.
- **Secret Garden:** Lillia, Bashful Bloom; Ivern, Green Father; Ultrasoft Poro (numbered `001–003/003`).

## Promo scans to check again

`data/promos.json` has a checked scan for 96 of its 138 promos; the apps show it when that finish is picked. The rest have a `scanIssue` and show the PROMO badge instead. Look again when DotGG updates its images, or find another source; set `scan` to the DotGG ID once a scan passes, and re-run the generator.

What counts as passing (2026-11-17): a render or flat scan of the English promo printing, about 700 px wide or more, with its promo marking (the PROMO stamp, TOP 8 or CHAMPION on the art, or "P" after the collector number) and no glare.

**Radiance Nexus Night (24):** DotGG's images are pixel for pixel the standard cards, not the promos: RAD-002, RAD-008, RAD-014, RAD-019, RAD-027, RAD-029, RAD-037, RAD-041, RAD-048, RAD-054, RAD-055, RAD-066, RAD-070, RAD-076, RAD-083, RAD-087, RAD-094, RAD-096, RAD-105, RAD-107, RAD-119, RAD-122, RAD-127, RAD-130.

| Card | Name | Promo | Why not yet |
|---|---|---|---|
| OGN-034 | Tryndamere, Barbarian | Skirmish Participation | DotGG's image is the standard card, with no participation marking |
| OGN-067 | Blitzcrank, Impassive | Skirmish Top 8 | DotGG's image is the standard card, with no Top 8 marking |
| SFD-022 | Long Sword | Skirmish Participation | Photo of the card with glare, 255x361 |
| SFD-022 | Long Sword | Skirmish Top 8 | No scan on DotGG |
| SFD-051 | Guardian Angel | Skirmish Top 8 | Photo of the foil card with glare; washed-out text |
| SFD-051 | Guardian Angel | Skirmish Champion | Photo of the foil card with glare; washed-out text |
| SFD-086 | World Atlas | Skirmish Participation | Too small: 255x361 |
| SFD-102 | Hexdrinker | Nexus Night | No scan on DotGG |
| SFD-139 | Edge of Night | Skirmish Top 8 | Photo of the card, 255x361 |
| SFD-139 | Edge of Night | Skirmish Champion | Photo of the card, 255x361 |
| UNL-058 | Lillia, Protector of Dreams | Skirmish Top 8 | Photo of the foil card; faded colours |
| UNL-058 | Lillia, Protector of Dreams | Skirmish Champion | Photo of the foil card; faded colours |
| UNL-120 | Rengar, Trophy Hunter | Skirmish Top 8 | Photo of the foil card; faded colours |
| UNL-120 | Rengar, Trophy Hunter | Skirmish Champion | Photo of the foil card; faded colours |
| VEN-109 | Illaoi, Prophet of the Great Kraken | Skirmish Top 8 | No scan on DotGG |
| VEN-109 | Illaoi, Prophet of the Great Kraken | Skirmish Champion | No scan on DotGG |
| VEN-134 | Kayle, Justified | Skirmish Top 8 | No scan on DotGG |
| VEN-134 | Kayle, Justified | Skirmish Champion | No scan on DotGG |

## Summoner Skirmish promos

Done: Participation, Top 8 and Champion promos are finishes on the base card's entry (`SKIRMISH_PARTICIPATION`, `SKIRMISH_TOP_8`, `SKIRMISH_CHAMPION`), listed in `data/promos.json`. That covers 16 cards, from DotGG, riftbound.gg's promo list and TCGplayer's product list. Add new ones there as they're announced.

## Other promos, not added yet

From riftbound.gg's list (2026-11-17). Most share their base card's number, so as finishes they'd each need a new kind in `Finish` (both apps) and `PROMO_KINDS` in the generator. Ones with their own art, like the Arcane Box Set alt arts, are cards of their own and are listed under "Alternate-art promos" above.

| Base card | Card | Promo |
|---|---|---|
| VEN-041 | Riven Shattered | Spiritforged Pre-Rift |
| RAD-086 | K'Sante Courageous | Riftbound North American Regional Championship 2026 |
| ? | K'Sante Courageous Top 8 | Riftbound North American Regional Championship 2026 |
| ? | Bird // Brush | Vault Promo |
| UNL-178 | Poppy - Defender of the Meek | Unleashed Regionals |
| UNL-169 | Ashe - Focused | Pre-Rift Promo |
| UNL-087 | Blue Sentinel | Judge Promo |
| UNL-082 | Lillia - Fae Fawn | Chinese Unleashed Regional Open |
| SFD-178 | Blade of the Ruined King | Regional Qualifier Bologna Champion Promo |
| SFD-178 | Blade of the Ruined King | Regional Qualifier Bologna Top 8 Promo |
| SFD-118 | Boneshiver | Regional Qualifier Bologna Participation Promo |
| SFD-084 | Jayce - Man of Progress | Evo 2026 Promo |
| SFD-059 | Svellsongur | Judge Promo |
| OGS-011 | Flash | LGS Play Promo |
| OGN-275 | Altar to Unity | Teamfight Tactics Paris Open Promo |
| ? | Recruit//Buff | TFT Promo |
| OGN-066 | Ahri - Alluring | Launch Event Promo |
| OGN-255 | Ahri - Nine-Tailed Fox | Origins Release Event Promo |
| OGN-276 | Aspirant's Climb | Worlds Bundle 2025 Promo |
| OGN-278 | Bandle Tree | Worlds Bundle 2025 Promo |
| UNL-T04 | Buff | Riot Fist Bump Promo |
| OGN-068 | Caitlyn - Patrolling | Arcane Box Set Promo |
| OGN-068 | Caitlyn - Patrolling | Chinese Arcane Box Set Promo |
| OGN-253 | Darius, Hand of Noxus | Origins Release Event Promo |
| OGN-111 | Heimerdinger - Inventor | Arcane Box Set Promo |
| OGN-111 | Heimerdinger - Inventor | Chinese Arcane Box Set Promo |
| OGN-111 | Heimerdinger - Inventor | Judge Promo |
| SFD-195 | Irelia - Blade Dancer | Lunar Revel Bundle 2026 |
| OGN-251 | Jinx - Loose Cannon | Origins Release Event Promo |
| OGN-202 | Jinx - Rebel | Arcane Box Set Promo |
| OGN-202 | Jinx - Rebel | Chinese Arcane Box Set Promo |
| OGN-202 | Jinx - Rebel | Origins Release Event Promo |
| OGN-247 | Kai'Sa, Daughter of the Void | Origins Release Event Promo |
| OGN-078 | Lee Sin - Ascetic | Origins Release Event Promo |
| OGN-257 | Lee Sin - Blind Monk | Origins Release Event Promo |
| OGN-261 | Leona - Radiant Dawn | Origins Release Event Promo |
| OGN-267 | Miss Fortune - Bounty Hunter | Origins Release Event Promo |
| OGN-193 | Miss Fortune - Buccaneer | Regional Qualifier |
| OGN-193 | Miss Fortune - Buccaneer | Guangzhou Prestige Battle Bunny |
| OGN-269 | Sett - The Boss | Origins Release Event Promo |
| OGN-197 | Teemo - Scout | Origins Store Demo Promo |
| OGN-263 | Teemo - Swift Scout | Origins Release Event Promo |
| OGN-263 | Teemo - Swift Scout | Worlds Bundle 2025 Promo |
| OGN-293 | The Grand Plaza | Worlds Bundle 2025 Promo |
| OGN-036 | Vi - Destructive | Arcane Box Set Promo |
| OGN-036 | Vi - Destructive | Chinese Arcane Box Set Promo |
| OGN-265 | Viktor - Herald of the Arcane | Origins Release Event Promo |
| OGN-246 | Viktor - Leader | Arcane Box Set Promo |
| OGN-246 | Viktor - Leader | Chinese Arcane Box Set Promo |
| OGN-249 | Volibear - Relentless Storm | Origins Release Event Promo |
| OGN-159 | Warwick - Hunter | Arcane Box Set Promo |
| OGN-159 | Warwick - Hunter | Chinese Arcane Box Set Promo |
| OGN-259 | Yasuo - Unforgiven | Origins Release Event Promo |
| SFD-116 | Yone - Blademaster | Pre-Rift Promo |

## Also spotted

- **Metal cards:** about 86, in "Prize Wall" and "Best Of" versions. They are legends and champions from every set, e.g. *Kai'Sa, Daughter of the Void (Metal)*, and they share the base number.
- **Legacy:** a new set on TCGplayer, with only sealed products listed so far. The weekly card data workflow picks it up when the gallery adds it. Then update:
  - `SETS` and `SET_NUMBERS` in `scripts/update_prices.py`;
  - `RiftSet` in the Android app.
- **Oversized battlefields:** OGS-279, 288, 294 and 296, and the Worlds Bundle ones. Probably not worth tracking.
