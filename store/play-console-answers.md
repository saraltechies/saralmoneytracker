# Play Console answers for Saral Money Tracker

Answers for every declaration in **Policy → App content**, plus the release setup.
They match the code as of v1.0.0: no ads, no analytics, no crash reporting, and **no INTERNET permission**
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
- In the declaration, choose the option for apps that **don't provide any of the listed financial features**. If the form asks
  specifically about budgeting tools, select **Personal finance management / budgeting** (no regulated services).

## Health apps
- **None**

## Permissions
No sensitive-permission declarations are needed: notifications, run at startup and biometrics are normal permissions.
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
- Deobfuscation file: upload `mapping.txt` from the same build.
- Release name: `1.0.0 (1)`
- Release notes (en-IN):
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
