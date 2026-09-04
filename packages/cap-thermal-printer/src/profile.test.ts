import { describe, expect, it } from 'vitest';
import type { PrinterDevice, PrinterProfile } from './definitions';
import {
  createPrinterProfile,
  DEFAULT_BLE_RECONNECT_ATTEMPTS,
  DEFAULT_BLE_RECONNECT_DELAY_MS,
  normalizePrinterProfile,
  profileToConnectionOptions,
} from './profile';

describe('printer profiles', () => {
  it('creates BLE profiles from discovered devices', () => {
    const device: PrinterDevice = {
      id: 'AA:BB:CC:DD:EE:FF',
      name: 'Jenix BLE',
      transport: 'ble',
      serviceUuid: 'service-uuid',
      writeCharacteristicUuid: 'char-uuid',
    };

    expect(createPrinterProfile(device, { autoReconnect: true })).toEqual({
      id: 'ble:AA:BB:CC:DD:EE:FF',
      name: 'Jenix BLE',
      transport: 'ble',
      deviceId: 'AA:BB:CC:DD:EE:FF',
      serviceUuid: 'service-uuid',
      writeCharacteristicUuid: 'char-uuid',
      autoReconnect: true,
      reconnectAttempts: DEFAULT_BLE_RECONNECT_ATTEMPTS,
      reconnectDelayMs: DEFAULT_BLE_RECONNECT_DELAY_MS,
    });
  });

  it('maps BLE profiles to connection options', () => {
    expect(profileToConnectionOptions(normalizePrinterProfile({
      id: 'profile-1',
      name: 'Counter Printer',
      transport: 'ble',
      deviceId: 'AA:BB',
      autoReconnect: true,
      reconnectAttempts: 3,
      reconnectDelayMs: 2000,
      timeoutMs: 12000,
    }))).toEqual({
      transport: 'ble',
      deviceId: 'AA:BB',
      autoReconnect: true,
      reconnectAttempts: 3,
      reconnectDelayMs: 2000,
      timeoutMs: 12000,
      serviceUuid: undefined,
      writeCharacteristicUuid: undefined,
    });
  });

  it('creates USB profiles and preserves selection fields', () => {
    const device: PrinterDevice = {
      id: '/dev/bus/usb/001/002',
      name: 'USB Printer',
      transport: 'usb',
      vendorId: 1234,
      productId: 5678,
    };

    expect(profileToConnectionOptions(createPrinterProfile(device, { timeoutMs: 9000 }))).toEqual({
      transport: 'usb',
      deviceId: '/dev/bus/usb/001/002',
      vendorId: 1234,
      productId: 5678,
      timeoutMs: 9000,
    });
  });

  it('rejects invalid reconnect settings', () => {
    expect(() => normalizePrinterProfile({
      id: 'bad',
      name: 'Bad',
      transport: 'ble',
      deviceId: 'AA:BB',
      autoReconnect: true,
      reconnectAttempts: 0,
    })).toThrow('reconnectAttempts must be between 1 and 5.');
    expect(() => normalizePrinterProfile({
      id: 'bad',
      name: 'Bad',
      transport: 'usb',
      vendorId: -1,
    })).toThrow('vendorId must be zero or greater.');
    expect(() => normalizePrinterProfile({
      id: 'bad',
      name: 'Bad',
      transport: 'usb',
      timeoutMs: Number.NaN,
    })).toThrow('timeoutMs must be a finite number.');
  });

  it('rejects blank required profile fields from untyped callers', () => {
    expect(() => normalizePrinterProfile({
      id: '   ',
      name: 'Profile',
      transport: 'ble',
      deviceId: 'AA:BB',
    } as unknown as PrinterProfile)).toThrow('Profile id must not be empty.');
  });
});
