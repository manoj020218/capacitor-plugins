import { describe, expect, it } from 'vitest';
import { isPrinterErrorCode, printerError, ThermalPrinterError, toPrinterError } from './errors';

describe('thermal printer errors', () => {
  it('recognizes stable printer error codes', () => {
    expect(isPrinterErrorCode('WRITE_FAILED')).toBe(true);
    expect(isPrinterErrorCode('UNKNOWN_CODE')).toBe(false);
  });

  it('preserves explicit thermal printer errors', () => {
    const error = printerError('NOT_CONNECTED', 'No active printer connection.');
    expect(toPrinterError(error)).toBe(error);
  });

  it('converts generic errors into thermal printer errors', () => {
    const error = toPrinterError(new Error('BLE connection dropped.'), 'CONNECTION_FAILED');
    expect(error).toBeInstanceOf(ThermalPrinterError);
    expect(error.code).toBe('CONNECTION_FAILED');
    expect(error.message).toBe('BLE connection dropped.');
  });

  it('captures non-error causes with fallback details', () => {
    const error = toPrinterError('failed', 'WRITE_FAILED', 'Write failed.');
    expect(error.code).toBe('WRITE_FAILED');
    expect(error.details).toEqual({ cause: 'failed' });
  });
});
