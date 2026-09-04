# @jenix/cap-thermal-printer

Reusable Android Capacitor thermal printer plugin for Jenix applications.

It provides one transport-independent TypeScript API over two Android transports:

- BLE thermal printers
- Android USB host thermal printers

The plugin keeps ESC/POS composition in TypeScript and keeps native Kotlin focused on device discovery, connection lifecycle, and raw byte transport.

## Status

Implemented in this repository:

- BLE scan with deduplicated discovery and scan-stop events
- BLE connect, writable characteristic discovery, MTU-aware chunked writes, and bounded reconnect
- USB enumeration, permission handoff, attach/detach events, connect, and bulk OUT writes
- Shared raw `number[]` write API for both transports
- ESC/POS helpers for text, feed, cut, cash drawer, QR, and CODE128
- Printer profile helpers for app-side persistence and reconnection defaults
- Standalone demo UI under `demo/`

Not implemented:

- `printImage`
- Windows native printing
- Bluetooth Classic
- TCP/IP printers
- Web Bluetooth or WebUSB

The web implementation intentionally rejects with an Android-only unsupported error.

## Installation

For this monorepo, install it as a local workspace dependency:

```json
{
  "dependencies": {
    "@jenix/cap-thermal-printer": "file:../../EMS/Plugins/cap-thermal-printer"
  }
}
```

If the package is later packed or published internally, install it like any other dependency:

```bash
npm install @jenix/cap-thermal-printer
```

Then sync Android:

```bash
npx cap sync android
```

If you consume the package through `npm pack` or a registry, `demo/` is included alongside `android/` and `dist/`.

## Android Requirements

- Android only
- Capacitor 7+
- `minSdkVersion 26`
- `compileSdkVersion 34`
- `targetSdkVersion 34`
- Bluetooth LE hardware is optional but required for BLE transport
- Android USB host support is optional but required for USB transport

Manifest entries already provided by the plugin:

- `android.permission.BLUETOOTH` on Android 11 and below
- `android.permission.BLUETOOTH_ADMIN` on Android 11 and below
- `android.permission.ACCESS_FINE_LOCATION` on Android 11 and below for BLE scan
- `android.permission.BLUETOOTH_SCAN` on Android 12+
- `android.permission.BLUETOOTH_CONNECT` on Android 12+
- `uses-feature android.hardware.bluetooth_le` as optional
- `uses-feature android.hardware.usb.host` as optional

The plugin does not require network permission.

## Public API

Connection and discovery:

- `scan(options?)`
- `stopScan()`
- `getDevices(options?)`
- `connect(options)`
- `disconnect()`
- `isConnected()`
- `getStatus()`

Raw and convenience printing:

- `write({ data, chunkSize? })`
- `printText({ text, encoding?, alignment? })`
- `feed({ lines? })`
- `printQRCode({ data, size?, alignment? })`
- `printBarcode({ data, format?, width?, height?, alignment? })`
- `cut({ partial? })`
- `openCashDrawer({ pin?, onMs?, offMs? })`

Current placeholder:

- `printImage({ base64, width?, alignment? })`

Events:

- `deviceFound`
- `scanStopped`
- `connected`
- `disconnected`
- `connectionError`
- `usbAttached`
- `usbDetached`

## Core Types

`PrinterDevice` is the shared discovery model:

```ts
interface PrinterDevice {
  id: string;
  name?: string;
  transport: 'ble' | 'usb';
  connected?: boolean;
  permissionGranted?: boolean;
  rssi?: number;
  vendorId?: number;
  productId?: number;
  serviceUuid?: string;
  writeCharacteristicUuid?: string;
}
```

`PrinterStatus` is the shared runtime status model:

```ts
interface PrinterStatus {
  connected: boolean;
  transport?: 'ble' | 'usb';
  device?: PrinterDevice;
  connectionState?: 'disconnected' | 'connecting' | 'connected' | 'disconnecting' | 'reconnecting';
  reconnectAttempt?: number;
  reconnectMaxAttempts?: number;
  lastError?: {
    code: PrinterErrorCode;
    message: string;
  };
}
```

`PrinterProfile` is the app-side persistence shape:

