# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"Dont Broke" — an Android personal-finance tracker for Rupiah. Multiple wallets
(cash, bank, e-wallet, e-money), per-account balances, spend windows, an analytics
dashboard, and an optional Hugging Face-backed insight card.

Kotlin · Jetpack Compose (Material 3) · MVVM · Room · Hilt · Retrofit · Coroutines
minSdk 24 · targetSdk 36 · compileSdk 37 · JDK 11

## Commands

```bash
./gradlew assembleDebug          # build
./gradlew testDebugUnitTest      # all JVM unit tests
./gradlew lint                   # Android lint

# one class / one method
./gradlew testDebugUnitTest --tests '*.AnalyticsSqlTest'
./gradlew testDebugUnitTest --tests '*.SpendWindowTest.weekStartsMonday'
```

`local.properties` is git-ignored and required for a command-line build
(`sdk.dir=/path/to/Android/Sdk`). `HF_API_KEY` is optional — the app builds
and runs without it; the insight card just reports it could not load.

AGP 8.13.2 is only tested to compileSdk 36.1, so every build prints a loud
compileSdk-37 warning. It is noise, not a failure.

## Architecture

`data → domain → presentation`, wired by Hilt (`di/AppModule.kt`). Everything is
cold `Flow` from the DAO up; ViewModels expose a single `StateFlow<UiState>`.

**Balances are derived, never incremented.** The whole balance story is one SQL
constant, `RECALC_ALL_BALANCES` in `data/local/dao/TransactionDao.kt`:
`initialBalance + Σ(signed transactions matching this account by name)`. Writes go
through `save()` / `deleteAndRecalc()`, which are `@Transaction` methods that
recompute the affected account(s) in the same DB transaction — an edit that moves
a row between accounts rebalances both. There is no delta bookkeeping to get
wrong, so never "adjust" a balance; change the rows and recalc.

Note the join key: transactions point at accounts by **name** (`sourceOrAccount`),
not by id. Renaming an account without migrating that column silently orphans its
transactions.

**Aggregate SQL lives in named constants**, not inline in `@Query`, so JVM tests
can run the production statement verbatim against real SQLite
(`EXPENSE_BY_CATEGORY`, `EXPENSE_BY_BUCKET`, `MONTH_TOTALS`). If you change one of
these queries, `AnalyticsSqlTest` / `AccountBalanceSqlTest` execute the same string
— keep them constants.

**Time windows are half-open `[from, until)` everywhere** — SQL, domain, tests.
`domain/model/AnalyticsTimeFrame.kt` owns every window helper (`todayWindow`,
`weekWindow`, `monthWindow`, `yearWindow`, `dayStart`). Weeks start **Monday**, walked
back explicitly because `Calendar.firstDayOfWeek` is locale-dependent.

The trend query buckets by fixed-width millisecond slices
(`(timestamp - :from) / :bucketMs`), which is exact only in a DST-free zone —
correct for Indonesia (WIB/WITA/WIT), wrong elsewhere. Empty slices produce no
row at all; `AnalyticsRepository.spendingTrend` pads the gaps.

**Money is `Long` whole rupiah** in the database (Rupiah has no cents). The
widening to `Double` in `AnalyticsRepository` is plain, with no scaling. Display
formatting uses `Locale("id","ID")` so separators agree with the currency beside
them.

### Room migrations

Currently **version 5**, with an unbroken 1→2→3→4→5 chain in
`data/database/AppDatabase.kt`; `exportSchema = false`. Schema-affecting changes
need a bumped version *and* a new `Migration` — including index-only changes, since
Room validates indices at startup (`MIGRATION_4_5` exists solely to create
`index_transactions_timestamp` declared by `@Entity(indices = ...)`).

Migration DDL must match Room's generated schema **exactly** or startup validation
throws; that is why `CREATE_ACCOUNTS` and friends are hand-written constants shared
between the migration and the tests.

## Tests

Plain JUnit 4 on the JVM — no Robolectric, no instrumentation for the logic. Two
kinds worth knowing:

- **SQL tests** (`data/`) run production SQL constants against `sqlite-jdbc`. DAO
  seams are hand-written fakes implementing `TransactionDao` with `TODO()` for the
  methods under test that never run — add your new method there when you extend the
  interface, or these stop compiling.
- **Date/time tests** pin `TimeZone.setDefault(Asia/Jakarta)` in `@Before` and
  restore it in `@After`, so window boundaries are machine-independent. Follow that
  pattern for anything touching calendars.

`ExampleUnitTest` / `ExampleInstrumentedTest` are template leftovers.
