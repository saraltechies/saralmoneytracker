# Saral Money Tracker

A simple, private income and expense tracker for Android by [Saral Techies](https://saraltechies.com/).
See where your money goes day by day, week by week and month by month, keep to a budget, and never miss a repeating bill.
Free and open source (GPL-3.0). No ads, no account, and no internet permission: your money data never leaves your phone.

<!-- Add the Play badge once the listing is live:
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.saraltechies.saralmoneytracker)
-->

## Features (1.0)

- **Fast entry**: an on-screen keypad that also adds up sums (`250+120+45`), a category grid, today/yesterday chips, and
  the last used account picked for you. Expense, income or **transfer**.
- **Transfers are not spending.** Paying a credit card bill, withdrawing cash or moving money to savings moves money between
  your own accounts, so it never inflates your expenses. Card purchases are counted once, when you make them.
- **Day, week, month, year or any date range**, with previous/next arrows. Months can follow your salary cycle
  (for example 25th to 24th) and weeks can start on Monday, Sunday or Saturday.
- **Home**: this month's spending, income and savings, today's and this week's spending, total balance, budget status with a
  safe amount to spend per day, top categories, what's coming up and recent entries.
- **Stats**: donut chart by category, a trend chart (daily or monthly bars), per-day average, comparison with the previous
  period and your savings rate. Tap a category to see its entries.
- **Budgets**: a monthly limit per category, warnings at 80% and when over, and the total monthly budget.
- **Accounts**: cash, bank, credit card, wallet/UPI with opening balances, live balances and net worth (what you have minus
  what you owe). Each account has its own history.
- **Repeating entries**: salary, rent, EMIs, SIPs and subscriptions are added on their dates by themselves (daily, weekly,
  monthly, yearly; a rule started on the 31st lands on the last day of shorter months).
- **Search** every note and category; filter by expense, income or transfer.
- **Excel import**: bring in a hand-made tracker (monthly "Money Out / Money In" sheets), any table with Date and Amount
  columns, or a bank statement with Debit/Credit columns. Copied rows are skipped, "Credit Card" bill payments can be turned
  into transfers, and a summary sheet's planned amounts can become budgets. Importing the same file twice is safe.
- **Excel export** that doubles as a full backup (accounts, categories with budgets, repeating entries, every entry), and CSV.
- **App lock** with the phone's fingerprint, face or screen lock; optional **hide in recent apps** (blocks screenshots).
- **Daily reminder** in the evening, only on days nothing was added.
- 29 currencies, Indian number grouping for rupees (₹1,25,000), editable categories with 47 icons and 20 colours,
  light/dark theme, launcher shortcuts (Add expense, Add income, Transfer).
- Google Play rating and in-app update prompts (through the Play Store app, so still no internet permission).

## Permissions

| Permission | Why |
| --- | --- |
| `POST_NOTIFICATIONS` | The optional daily reminder. Asked for only when you turn it on. |
| `RECEIVE_BOOT_COMPLETED` | Sets the reminder again after the phone restarts. |
| `USE_BIOMETRIC` | The optional app lock. |

There is no `INTERNET` permission; the manifest removes it in case a library asks, and the release build fails if it appears.
Import and export go through Android's file picker, so the app needs no storage permission.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · DataStore · plain SQLite · AndroidX Biometric.
The Excel reader and writer are a few hundred lines on top of `java.util.zip` and the JDK's XML parser, with no extra library.
minSdk 26 (Android 8.0), targetSdk 36, compileSdk 37.

```
app/src/main/java/com/saraltechies/saralmoneytracker/
├── model/   # Money (hundredths, formatting), periods, stats and budgets, repeating dates (pure JVM, unit-tested)
├── xlsx/    # .xlsx writer and reader, spreadsheet import, backup/export format (pure JVM, unit-tested)
├── data/    # SQLite database and repository, DataStore settings, review policy
├── ui/      # Compose: home, entries, add/edit with keypad, stats, budgets, accounts, settings, import, lock
└── util/    # App lock, daily reminder, launcher shortcuts, Play Store links and updates
```

## Build

Requirements: JDK 17 and the Android SDK (open this folder in Android Studio).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

Every push and pull request runs the tests and builds a debug APK on GitHub Actions (download it from the run's **Artifacts**).

## Release (maintainers)

1. Raise `versionCode` (+1) and `versionName` in `app/build.gradle.kts` and push to `main`.
2. Push a tag `vX.Y.Z`, or run `gh workflow run android.yml -f release=true`. The workflow signs the build with the upload key stored in the repository secrets (`SARALMT_KEYSTORE_BASE64`, `SARALMT_KEYSTORE_PASSWORD`, `SARALMT_KEY_ALIAS`, `SARALMT_KEY_PASSWORD`).
3. Upload `app-release.aab` and `mapping.txt` from the run's artifacts to Play Console.

Store listing text, graphics, the privacy policy and the Play Console answers are in [`store/`](store).

## Contributing

Bug reports, ideas and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md), and report security problems privately as described in [SECURITY.md](SECURITY.md).

## License

Copyright © 2026 Saral Techies.

Saral Money Tracker is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation, version 3 or (at your option) any later version. See [LICENSE](LICENSE).

The "Saral Money Tracker" and "Saral Techies" names and the app icon are trademarks of Saral Techies and aren't covered by the license.
If you publish a modified version, please give it a different name and icon.
