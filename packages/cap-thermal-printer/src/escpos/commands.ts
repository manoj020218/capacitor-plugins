import { encodeAscii, toByteNumbers } from '../bytes';
import type { CashDrawerOptions, PrinterAlignment } from '../definitions';
import { printerError } from '../errors';

const ESC = 0x1b;
const GS = 0x1d;
const LF = 0x0a;

export function initializePrinter(): number[] {
  return [ESC, 0x40];
}

export function textBytes(text: string, encoding = 'ascii'): number[] {
  if (encoding.toLowerCase() !== 'ascii') {
    throw printerError('UNSUPPORTED_OPERATION', 'Only ASCII text encoding is supported in this phase.');
  }
  return toByteNumbers(encodeAscii(text));
}

export function lineFeed(count = 1): number[] {
  const normalized = normalizeByteRange(count, 'count');
  return Array.from({ length: normalized }, () => LF);
}

export function feedLines(lines = 1): number[] {
  return [ESC, 0x64, normalizeByteRange(lines, 'lines')];
}

export function setAlignment(alignment: PrinterAlignment): number[] {
  return [ESC, 0x61, alignmentCode(alignment)];
}

export function setBold(enabled: boolean): number[] {
  return [ESC, 0x45, enabled ? 1 : 0];
}

export function setUnderline(enabled: boolean): number[] {
  return [ESC, 0x2d, enabled ? 1 : 0];
}

export function setTextSize(widthMultiplier = 1, heightMultiplier = 1): number[] {
  const width = normalizeMultiplier(widthMultiplier, 'widthMultiplier');
  const height = normalizeMultiplier(heightMultiplier, 'heightMultiplier');
  return [GS, 0x21, ((width - 1) << 4) | (height - 1)];
}

export function separatorLine(length = 32, char = '-'): number[] {
  const normalizedLength = normalizeByteRange(length, 'length');
  const glyph = char.at(0);
  if (!glyph) {
    throw printerError('INVALID_ARGUMENT', 'char must contain at least one character.');
  }
  return textBytes(`${glyph.repeat(normalizedLength)}\n`);
}

export function cutPaper(partial = false): number[] {
  return [GS, 0x56, partial ? 1 : 0];
}

export function openCashDrawerPulse(options: CashDrawerOptions = {}): number[] {
  const pin = options.pin === 5 ? 1 : 0;
  const onMs = normalizePulse(options.onMs ?? 120, 'onMs');
  const offMs = normalizePulse(options.offMs ?? 240, 'offMs');
  return [ESC, 0x70, pin, onMs, offMs];
}

function alignmentCode(alignment: PrinterAlignment): number {
  switch (alignment) {
    case 'left':
      return 0;
    case 'center':
      return 1;
    case 'right':
      return 2;
  }
}

function normalizeMultiplier(value: number, label: string): number {
  const normalized = Math.trunc(value);
  if (normalized < 1 || normalized > 8) {
    throw printerError('INVALID_ARGUMENT', `${label} must be between 1 and 8.`);
  }
  return normalized;
}

function normalizeByteRange(value: number, label: string): number {
  const normalized = Math.trunc(value);
  if (normalized < 1 || normalized > 255) {
    throw printerError('INVALID_ARGUMENT', `${label} must be between 1 and 255.`);
  }
  return normalized;
}

function normalizePulse(value: number, label: string): number {
  const normalized = Math.trunc(value / 2);
  if (normalized < 0 || normalized > 255) {
    throw printerError('INVALID_ARGUMENT', `${label} must be between 0 and 510.`);
  }
  return normalized;
}
