# PriceScan

An Android app that opens straight to a camera scanner, reads a product's price (MRP) and barcode, counts how many you bought, and keeps date-wise expense records with a live dashboard and PDF export.

## Features

- **Scanner first**: the app opens on the camera. It reads the printed MRP / ₹ / Rs price with on-device text recognition, and the barcode if there is one. No barcode is needed. Nothing leaves the phone.
- **Reads label layouts**: prices printed under an "MRP ↓" heading (with USP and batch number columns beside it) are matched to the right heading, so the unit price or batch number is not mistaken for the MRP.
- **Mfg and expiry dates**: reads packaging / manufacture and use-by / expiry / best-before dates in common formats (12 SEP 26, 11JUN.27, 08/2026, 15-03-2026, "best before 9 months"). Saved with each purchase, editable, shown in records and the PDF, and the dashboard warns about items expiring soon.
- **One pack = one item**: everything read while a pack is in view (price, barcode, name, dates, nutrition) goes to the same item and it is counted once. Take the pack out of view before showing the next one.
- **Product names and nutrition from the barcode**: barcodes are looked up on Open Food Facts (free, needs internet the first time; the result is remembered offline).
- **Nutrition from the pack**: turn the pack to its nutrition table and the app reads energy, fat, saturated fat, trans fat, sugar, added sugar, carbs, protein, fibre and sodium (per 100 g), with green / amber / red levels and "High sugar"-style warnings in the bill and records.
- **Product photos**: each scanned item gets a product photo from Open Food Facts (by barcode, or by product name when there is no barcode), shown in the scanner, bill, records and dashboard. Older records get photos filled in automatically when online.
- **Product type and details**: every scan is sorted into Food, Groceries, Medicine, Clothes, Personal care, Household, Electronics or Other (from the online database and label words like "tablets", "100% cotton", "shampoo", "atta"). The app reads type-specific details: ingredients, allergens, veg/vegan, Nutri-Score and processing level for food; composition, dosage, Schedule H / prescription warning for medicine; fabric, size and wash care for clothes; plus net quantity, manufacturer, storage and origin. You can change the category; Records can be filtered by it, the dashboard shows spend by category and the PDF includes a category summary.
- **Hindi names**: reads Devanagari product names (e.g. आम का अचार) when there is no English name.
- **Auto count**: show each packet to the camera one after another. Every new packet is counted (5 packets of Maggi = qty 5), with a beep and vibration. Holding one packet still never counts twice.
- **Manual mode**: tap Capture, then type or step the quantity yourself. You can also add any item by hand.
- **Bill list**: review the items scanned, fix name, price or quantity, then Save.
- **Remembers products**: after you save a barcoded item once, the next scan fills in its name and price.
- **Date-wise records**: grouped by day with daily totals, search, filters (Today, This week, This month, Custom range), edit, delete with undo.
- **Dashboard**: month spend with change vs last month, today / week / average per day / items bought, 7-day, 30-day and 12-month charts, top products, recent purchases.
- **PDF export**: a formatted report (summary, top products, date-wise table with daily subtotals, grand total) for the current filter, ready to open, share or save.
- Light and dark theme, Material 3 design.

## Get the APK

Every push to `main` builds the app on GitHub Actions.

1. Open the repository's **Releases** page and download `PriceScan.apk` from **PriceScan (latest build)**. (It is also attached to each run on the **Actions** tab as `PriceScan-apk`.)
2. Open the file on your Android phone (Android 8.0 or newer) and allow "install unknown apps" when asked.

## Build it yourself

Open the folder in Android Studio (Ladybug or newer) and press Run, or from a terminal with the Android SDK installed:

```
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

## Tech

Kotlin, Jetpack Compose, Material 3, CameraX, ML Kit (text recognition + barcode, bundled models), Room, Android PdfDocument.
