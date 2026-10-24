# Expense Tracker

Personal expense-tracking Android app built to the **Requirements Specification v1.6**.
Single user, single device, fully offline, dark mode only.

## Tech stack
- Kotlin + Jetpack Compose
- Material 3 (dark / pure-black OLED theme)
- Room database (local only, no network permission)
- Min Android 14 (API 34), target SDK 35

## Features
- Transactions: add/edit/delete, search, filter by direction / category / note text
- Per-ledger categories (user-choosable colors) and transaction templates with optional prefills
- Home ledger + multiple trip ledgers, each with its own currency
- Monthly overview: totals, last-4 transactions, expenses donut with tappable legend
- Transactions view: month mode / all mode
- Cumulative balance table (home ledger only, trips excluded)
- Embedded calculator modal in the amount field (+ - × ÷ parentheses %)
- CSV export (home date range / whole trip / all ledgers lifetime / categories / templates / trips)
- CSV import with pre-check, error report, all-or-nothing validation, append/replace modes
- Full JSON backup & atomic Strict-Replace restore with pre-check screen
- State restoration (last tab + last ledger; month resets to current)

## Build & install via GitHub Actions (recommended)

1. Push this project to a GitHub repository (see instructions below).
2. The workflow `.github/workflows/android-build.yml` runs automatically on push.
3. Open the repository's **Actions** tab → click the latest **Build Android APK** run.
4. Download the **expense-tracker-debug-apk** artifact and unzip it.
5. Transfer `app-debug.apk` to your Android 14+ phone and install it
   (enable "Install unknown apps" for your browser or file manager first).

## Build locally

```bash
git clone <your-repo>
cd ExpenseTracker
gradle assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/java/com/expensetracker/app/
├── MainActivity.kt
├── data/         # Room entities, DAOs, database, repository
├── util/         # Currencies, CSV, JSON backup, calculator evaluator
└── ui/           # Compose screens, components, view model, theme
```

## Privacy
- No permissions, no network, no analytics, no accounts.
- All data stays on-device in a Room database.
- Screenshots are allowed on every screen (FLAG_SECURE is NOT set).
