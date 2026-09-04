import type {
  BleConnectionOptions,
  BlePrinterProfile,
  PrinterConnectionOptions,
  PrinterDevice,
  PrinterPaperWidth,
  PrinterProfile,
  UsbConnectionOptions,
  UsbPrinterProfile,
} from './definitions';
import { printerError } from './errors';

export const DEFAULT_BLE_RECONNECT_ATTEMPTS = 2;
export const DEFAULT_BLE_RECONNECT_DELAY_MS = 1500;

export interface CreatePrinterProfileOptions {
  id?: string;
  name?: string;
  paperWidth?: PrinterPaperWidth;
  charsPerLine?: number;
  timeoutMs?: number;
  autoReconnect?: boolean;
  reconnectAttempts?: number;
  reconnectDelayMs?: number;
}

export function createPrinterProfile(device: PrinterDevice, options: CreatePrinterProfileOptions = {}): PrinterProfile {
  const base = {
    id: cleanRequired(options.id ?? `${device.transport}:${device.id}`, 'Profile id'),
    name: cleanRequired(options.name ?? device.name ?? device.id, 'Profile name'),
    transport: device.transport,
    paperWidth: options.paperWidth,
    charsPerLine: options.charsPerLine,
    timeoutMs: options.timeoutMs,
  };

  if (device.transport === 'ble') {
    return normalizePrinterProfile({
      ...base,
      transport: 'ble',
      deviceId: device.id,
      serviceUuid: device.serviceUuid,
      writeCharacteristicUuid: device.writeCharacteristicUuid,
      autoReconnect: options.autoReconnect,
      reconnectAttempts: options.reconnectAttempts,
      reconnectDelayMs: options.reconnectDelayMs,
    });
  }

  return normalizePrinterProfile({
    ...base,
    transport: 'usb',
    deviceId: device.id,
    vendorId: device.vendorId,
    productId: device.productId,
  });
}

export function normalizePrinterProfile(profile: PrinterProfile): PrinterProfile {
  const base = {
    id: cleanRequired(profile.id, 'Profile id'),
    name: cleanRequired(profile.name, 'Profile name'),
    paperWidth: normalizePaperWidth(profile.paperWidth),
    charsPerLine: normalizePositiveInt(profile.charsPerLine, 'charsPerLine'),
    timeoutMs: normalizeRange(profile.timeoutMs, 'timeoutMs', 3000, 30000),
  };

  if (profile.transport === 'ble') {
    const autoReconnect = profile.autoReconnect ?? false;
    return {
      ...base,
      transport: 'ble',
      deviceId: cleanRequired(profile.deviceId, 'deviceId'),
      serviceUuid: cleanOptional(profile.serviceUuid, 'serviceUuid'),
      writeCharacteristicUuid: cleanOptional(profile.writeCharacteristicUuid, 'writeCharacteristicUuid'),
      autoReconnect,
      reconnectAttempts: autoReconnect ? normalizeRange(
        profile.reconnectAttempts ?? DEFAULT_BLE_RECONNECT_ATTEMPTS,
        'reconnectAttempts',
        1,
        5,
      ) : undefined,
      reconnectDelayMs: autoReconnect ? normalizeRange(
        profile.reconnectDelayMs ?? DEFAULT_BLE_RECONNECT_DELAY_MS,
        'reconnectDelayMs',
        250,
        10000,
      ) : undefined,
    };
  }

  return {
    ...base,
    transport: 'usb',
    deviceId: cleanOptional(profile.deviceId, 'deviceId'),
    vendorId: normalizeNonNegative(profile.vendorId, 'vendorId'),
    productId: normalizeNonNegative(profile.productId, 'productId'),
  };
}

export function profileToConnectionOptions(profile: PrinterProfile): PrinterConnectionOptions {
  const normalized = normalizePrinterProfile(profile);
  if (normalized.transport === 'ble') {
    const options: BleConnectionOptions = {
      transport: 'ble',
      deviceId: normalized.deviceId,
      timeoutMs: normalized.timeoutMs,
      serviceUuid: normalized.serviceUuid,
      writeCharacteristicUuid: normalized.writeCharacteristicUuid,
      autoReconnect: normalized.autoReconnect,
      reconnectAttempts: normalized.reconnectAttempts,
      reconnectDelayMs: normalized.reconnectDelayMs,
    };
    return options;
  }

  const options: UsbConnectionOptions = {
    transport: 'usb',
    deviceId: normalized.deviceId,
    vendorId: normalized.vendorId,
    productId: normalized.productId,
    timeoutMs: normalized.timeoutMs,
  };
  return options;
}

function cleanRequired(value: string | undefined | null, label: string): string {
  if (typeof value !== 'string') {
    throw printerError('INVALID_ARGUMENT', `${label} must not be empty.`);
  }
  const normalized = value.trim();
  if (!normalized) {
    throw printerError('INVALID_ARGUMENT', `${label} must not be empty.`);
  }
  return normalized;
}

function cleanOptional(value: string | undefined | null, label: string): string | undefined {
  if (value !== undefined && value !== null && typeof value !== 'string') {
    throw printerError('INVALID_ARGUMENT', `${label} must be a string.`);
  }
  const normalized = value?.trim();
  return normalized ? normalized : undefined;
}

function normalizePaperWidth(value: PrinterPaperWidth | undefined): PrinterPaperWidth | undefined {
  if (value === undefined) {
    return undefined;
  }
  if (value !== 58 && value !== 80) {
    throw printerError('INVALID_ARGUMENT', 'paperWidth must be 58 or 80.');
  }
  return value;
}

function normalizePositiveInt(value: number | undefined, label: string): number | undefined {
  const normalized = normalizeInteger(value, label);
  if (normalized === undefined) {
    return undefined;
  }
  if (normalized < 1) {
    throw printerError('INVALID_ARGUMENT', `${label} must be at least 1.`);
  }
  return normalized;
}

function normalizeRange(
  value: number | undefined,
  label: string,
  min: number,
  max: number,
): number | undefined {
  const normalized = normalizeInteger(value, label);
  if (normalized === undefined) {
    return undefined;
  }
  if (normalized < min || normalized > max) {
    throw printerError('INVALID_ARGUMENT', `${label} must be between ${min} and ${max}.`);
  }
  return normalized;
}

function normalizeNonNegative(value: number | undefined, label: string): number | undefined {
  const normalized = normalizeInteger(value, label);
  if (normalized === undefined) {
    return undefined;
  }
  if (normalized < 0) {
    throw printerError('INVALID_ARGUMENT', `${label} must be zero or greater.`);
  }
  return normalized;
}

function normalizeInteger(value: number | undefined, label: string): number | undefined {
  if (value === undefined) {
    return undefined;
  }
  if (!Number.isFinite(value)) {
    throw printerError('INVALID_ARGUMENT', `${label} must be a finite number.`);
  }
  return Math.trunc(value);
}
