import { describe, expect, it } from 'vitest';
import { ThermalPrinterError } from '../errors';
import {
  cutPaper,
  feedLines,
  initializePrinter,
  openCashDrawerPulse,
  separatorLine,
  setAlignment,
  setBold,
  setTextSize,
  setUnderline,
  textBytes,
} from './commands';

describe('ESC/POS commands', () => {
  it('builds printer init and text-style commands', () => {
    expect(initializePrinter()).toEqual([0x1b, 0x40]);
    expect(setAlignment('center')).toEqual([0x1b, 0x61, 0x01]);
    expect(setBold(true)).toEqual([0x1b, 0x45, 0x01]);
    expect(setUnderline(false)).toEqual([0x1b, 0x2d, 0x00]);
    expect(setTextSize(2, 3)).toEqual([0x1d, 0x21, 0x12]);
  });

  it('encodes text and separator output with ASCII fallback', () => {
    expect(textBytes('A₹B')).toEqual([65, 63, 66]);
    expect(separatorLine(4, '=')).toEqual([61, 61, 61, 61, 10]);
  });

  it('builds feed, cut, and cash-drawer commands', () => {
    expect(feedLines(3)).toEqual([0x1b, 0x64, 0x03]);
    expect(cutPaper(true)).toEqual([0x1d, 0x56, 0x01]);
    expect(openCashDrawerPulse({ pin: 5, onMs: 100, offMs: 200 })).toEqual([0x1b, 0x70, 0x01, 50, 100]);
  });

  it('rejects invalid encoding and sizing values', () => {
    expect(() => textBytes('Hello', 'utf-8')).toThrow(ThermalPrinterError);
    expect(() => setTextSize(0, 1)).toThrow('widthMultiplier must be between 1 and 8.');
  });
});