- BLE profiles can store `deviceId`, `serviceUuid`, `writeCharacteristicUuid`, and bounded reconnect settings.
- USB profiles can store `deviceId`, `vendorId`, and `productId`.
- Both profile kinds can store `paperWidth`, `charsPerLine`, and `timeoutMs`.

## BLE Usage

Scan:

```ts
import { ThermalPrinter } from '@jenix/cap-thermal-printer';

const result = await ThermalPrinter.scan({
  transport: 'ble',
  allowUnnamed: true,
  timeoutMs: 10000,
});

console.log(result.devices);
```

Connect with automatic writable-characteristic discovery:

```ts
const status = await ThermalPrinter.connect({
  transport: 'ble',
  deviceId: 'AA:BB:CC:DD:EE:FF',
  autoReconnect: true,
  reconnectAttempts: 2,
  reconnectDelayMs: 1500,
});
```

Connect with explicit UUID hints when you know the printer service:

```ts
await ThermalPrinter.connect({
  transport: 'ble',
  deviceId: 'AA:BB:CC:DD:EE:FF',
  serviceUuid: '000018f0-0000-1000-8000-00805f9b34fb',
  writeCharacteristicUuid: '00002af1-0000-1000-8000-00805f9b34fb',
});
```

BLE notes:

- Runtime scan permission alias is `ble` on Android 12+ and `location` on Android 11 and below.
- Discovered devices are deduplicated per scan session.
- Writes are queued natively and chunk size is derived from negotiated MTU when available.
- Reconnect is opt-in and bounded. There is no infinite retry loop.

## USB Usage

List devices:

```ts
const result = await ThermalPrinter.getDevices({ transport: 'usb' });
console.log(result.devices);
```

Connect by device ID:

```ts
await ThermalPrinter.connect({
  transport: 'usb',
  deviceId: 'device-name-or-system-id',
  timeoutMs: 10000,
});
```

Connect by vendor and product IDs when needed:

```ts
await ThermalPrinter.connect({
  transport: 'usb',
  vendorId: 1155,
  productId: 22336,
});
```

USB notes:

- The plugin requests Android USB permission through `connect({ transport: 'usb' })`.
- Printer candidates are devices exposing a printer-class interface or any bulk OUT endpoint.
- The plugin prefers printer-class interfaces first and falls back to other bulk OUT interfaces only when needed.

## Raw Printing

The low-level transport escape hatch is `write({ data })`, where `data` is a JSON-safe `number[]`.

```ts
await ThermalPrinter.write({
  data: [0x1b, 0x40, 0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x0a],
});
```

Use `chunkSize` only when you need to override transport defaults for troubleshooting:

```ts
await ThermalPrinter.write({
  data: receiptBytes,
  chunkSize: 128,
});
```

## ESC/POS Helpers

`EscPosBuilder` keeps receipt composition in TypeScript:

```ts
import { EscPosBuilder, ThermalPrinter } from '@jenix/cap-thermal-printer';

const receipt = new EscPosBuilder()
  .initialize()
  .align('center')
  .bold()
  .text('JENIX INDIA PVT LTD')
  .newline()
  .bold(false)
  .separator()
  .align('left')
  .text('Thermal Printer Test\n')
  .feed(2)
  .cut()
  .build();

await ThermalPrinter.write({ data: receipt });
```

Convenience wrappers build ESC/POS internally and still use the same native `write()` path:

```ts
await ThermalPrinter.printText({ text: 'Quick text path\n', alignment: 'center' });
await ThermalPrinter.feed({ lines: 2 });
await ThermalPrinter.printQRCode({ data: 'https://jenix.example/receipt/123', alignment: 'center', size: 6 });
await ThermalPrinter.printBarcode({ data: 'INV-123', format: 'code128', alignment: 'center', width: 3 });
await ThermalPrinter.cut();
await ThermalPrinter.openCashDrawer({ pin: 2, onMs: 120, offMs: 240 });
```

Current ESC/POS limitations:

- Text path uses ASCII-safe output in this phase.
- QR payloads are ASCII only.
- Barcode support is limited to CODE128 with Code Set B selection and brace escaping.
- `printImage` is not implemented yet.

