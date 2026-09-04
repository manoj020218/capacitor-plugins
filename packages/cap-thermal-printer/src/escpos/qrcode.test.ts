import { describe, expect, it } from 'vitest';
import { ThermalPrinterError } from '../errors';
import { qrCodeBytes } from './qrcode';

describe('ESC/POS QR codes', () => {
  it('builds QR data with temporary alignment', () => {
    expect(qrCodeBytes({ data: 'ABC123', size: 5, alignment: 'center' })).toEqual([
      0x1b, 0x61, 0x01,
      0x1d, 0x28, 0x6b, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00,
      0x1d, 0x28, 0x6b, 0x03, 0x00, 0x31, 0x43, 0x05,
      0x1d, 0x28, 0x6b, 0x03, 0x00, 0x31, 0x45, 0x31,
      0x1d, 0x28, 0x6b, 0x09, 0x00, 0x31, 0x50, 0x30, 65, 66, 67, 49, 50, 51,
      0x1d, 0x28, 0x6b, 0x03, 0x00, 0x31, 0x51, 0x30,
      0x1b, 0x61, 0x00,
    ]);
  });

  it('rejects invalid QR data and sizes', () => {
    expect(() => qrCodeBytes({ data: '', size: 5 })).toThrow(ThermalPrinterError);
    expect(() => qrCodeBytes({ data: 'abc', size: 0 })).toThrow('size must be between 1 and 16.');
    expect(() => qrCodeBytes({ data: String.fromCodePoint(0x20b9), size: 5 })).toThrow(
      'QR data only supports ASCII characters in this phase.',
    );
  });
});
