import type { PrintBarcodeOptions } from '../definitions';
import { printerError } from '../errors';
import { setAlignment } from './commands';

const GS = 0x1d;
const CODE128 = 0x49;

export function barcodeBytes(options: PrintBarcodeOptions): number[] {
  const format = options.format ?? 'code128';
  if (format !== 'code128') {
    throw printerError('UNSUPPORTED_OPERATION', 'Only CODE128 barcodes are supported in this phase.');
  }

  const data = encodeCode128Data(options.data);
  const width = normalizeWidth(options.width ?? 2);
  const height = normalizeHeight(options.height ?? 80);
  const bytes: number[] = [];

  if (options.alignment) {
    bytes.push(...setAlignment(options.alignment));
  }

  bytes.push(GS, 0x48, 0x02);
  bytes.push(GS, 0x77, width);
  bytes.push(GS, 0x68, height);
  bytes.push(GS, 0x6b, CODE128, data.length, ...data);

  if (options.alignment && options.alignment !== 'left') {
    bytes.push(...setAlignment('left'));
  }

  return bytes;
}

function encodeCode128Data(value: string): number[] {
  if (!value) {
    throw printerError('INVALID_ARGUMENT', 'Barcode data must not be empty.');
  }

  const payload = [0x7b, 0x42];
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0;
    if (code < 0x20 || code > 0x7e) {
      throw printerError('UNSUPPORTED_OPERATION', 'Barcode data only supports printable ASCII characters in this phase.');
    }
    payload.push(code);
    if (code === 0x7b) {
      payload.push(code);
    }
  }

  if (payload.length > 255) {
    throw printerError('INVALID_ARGUMENT', 'Barcode data is too long for a single CODE128 command.');
  }

  return payload;
}

function normalizeWidth(value: number): number {
  const normalized = Math.trunc(value);
  if (normalized < 2 || normalized > 6) {
    throw printerError('INVALID_ARGUMENT', 'width must be between 2 and 6.');
  }
  return normalized;
}

function normalizeHeight(value: number): number {
  const normalized = Math.trunc(value);
  if (normalized < 1 || normalized > 255) {
    throw printerError('INVALID_ARGUMENT', 'height must be between 1 and 255.');
  }
  return normalized;
}
