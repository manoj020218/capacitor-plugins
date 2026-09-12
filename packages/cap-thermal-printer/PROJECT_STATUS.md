# Thermal Printer Status

Updated: 2026-09-12

Completed phase:

- Phase 0: repository integration
- Phase 1: core models, transport contract, byte helpers, and shared errors
- Phase 2: Android BLE scan, permissions, deduplicated discovery, and scan events
- Phase 3: Android BLE connect, writable characteristic discovery, queued raw writes, and connection status
- Phase 4: TypeScript ESC/POS helpers for text, feed, cut, cash drawer, and receipt composition
- Phase 5: Android USB discovery, permission handoff, and attach/detach events
- Phase 6: Android USB connect, bulk OUT endpoint discovery, and raw writes
- Phase 7: TypeScript ESC/POS QR and CODE128 barcode helpers
- Phase 8: printer profile helpers, bounded BLE reconnect handling, and status refinements
- Phase 9: standalone demo UI for scan, connect, print, and disconnect testing
- Phase 10: documentation refresh and final verification
- Phase 11: Android Bluetooth Classic (SPP) transport — bonded-device listing, RFCOMM connect, blocking raw writes, and ACL-disconnect detection, added to fix printing on BR/EDR-only printers (e.g. PSF588) that BLE-only discovery could never see

Important architecture decisions:

- Package name stays `@jenix/cap-thermal-printer` and Capacitor plugin name stays `JenixThermalPrinter`.
- ESC/POS composition stays in TypeScript while Android Kotlin stays transport-focused.
- BLE, USB, and Bluetooth Classic share the same public raw `number[]` write contract and status model.
- BLE discovery remains UUID-agnostic by default and only prefers caller-supplied UUID hints when available.
- BLE reconnect is opt-in and bounded, with progress surfaced through `connectionState`, reconnect counters, and `lastError`.
- USB discovery remains generic by accepting printer-class interfaces or any bulk OUT endpoint.
- Bluetooth Classic connects only to already-bonded devices (paired via Android system Bluetooth settings); the plugin does not drive its own pairing UI.
- Demo assets stay inside `cap-thermal-printer/demo` and are not wired into any existing Jenix app.

Files added or changed:

- `cap-thermal-printer/package.json`
- `cap-thermal-printer/README.md`
- `cap-thermal-printer/PROJECT_STATUS.md`
- `cap-thermal-printer/HARDWARE_TEST_CHECKLIST.md`
- `cap-thermal-printer/demo/*`
- `cap-thermal-printer/src/*`
- `cap-thermal-printer/android/src/main/*` (added `BtClassicPrinterConnection.kt`, `BtClassicPrinterDevice.kt`, `BtClassicSupport.kt`)
- `packages/tsconfig.base.json` (was missing entirely — `tsconfig.json`'s `extends` reference was broken, so `npm run build`/`test` could not run at all; added a minimal base config)

Current build status:

- `npm run test --workspace @jenix/cap-thermal-printer` passed on 2026-09-12 (26 tests, including new Bluetooth Classic profile coverage).
- `npm run build --workspace @jenix/cap-thermal-printer` (tsc, strict mode) passed on 2026-09-12.
- Kotlin changes could not be compiled in this environment (no `gradlew`/Android SDK available in this repo checkout) — reviewed by hand against the existing BLE/USB connection classes' patterns; needs a real Gradle build (e.g. from `APK/mobile/android`) before shipping.
- `node --check demo/demo.js` / `demo/receipt.js` still fail — pre-existing, unrelated to this change (the demo files use ESM `import`/`export` but `package.json` has no `"type": "module"`, so `node --check` treats them as CommonJS). Not fixed here since it's orthogonal to Bluetooth printing.
- Manual hardware verification is still pending; use `HARDWARE_TEST_CHECKLIST.md`. The demo UI now has a Bluetooth Classic panel for this.

Next phase:

- Baseline Android BLE + USB + Bluetooth Classic plugin scope is complete.
- Future enhancements, if needed: image printing, extra code pages, or network transports.
