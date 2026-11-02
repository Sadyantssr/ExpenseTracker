# 💸 Expense Tracker (Auto-Sync to Google Sheets)

A lightweight Android expense tracking app that logs daily spending and syncs directly with Google Sheets. 

No manual spreadsheet creation required — sign in once with Google, and the app automatically provisions a dedicated spreadsheet in your Google Drive and starts recording your transactions immediately.

---

## ✨ Features

- **Automated Spreadsheet Setup:** Creates and formats its own `Expense Tracker` sheet in your Google Drive upon first sign-in.
- **Single-Tap Logging:** Quick categories (Food, Groceries, Commute, Bills, Entertainment) with payment method tags.
- **Zero-Friction Install:** Download the standalone `.apk` directly to your phone — no local developer environment or cloning required.
- **Privacy-First (Least Privilege):** Uses the `drive.file` scope — the app can only view and modify the files it creates. It has zero access to the rest of your Google Drive.
- **Real-Time Cloud Sync:** Backed by Google Sheets so you retain full ownership and export control of your financial data.

---

## 📲 Download & Installation

You do not need to clone the project or run development tools to use this app.

1. **Download the APK:**
   * Go to the [**Releases**](https://github.com/Sadayantssr/ExpenseTracker/releases/latest) section on this repository.
   * Download the latest `ExpenseTracker-vX.X.X.apk` file directly on your Android phone.

2. **Install on Android:**
   * Tap the downloaded file.
   * If prompted by Android, toggle **"Allow from this source"** (or *Install unknown apps* in Settings).
   * Tap **Install** and open the app.

---

## 🚀 Quick Start Guide

1. **Launch the App:** Open **Expense Tracker** from your app drawer.
2. **Sign in with Google:** Tap **"Sign in with Google"** and choose your preferred Google account.
3. **Automatic Sheet Creation:** 
   * On your first sign-in, the app will automatically generate a new sheet titled **`Personal Expenses`** in your Google Drive.
   * Row headers (*Date, Amount, Category, Payment Method, Notes*) and standard formatting are generated automatically.
4. **Log an Expense:**
   * Enter the amount.
   * Select a category and payment mode.
   * Tap **Save to Sheets**.
   * Open your Google Drive / Google Sheets app to see the entry appear immediately.

---

## 🔒 Permissions & Security

| Scope | Purpose |
| :--- | :--- |
| `https://www.googleapis.com/auth/drive.file` | Allows the app to create and update its own `Personal Expenses` spreadsheet. The app **cannot** view, edit, or delete any other files in your Google Drive. |
| `openid`, `userinfo.email` | Used exclusively to verify your Google identity and link the app to your Drive. |

---

## 🛠️ How It Works (For Contributors)

If you wish to modify or build the standalone APK from source:

1. The app handles authentication via Google OAuth 2.0.
2. Upon login, the app queries Google Drive API:
   - If `Personal Expenses` does not exist, it executes a `POST` request to `https://www.googleapis.com/drive/v3/files` with MIME type `application/vnd.google-apps.spreadsheet`.
   - Populates Row 1 with header styling.
3. Every submission dispatches a `spreadsheets.values.append` API call to log the transaction.

---

## 📄 License

Distributed under the [MIT License](LICENSE).
[Privacy Policy](https://sadyantssr.github.io/ExpenseTracker/privacy-policy.html)
