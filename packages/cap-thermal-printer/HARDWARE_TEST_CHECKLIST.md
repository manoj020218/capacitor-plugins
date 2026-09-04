# Hardware Test Checklist

Use the standalone demo in `demo/` or an equivalent throwaway Capacitor shell while running these checks.

## BLE

- Confirm Android grants the required BLE scan/connect permissions.
- Start a scan and verify nearby printers appear once with stable device IDs.
- Scan with `namePrefix` and verify non-matching devices are excluded.
- Scan with `serviceUuid` and verify matching devices are returned.
- Stop scan manually and verify `scanStopped` reports the discovered devices.
- Connect by `deviceId` and verify `connected` plus `getStatus()`.
- Connect with explicit `serviceUuid` and `writeCharacteristicUuid` when known.
- Print a short raw receipt and verify output starts immediately.
- Print a long raw receipt that exceeds one BLE packet and verify chunked output stays ordered.
- Print `printText`, `feed`, `cut`, and `openCashDrawer` and verify printer behavior.
- Print `printQRCode` with ASCII content and verify it scans.
- Print `printBarcode` with CODE128 data and verify it scans.
- Disconnect manually and verify `disconnected` plus `isConnected() === false`.
- Power off or move the printer out of range and verify disconnect/error events.
- Enable auto reconnect and verify retries remain bounded and status counters update.
- Deny permission and verify the plugin returns `PERMISSION_DENIED`.

## USB

- Attach a USB printer and verify enumeration returns vendor/product IDs.
- Accept the USB permission prompt and verify connection succeeds.
- Reject the USB permission prompt and verify a stable `USB_PERMISSION_DENIED` error.
- Connect by `deviceId` when multiple USB printers are attached.
- Connect by `vendorId` and `productId` when `deviceId` is not convenient.
- Print a short raw receipt and verify bulk OUT printing succeeds.
- Print `printQRCode` and `printBarcode` and verify both scan correctly.
- Disconnect manually and verify `disconnected` plus `isConnected() === false`.
- Unplug while connected and verify `usbDetached` plus disconnect/error events.

## Shared

- Verify `getStatus()` exposes `connectionState`, current device details, and `lastError` when relevant.
- Verify cached BLE device lists reset on a new scan and remain deduplicated.
- Verify `write()` without an active connection returns `NOT_CONNECTED`.
- Verify unsupported operations on web or non-Android shells fail cleanly.
- Verify the demo receipt prints transport, printer, and timestamp fields as expected.
