# Play Console answers for Saral Money Tracker

Answers for every declaration in **Policy → App content**, plus the release setup.
They match the code as of v1.5.0: no ads, no analytics, no crash reporting, and **no INTERNET permission**
(the manifest removes it and the release workflow fails if it appears).
**Re-check them whenever you add an SDK or a permission.**

## Create app
- Developer account: **Saral Techies Official** (organization)
- App name: `Saral Money Tracker`
- Default language: English (India) – en-IN
- App or game: **App**
- Free or paid: **Free**

## App access
- **All functionality is available without special access** (no login).

## Ads
- **No, my app does not contain ads.**

## Content rating (IARC questionnaire)
- Category: **All other app types**
- Violence, sexuality, language, controlled substances, gambling: **No** to all
- Users interact or exchange content: **No**. Shares location: **No**. Digital purchases: **No**
- Unrestricted internet access / web browser: **No**
- Expected result: Everyone / PEGI 3 / IARC 3+

## Target audience and content
- Target age groups: **18 and over** (a personal finance app; do NOT select under 13)
- Could the app unintentionally appeal to children? **No**

## News app
- **No**

## Data safety
- Does your app collect or share any of the required user data types? **No**
  - Reason: everything entered (amounts, notes, accounts) is stored only on the phone, and the app has no internet permission,
    so nothing can be sent off the device. Import/export only touch the file the user picks.
- Is all user data encrypted in transit? Not applicable. If the form forces an answer, choose **Yes**.
- Deletion request: not applicable; Settings → Delete all data, or uninstalling, deletes everything.
- Result shown on the store: "No data collected · No data shared with third parties"

## Government apps
- **No**

## Financial features
- The app is a **personal budgeting / expense tracking** tool. It offers no banking, loans, payments, investment, trading,
  crypto or insurance services, does not connect to bank accounts and does not move money.
- Lent & borrowed (1.1) only records money the user gave to or took from people they know, and works out interest and
  EMIs for their own reference. The app doesn't lend, arrange or collect loans, so this is still not a loan feature.
- In the declaration, choose the option for apps that **don't provide any of the listed financial features**. If the form asks
  specifically about budgeting tools, select **Personal finance management / budgeting** (no regulated services).

## Health apps
- **None**

## Permissions
No sensitive-permission declarations are needed: notifications, run at startup and biometrics are normal permissions.
Picking a contact for a loan uses the system picker (`ACTION_PICK`), which shares only the chosen entry; there is no
`READ_CONTACTS` permission. Contact details and statements stay on the phone and leave it only when the user shares a PDF.
The app does not use SMS, call log, location, contacts or all-files access.

## Store settings
- Category: **Finance**
- Contact email: support@saraltechies.com
- Website: https://saraltechies.com/

## Release
- Countries: **all countries**
- Track: **Production**
- Play App Signing: **use Google-generated app signing key**. Upload `app-release.aab`, signed with our upload key `saralmoneytracker-upload`.
- Upload key SHA-256 (for reference):
  `C6:3B:B0:55:85:E0:D3:84:DF:58:25:64:36:B6:28:06:C0:25:33:6B:3B:23:56:4A:C6:57:B8:C0:14:1C:A1:FA`
- Native debug symbols warning: expected and harmless. The only native code is two small AndroidX libraries
  (graphics path, DataStore) that Google ships already stripped, so there are no symbols to upload.
- Deobfuscation file: nothing to do. The build puts `mapping.txt` inside the AAB and Play picks it up (App bundle
  explorer → Downloads shows it as "ReTrace mapping file").
- Release name: `1.5.0 (6)` (earlier: `1.4.0 (5)`, `1.3.0 (4)`, `1.2.0 (3)`, `1.1.0 (2)`, `1.0.0 (1)`)
- Release notes for 1.5.0 (en-IN):
  ```
  <en-IN>
  New: automatic backup, daily or weekly, to a folder you choose such as Google Drive.
  • Quick setup for new users: personal, business or both
  • What's new: a tour of everything added since the first version
  • Undo after deleting a loan or credit ledger entry
  • Search now finds customers, suppliers, loans and accounts by name
  • Fixes: restoring a backup keeps every entry; widget resets at midnight
  </en-IN>
  ```
- Release notes for 1.4.0 (en-IN):
  ```
  <en-IN>
  New: credit ledger for customers and suppliers.
  • Record what you gave and got on credit, with a balance for each person
  • Send payment reminders on WhatsApp or SMS with your UPI ID
  • PDF account statement with a UPI scan-to-pay code
  New: home-screen widget with today's spending and quick add buttons,
  and a quick-settings tile to add an expense from anywhere.
  </en-IN>
  ```
- Release notes for 1.3.0 (en-IN):
  ```
  <en-IN>
  New: personal and business books.
  • Keep your business money apart from your own, switch in one tap
  • Business categories ready: sales, purchases, rent, salaries, GST
  • Money put in or taken out is capital or drawings, not spending
  • Profit and loss report as a PDF, by month or financial year
  • Your details per business on statements and reports
  </en-IN>
  ```
- Release notes for 1.2.0 (en-IN):
  ```
  <en-IN>
  New: PDF loan statements.
  • Send a statement with the running balance, EMI plan and a UPI scan-to-pay code
  • Save full details for each person, bank or vendor: phone, email, address, bank and UPI
  • Add your own details once; they appear on every statement
  • Pick name and number straight from your contacts
  Also: the name under the icon is now Saral Money.
  </en-IN>
  ```
- Release notes for 1.1.0 (en-IN):
  ```
  <en-IN>
  New: Lent & borrowed.
  • Track money given to or taken from people, on credit or on interest
  • Interest per month or per year, any-time repayment or monthly EMI
  • See who owes what, interest due, the next payment and what is overdue
  • Reminder on the due date and a statement to share
  Also: your most used categories now come first when adding an entry.
  </en-IN>
  ```
- Release notes for 1.0.0 (en-IN):
  ```
  <en-IN>
  First release of Saral Money Tracker.
  • Track income and expenses by day, week, month or year
  • Budgets per category with a safe amount to spend each day
  • Accounts and credit cards, repeating entries and charts
  • Import your Excel tracker, export a full backup
  • Private: no ads, no internet access, optional app lock
  </en-IN>
  ```
