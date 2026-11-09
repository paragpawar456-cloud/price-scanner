# PriceScan

An Android app that opens straight to a camera scanner, reads a product's price (MRP) and barcode, counts how many you bought, and keeps date-wise expense records with a live dashboard and PDF export.

## Features

- **Scanner first**: the app opens on the camera. It reads the printed MRP / ₹ / Rs price with on-device text recognition and the barcode with ML Kit. Nothing leaves the phone.
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
