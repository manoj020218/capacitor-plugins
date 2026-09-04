import { describe, expect, it } from 'vitest';
import { ThermalPrinterError } from '../errors';
import { barcodeBytes } from './barcode';

describe('ESC/POS barcodes', () => {
  it('builds CODE128 barcode data with temporary alignment', () => {
    expect(barcodeBytes({ data: 'A{B', width: 3, height: 60, alignment: 'right' })).toEqual([
      0x1b, 0x61, 0x02,
      0x1d, 0x48, 0x02,
      0x1d, 0x77, 0x03,
      0x1d, 0x68, 0x3c,
      0x1d, 0x6b, 0x49, 0x06, 0x7b, 0x42, 0x41, 0x7b, 0x7b, 0x42,
      0x1b, 0x61, 0x00,
    ]);
  });

  it('rejects invalid barcode values', () => {
    expect(() => barcodeBytes({ data: '', format: 'code128' })).toThrow(ThermalPrinterError);
    expect(() => barcodeBytes({ data: 'ABC', format: 'code128', width: 1 })).toThrow('width must be between 2 and 6.');
    expect(() => barcodeBytes({ data: String.fromCodePoint(0x20b9), format: 'code128' })).toThrow(
      'Barcode data only supports printable ASCII characters in this phase.',
    );
  });
});
