# Dont Broke

An Android personal-finance tracker for keeping an eye on where the money actually
goes. Built for Rupiah: multiple wallets (cash, bank, e-wallet, e-money), per-account
balances, and a spend counter for today, this week, and this month.

## Features

- **Your own accounts.** Nothing is pre-seeded — you add the wallets you actually
  use, each with the balance it holds right now.
- **Income and expenses** with category, date, optional location and note.
- **Real per-account balances.** `initialBalance + income − expenses`, recomputed
  from history rather than nudged by deltas, so the numbers cannot drift.
- **Spend summary** — today, this week (Monday–Sunday), and this month.
- **Analytics dashboard** — category breakdown ring, weekly spending trend bars,
  income vs expense for the selected timeframe (day/week/month/year/custom).
- **Transaction history** — filtered by timeframe, category, account, or location,
  grouped by day with daily net balances.
- **Swipe gestures** — swipe right to edit, swipe left to delete (with confirmation).
- **AI insight** — an optional one-paragraph read on your spending, via Gemini.

## Build

Requires JDK 11+ and the Android SDK (API 37).

```bash
git clone https://github.com/BangKumish/dontbroke.git
cd dontbroke
./gradlew assembleDebug
```

`local.properties` is not in the repo. Android Studio writes `sdk.dir` for you on
first open; from the command line, create it yourself:

```properties
sdk.dir=/path/to/Android/Sdk
```

### AI insight (optional)

The app builds and runs fine without a key — the insight card just reports that it
could not load. To enable it, add your [Gemini API key](https://aistudio.google.com/apikey)
to `local.properties` (git-ignored) or export it:

```properties
GEMINI_API_KEY=your-key-here
```

```bash
export GEMINI_API_KEY=your-key-here
```

The key is injected as a `BuildConfig` field at build time and is never committed.
Note that it does ship inside the APK, so treat it as a personal-use key — don't
publish a build with a key you care about.

## Tests

```bash
./gradlew testDebugUnitTest
```

79 tests covering balance arithmetic, spend-window boundaries, analytics queries,
timeframe stepping, day grouping, and chart logic. `AccountBalanceSqlTest` and
`AnalyticsSqlTest` run the production SQL against real SQLite on the JVM; the
window and stepping tests pin date/time in Asia/Jakarta so boundaries are
machine-independent.

## Stack

Kotlin · Jetpack Compose (Material 3) · MVVM · Room · Hilt · Retrofit · Coroutines

```
data/        Room entities, DAOs, migrations, repositories
domain/      plain models + use cases
presentation/  Compose screens + ViewModels
network/     Gemini API
di/          Hilt modules
```

minSdk 24 · targetSdk 36 · compileSdk 37

## Database

Room, currently at version 5, with migrations from v1 — upgrading an existing
install keeps its transactions. The v4 migration cleared the accounts older builds
pre-seeded, but only ones left untouched: any account you gave a starting balance
or already recorded a transaction against stayed put.

## License

[MIT](LICENSE) © 2026 Andreas Sihotang
