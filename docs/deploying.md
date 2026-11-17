# Deploying the web app

`.github/workflows/deploy_web.yml` builds `Web App/` and deploys it to Firebase Hosting (site `bounded`, project `riftbounded-d8951`, served at [bounded.web.app](https://bounded.web.app)). It runs when a change under `Web App/` reaches `main`, which includes merged card data updates, since `riftbound_data.json` lives in `Web App/public/`. You can also run it by hand from the Actions tab (Deploy Web App › Run workflow).

The workflow runs the tests and builds before it deploys, so a broken build never goes live. Pull requests aren't deployed: `checks.yml` already builds and tests them, and Google sign-in only works on authorised domains, so a preview link couldn't sign in.

Only the hosting site is deployed. Firestore rules (`Web App/firestore.rules`) change rarely and are deployed by hand: `firebase deploy --only firestore:rules` from `Web App/`.

## One-time setup

The workflow needs four repository secrets. Until they're set it stops at its first step and names the ones that are missing.

### 1. A service account that can deploy

Let the Firebase CLI create it. It makes a service account with permission to deploy to Hosting and saves the account's key straight to the repository as a secret, so you never handle the key file.

```bash
cd "Web App"
npx firebase-tools login
npx firebase-tools init hosting:github
```

- Repository: `LachieBurne/Bounded`.
- "Set up the workflow to run a build script before every deploy?": **No**.
- "Set up automatic deployment to your site's live channel when a PR is merged?": **No**.

The CLI writes a `firebase-hosting-pull-request.yml` (and maybe a merge workflow) into `Web App/.github/workflows/`. Delete them, because `deploy_web.yml` replaces them. The secret it creates is `FIREBASE_SERVICE_ACCOUNT_RIFTBOUNDED_D8951`, which is the name the workflow reads. If the CLI prints a different name, change the two references in `deploy_web.yml` to match; GitHub doesn't let you rename a secret or read its value.

### 2. The web app's Firebase config

Vite builds the Firebase web config into the app. Three of its values aren't in `.env.example`, so they're secrets: `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_MESSAGING_SENDER_ID` and `VITE_FIREBASE_APP_ID`. `gh` can set them straight from your `.env.local`:

```bash
gh secret set -f "Web App/.env.local"
```

That sets every value in the file as a secret. The extra ones are harmless. The workflow takes the auth domain, project ID and storage bucket from the workflow file itself.

### 3. Check it

Run Deploy Web App from the Actions tab. The run's summary links to bounded.web.app when it finishes.

## Deploying by hand

From `Web App/`, with `.env.local` filled in and the Firebase CLI logged in:

```bash
npm run build && npx firebase-tools deploy --only hosting
```

# Card images and prices (GitHub Pages)

Both apps load card images, domain and rarity icons and `prices.json` from [lachieburne.github.io/Bounded](https://lachieburne.github.io/Bounded/). `.github/workflows/deploy_pages.yml` publishes just those files, so not the whole repository. It runs when one of them changes on `main`, or by hand from the Actions tab.

- **Card data updates** are merged pull requests, so their new images publish when you merge.
- **The daily price job** pushes with `GITHUB_TOKEN`, which doesn't trigger push workflows, so it starts the Pages workflow itself, and only when prices changed.

The repository's Pages source must be **GitHub Actions** (Settings › Pages › Build and deployment). With "Deploy from a branch", GitHub would go back to publishing the whole repository on every push.

The site is about 160 MB of WebP card images. The 677 old PNG card images were removed in November 2026 along with the April 2026 APK that loaded them, so that build no longer shows card art.

# Android app download (GitHub Releases)

The README links `releases/latest/download/Bounded.apk`, so it always gets the newest release. To publish one:

1. Bump `versionCode` and `versionName` in `Android App/app/build.gradle.kts`.
2. Build on `main`: `./gradlew assembleDebug` in `Android App/`. Leave out `-Pandroid.injected.build.abi` so the APK covers every phone.
3. `gh release create v<version> --target main --title "Bounded <version>" "Android App/app/build/outputs/apk/debug/app-debug.apk#Bounded.apk"`, renaming the asset to `Bounded.apk`.

Builds are signed with the debug key of the machine that builds them, so one built on a different machine won't install over the last; people have to uninstall first. A release signing key would fix that (docs/tech-debt.md).
