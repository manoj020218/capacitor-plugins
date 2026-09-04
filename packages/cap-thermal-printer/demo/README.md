# Thermal Printer Demo

This folder contains the minimal Phase 9 demo UI for `@jenix/cap-thermal-printer`.

It is intentionally self-contained:

- no React
- no Vite dependency
- no existing Jenix app routes
- direct use of `window.Capacitor.Plugins.JenixThermalPrinter`

## Files

- `index.html`: single-screen demo layout
- `demo.css`: mobile-friendly styling
- `demo.js`: scan, connect, print, disconnect, and event-log behavior
- `receipt.js`: sample receipt payload and helper formatting

## What The Demo Covers

- scan BLE printers
- stop scan
- list USB printers
- connect selected BLE printer
- connect selected USB printer
- print a test receipt
- disconnect
- inspect current status and event log

## Expected Test Receipt

The demo prints a simple receipt containing:

- `JENIX INDIA PVT LTD`
- `THERMAL PRINTER TEST`
- active transport
- connection status
- printer name or ID
- timestamp
- QR payload
- CODE128 barcode payload

## Running It In A Throwaway Capacitor Shell

Use any Android Capacitor shell app that can load static web assets.

Recommended setup:

1. Install this plugin into the shell app.
2. Copy the contents of `demo/` into the shell app web directory.
3. Make `index.html` the loaded page for the shell app.
4. Run `npx cap sync android`.
5. Open the app on an Android device with a BLE or USB printer available.

If your shell app uses a bundler:

- place these files in a static/public folder
- preserve the relative file names
- keep `demo.js` loading `./receipt.js`

## Notes

- This is a manual verification surface, not a production UI.
- The page expects the Capacitor plugin to be installed and registered as `JenixThermalPrinter`.
- The demo does not mock printers.
- `printImage` is still not implemented by the plugin.
