# Saral Money Tracker

A simple, private income and expense tracker for Android by [Saral Techies](https://saraltechies.com/).
See where your money goes day by day, week by week and month by month, keep to a budget, and never miss a repeating bill.
Free and open source (GPL-3.0). No ads, no account, and no internet permission: your money data never leaves your phone.

<!-- Add the Play badge once the listing is live:
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="80">](https://play.google.com/store/apps/details?id=com.saraltechies.saralmoneytracker)
-->

## Features (1.6)

- **Fast entry**: an on-screen keypad that also adds up sums (`250+120+45`), your most used categories first (with **More**
  and a search for the rest), today/yesterday chips, and
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
- **Lent & borrowed**: money given to or taken from friends and family, on credit or on interest (per month or per year,
  simple interest on what is still owed), paid back any time or as a monthly EMI. Shows what each person still owes,
  interest due, the EMI plan with paid and late instalments, and what is overdue; a payment is split into interest and
  principal for you. Each loan has its own account, so balances and net worth stay right and only the interest counts as
  income or spending. A reminder on the day a payment is due.
- **Contacts and PDF statements**: each loan is with a person, bank, finance company or vendor, with phone, email, address,
  PAN/GSTIN, bank account, IFSC, UPI ID and a loan reference (name and number can be picked from the phone's contacts,
  with no contacts permission). Add **your details** once, and send a PDF statement: both parties, terms, a summary, every
  entry with the running balance, the EMI plan, and how to pay with a UPI scan-to-pay code. Whole loan, a financial year
  or any dates; share it (the email is filled in for mail apps) or save it.
- **Personal and business books**: keep a business (or several) apart from your own money, each with its own accounts,
  categories, budgets, loans and details; switch from the top of Home or see all books together. A business bill paid from a
  personal account counts as a business expense; money moved between books shows as capital put in or drawings, never
  income or spending. A business book starts with business categories (sales, purchases, rent, salaries, GST…).
- **Profit and loss report** (PDF) for a business, or income and expenses for a personal book: by category, profit,
  capital and drawings, account balances and loans, for any period or financial year.
- **Credit ledger** for customers and suppliers on credit: You gave / You got in two taps, a running balance for each,
  goods on credit counted as a sale or purchase once, old balances, payment reminders by WhatsApp or SMS with your UPI
  ID, and a PDF account statement with a UPI scan-to-pay code.
- **Due dates in the ledger**: a credit period per customer or supplier (7 to 60 days). Payments settle the oldest bills
  first; each person shows what is overdue and since when, and the next amount due, with an **Overdue** filter. Entries can
  repeat weekly or monthly (rent from a tenant, a monthly milk bill).
- **Cash flow**: the expected balance at month end, or in 30 or 90 days, from repeating income and bills, loan EMIs and
  interest, and ledger bills as they fall due, with the balance after each and the lowest point.
- **Home-screen widget** (spent today and this month, with Add expense / Add income) and a **quick-settings tile** to add
  an expense from anywhere.
- **Search** every note and category; filter by expense, income or transfer.
- **Excel import**: bring in a hand-made tracker (monthly "Money Out / Money In" sheets), any table with Date and Amount
  columns, or a bank statement with Debit/Credit columns. Copied rows are skipped, "Credit Card" bill payments can be turned
  into transfers, and a summary sheet's planned amounts can become budgets. Importing the same file twice is safe.
- **Excel export** that doubles as a full backup (accounts, categories with budgets, repeating entries, loans, every entry), and CSV.
- **Automatic backup**, daily or weekly, into a folder you pick (on the phone or a Google Drive folder), keeping the newest 7.
  No internet or storage permission: Android's folder picker grants access to that one folder.
- **First-run setup** (personal, business or both) and a **What's new** sheet for people updating.
- **App lock** with the phone's fingerprint, face or screen lock; optional **hide in recent apps** (blocks screenshots).
- **Daily reminder** in the evening on days nothing was added.
- **Morning alerts** at 9 am: bills coming up (a repeating entry can remind you 1 to 7 days before), and loan or ledger
  payments due today. A **monthly summary** on the first morning of each month. Both can be turned off.
- 29 currencies, Indian number grouping for rupees (₹1,25,000), editable categories with 49 icons and 20 colours,
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
├── model/   # Money (hundredths, formatting), periods, stats and budgets, repeating dates, loan interest and EMIs (pure JVM, unit-tested)
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
