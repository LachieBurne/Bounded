<img src="Android App/app/src/main/ic_launcher-playstore.png" alt="Bounded icon" width="96" align="right">

# Bounded

Bounded is a collection tracker and deck builder for the Riftbound trading card game, with an Android app and a web app. Both use the same Google account, so your collection and decks stay in sync between them.

- Web app: [bounded.web.app](https://bounded.web.app)
- Android app: [riftbound-app.apk](https://github.com/LachieBurne/Bounded/blob/main/Android%20App/riftbound-app.apk) (an older build from April 2026)
- Bugs and requests: [Issues](https://github.com/LachieBurne/Bounded/issues)

## Features

| | Android | Web |
|---|:---:|:---:|
| Card collection with quantities, search and filters (set, domain, type, rarity, cost, might) | Yes | Yes |
| Binders: track completion of a set or any filtered group of cards | Yes | Yes |
| Card prices from JustTCG, shown in your local currency | Yes | Yes |
| Guided deck builder: Legend, Champion, Battlefields, Runes, main deck, sideboard | Yes | Yes |
| Deck rule checks, including banned cards | Yes | Yes |
| Deck stats: energy and might curves, deck value, cost of missing cards | Yes | Yes |
| Import and export decklists as text | Yes | Yes |
| Add cards by scanning them with the camera | Yes | No |

The deck rules the apps enforce are listed in [docs/deck-rules.md](docs/deck-rules.md). Binders are views of one collection, so a card is never counted twice; see [docs/binders.md](docs/binders.md).

## Installing

**Web:** open [bounded.web.app](https://bounded.web.app) and sign in with Google.

**Android:** download the APK above on your phone and open it. Android asks you to allow installing apps from your browser or file manager the first time.

## Architecture

| Part | Technology |
|---|---|
| Android app | Kotlin, Jetpack Compose, Room (card cache), CameraX and ML Kit text recognition (scanner) |
| Web app | React, TypeScript, Vite, Tailwind CSS, hosted on Firebase Hosting |
| Accounts and user data | Firebase Authentication (Google sign-in) and Cloud Firestore |
| Card images, icons and prices | Static files served from GitHub Pages |
| Card data | `riftbound_data.json`, generated from the official card gallery and bundled with both apps |
| Price updates | Python script run daily by GitHub Actions against the JustTCG API |

## Repository layout

```
Android App/    Android app (open this folder in Android Studio)
Web App/        Web app
scripts/        Card data and price scripts
data/           Hand-maintained card data: extra cards, overrides, bans, price state
shared/         Test cases run by both apps (deck rules, decklist import)
docs/           Project documentation
card_images/, domain_images/, rarity_images/, prices.json
                Published to GitHub Pages and loaded by both apps. Keep these paths stable.
```

## Building from source

The Firebase config files are not in the repository. Download them from the Firebase console (project `riftbounded-d8951`).

### Android

1. Put `google-services.json` in `Android App/app/`.
2. Open `Android App/` in Android Studio, or build from the command line:

```bash
cd "Android App" && ./gradlew assembleDebug
```

Unit tests: `./gradlew testDebugUnitTest`.

### Web

1. Copy `Web App/.env.example` to `Web App/.env.local` and fill in the Firebase web config.
2. Install and run:

```bash
cd "Web App" && npm ci && npm run dev
```

Other scripts: `npm test`, `npm run lint`, `npm run build`.

## Card data

`scripts/generate_card_data.py` builds `riftbound_data.json` from the official [card gallery](https://playriftbound.com/en-us/card-gallery/), writes it to `Android App/app/src/main/assets/` and `Web App/public/`, and saves any missing card images to `card_images/<cardId>.webp`.

```bash
pip install -r scripts/requirements.txt
python scripts/generate_card_data.py          # fetch the gallery, update data and images
python scripts/generate_card_data.py --check  # validate the committed data (CI runs this)
```

| File | Purpose |
|---|---|
| `data/extra_cards.json` | Cards the gallery doesn't list (promos, reprint variants) |
| `data/card_overrides.json` | Corrections to gallery data, such as a missing domain |
| `data/banned_cards.json` | Banned cards, by format |

Known missing cards are tracked in [docs/card-backlog.md](docs/card-backlog.md).

## Prices

`scripts/update_prices.py` runs daily and commits `prices.json` and `data/price_state.json`. It needs the `JUSTTCG_API_KEY` repository secret.

The JustTCG free tier allows 1,000 requests a month. Each run makes at most 30, spent on the cards that are due:

| Cards | Refreshed every |
|---|---|
| Showcase, or any card worth $20 or more | 2 days |
| Epic | 4 days |
| Rare | 7 days |
| Uncommon, Common | 14 days |

Each set is also re-crawled every 30 days, or straight away when it's new, to pick up cards JustTCG has added. This comes to about 500 requests a month. The intervals are set by `REFRESH_DAYS` at the top of the script.

```bash
python scripts/update_prices.py --plan                # show what's due and the monthly cost, without calling the API
JUSTTCG_API_KEY=... python scripts/update_prices.py --budget 30
```

## Automation

| Workflow | Runs | Does |
|---|---|---|
| Checks | Pull requests and merges to `main` | Builds and tests both apps, validates card data |
| Deploy Web App | Merges to `main` that touch `Web App/` | Deploys the web app to Firebase Hosting |
| Deploy Pages | Changes to images or `prices.json` | Publishes the static files to GitHub Pages |
| Update Card Prices | Daily at 00:30 UTC | Refreshes prices, then starts Deploy Pages if they changed |
| Update Card Data | Weekly, Sunday 06:00 UTC | Regenerates card data and opens a pull request if it changed |

Setup for the deploy workflows is in [docs/deploying.md](docs/deploying.md).

## Documentation

- [docs/deck-rules.md](docs/deck-rules.md): deck construction rules
- [docs/binders.md](docs/binders.md): how binders work
- [docs/deploying.md](docs/deploying.md): deploying the web app and GitHub Pages
- [docs/card-backlog.md](docs/card-backlog.md): cards not yet in the data
- [docs/tech-debt.md](docs/tech-debt.md): known issues and deferred work
