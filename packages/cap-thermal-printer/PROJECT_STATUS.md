# Thermal Printer Status

Updated: 2026-09-03

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

Important architecture decisions:

- Package name stays `@jenix/cap-thermal-printer` and Capacitor plugin name stays `JenixThermalPrinter`.
- ESC/POS composition stays in TypeScript while Android Kotlin stays transport-focused.
- BLE and USB share the same public raw `number[]` write contract and status model.
- BLE discovery remains UUID-agnostic by default and only prefers caller-supplied UUID hints when available.
- BLE reconnect is opt-in and bounded, with progress surfaced through `connectionState`, reconnect counters, and `lastError`.
- USB discovery remains generic by accepting printer-class interfaces or any bulk OUT endpoint.
- Demo assets stay inside `cap-thermal-printer/demo` and are not wired into any existing Jenix app.

Files added or changed:

- `cap-thermal-printer/package.json`
- `cap-thermal-printer/README.md`
- `cap-thermal-printer/PROJECT_STATUS.md`
- `cap-thermal-printer/HARDWARE_TEST_CHECKLIST.md`
- `cap-thermal-printer/demo/*`
- `cap-thermal-printer/src/*`
- `cap-thermal-printer/android/src/main/*`

Current build status:

- `npm run verify --workspace @jenix/cap-thermal-printer` passed on 2026-09-03.
- `cmd /c gradlew.bat :jenix-cap-thermal-printer:assembleDebug` from `APK/mobile/android` passed on 2026-09-03.
- Gradle emitted deprecation warnings from older Android and Capacitor APIs, but no compile errors remained.
- Manual hardware verification is still pending; use `HARDWARE_TEST_CHECKLIST.md`.

Next phase:

- Baseline Android BLE + USB plugin scope is complete.
- Future enhancements, if needed: image printing, extra code pages, Bluetooth Classic, or network transports.
