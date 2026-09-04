import type { PrintQRCodeOptions } from '../definitions';
import { printerError } from '../errors';
import { setAlignment } from './commands';

const GS = 0x1d;

export function qrCodeBytes(options: PrintQRCodeOptions): number[] {
  const data = strictAsciiData(options.data, 'QR data');
  const size = normalizeQrSize(options.size ?? 6);
  const bytes: number[] = [];

  if (options.alignment) {
    bytes.push(...setAlignment(options.alignment));
  }

  bytes.push(...qrCommand(0x41, [0x32, 0x00]));
  bytes.push(...qrCommand(0x43, [size]));
  bytes.push(...qrCommand(0x45, [0x31]));
  bytes.push(...qrCommand(0x50, [0x30, ...data]));
  bytes.push(...qrCommand(0x51, [0x30]));

  if (options.alignment && options.alignment !== 'left') {
    bytes.push(...setAlignment('left'));
  }

  return bytes;
}

function qrCommand(fn: number, args: number[]): number[] {
  const payloadLength = args.length + 2;
  return [GS, 0x28, 0x6b, payloadLength & 0xff, (payloadLength >> 8) & 0xff, 0x31, fn, ...args];
}

function normalizeQrSize(value: number): number {
  const normalized = Math.trunc(value);
  if (normalized < 1 || normalized > 16) {
    throw printerError('INVALID_ARGUMENT', 'size must be between 1 and 16.');
  }
  return normalized;
}

function strictAsciiData(value: string, label: string): number[] {
  if (!value) {
    throw printerError('INVALID_ARGUMENT', `${label} must not be empty.`);
  }

  const bytes: number[] = [];
  for (const char of value) {
    const code = char.codePointAt(0) ?? 0;
    if (code > 0x7f) {
      throw printerError('UNSUPPORTED_OPERATION', `${label} only supports ASCII characters in this phase.`);
    }
    bytes.push(code);
  }
  return bytes;
}