## Printer Profiles

Profile helpers normalize stored printer settings before connect time:

```ts
import {
  ThermalPrinter,
  createPrinterProfile,
  profileToConnectionOptions,
} from '@jenix/cap-thermal-printer';

const profile = createPrinterProfile(device, {
  paperWidth: 58,
  charsPerLine: 32,
  autoReconnect: true,
  reconnectAttempts: 2,
  reconnectDelayMs: 1500,
});

await ThermalPrinter.connect(profileToConnectionOptions(profile));
```

Validation rules enforced by `normalizePrinterProfile()`:

- `paperWidth` must be `58` or `80`
- `charsPerLine` must be at least `1`
- `timeoutMs` must be between `3000` and `30000`
- BLE reconnect attempts must be between `1` and `5`
- BLE reconnect delay must be between `250` and `10000`
- USB `vendorId` and `productId` must be non-negative when present

## Events

Subscribe through Capacitor listeners:

```ts
const statusHandle = await ThermalPrinter.addListener('connected', (status) => {
  console.log('connected', status);
});

const errorHandle = await ThermalPrinter.addListener('connectionError', (event) => {
  console.error(event.code, event.message, event.transport);
});
```

Event meanings:

- `deviceFound`: one event per unique BLE device during a scan
- `scanStopped`: emitted for manual stop, timeout, restart, or scanner failure
- `connected`: emitted after BLE or USB connect succeeds
- `disconnected`: emitted after disconnect or detach cleanup
- `connectionError`: emitted for connection and runtime transport failures
- `usbAttached` and `usbDetached`: emitted from the Android USB monitor

## Error Codes

Stable machine-readable error codes:

- `PERMISSION_DENIED`
- `DEVICE_NOT_FOUND`
- `CONNECTION_FAILED`
- `CONNECTION_TIMEOUT`
- `NOT_CONNECTED`
- `WRITE_FAILED`
- `NO_WRITABLE_CHARACTERISTIC`
- `USB_PERMISSION_DENIED`
- `USB_INTERFACE_NOT_FOUND`
- `USB_ENDPOINT_NOT_FOUND`
- `UNSUPPORTED_OPERATION`
- `INVALID_ARGUMENT`

Use `toPrinterError()` to normalize thrown values into `ThermalPrinterError`.

## Demo

The minimal manual test UI is in `demo/`:

- [demo/README.md](demo/README.md)
- [demo/index.html](demo/index.html)
- [demo/demo.js](demo/demo.js)

It is intentionally not wired into any existing Jenix application. Use it in a throwaway Capacitor Android shell app when you want a quick integration surface without touching production app routes.

## Hardware Compatibility Notes

This plugin is intentionally generic. It does not hard-code:

- BLE service UUIDs
- BLE characteristic UUIDs
- USB vendor IDs
- USB product IDs
- specific printer model names

You still need to validate actual printer behavior because thermal printers vary across:

- accepted code pages
- cut support
- cash drawer support
- QR and barcode command support
- BLE GATT layouts
- USB interface exposure

Recommended approach:

1. Start with raw `write()` and a short initialization receipt.
2. If BLE discovery is ambiguous, provide explicit service and characteristic UUIDs from the target hardware.
3. Use the hardware checklist before rolling out to multiple printer models.

## Verification

Verified on September 3, 2026:

- `npm run build --workspace @jenix/cap-thermal-printer`
- `npm run test --workspace @jenix/cap-thermal-printer`
- `npm run verify --workspace @jenix/cap-thermal-printer`
- `node --check demo/demo.js`
- `node --check demo/receipt.js`
- `cmd /c gradlew.bat :jenix-cap-thermal-printer:assembleDebug` from `APK/mobile/android`

Android library compile passed. The Gradle run reported only deprecation warnings from older Android and Capacitor APIs already used in the current implementation.

## Known Limitations

- Android only
- No image printing yet
- No Windows support
- No Bluetooth Classic support
- No network printer support
- No browser Web Bluetooth or WebUSB implementation
- No automatic code-page negotiation
- Hardware verification is still manual
