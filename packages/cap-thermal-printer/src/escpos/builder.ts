import type { CashDrawerOptions, PrintBarcodeOptions, PrintQRCodeOptions, PrintTextOptions, PrinterAlignment } from '../definitions';
import { barcodeBytes } from './barcode';
import {
  cutPaper,
  feedLines,
  initializePrinter,
  lineFeed,
  openCashDrawerPulse,
  separatorLine,
  setAlignment,
  setBold,
  setTextSize,
  setUnderline,
  textBytes,
} from './commands';
import { qrCodeBytes } from './qrcode';

export class EscPosBuilder {
  private readonly buffer: number[] = [];

  initialize(): this {
    return this.append(initializePrinter());
  }

  text(text: string, encoding = 'ascii'): this {
    return this.append(textBytes(text, encoding));
  }

  newline(count = 1): this {
    return this.append(lineFeed(count));
  }

  feed(lines = 1): this {
    return this.append(feedLines(lines));
  }

  align(alignment: PrinterAlignment): this {
    return this.append(setAlignment(alignment));
  }

  bold(enabled = true): this {
    return this.append(setBold(enabled));
  }

  underline(enabled = true): this {
    return this.append(setUnderline(enabled));
  }

  size(widthMultiplier = 1, heightMultiplier = 1): this {
    return this.append(setTextSize(widthMultiplier, heightMultiplier));
  }

  separator(length = 32, char = '-'): this {
    return this.append(separatorLine(length, char));
  }

  qrCode(options: PrintQRCodeOptions): this {
    return this.append(qrCodeBytes(options));
  }

  barcode(options: PrintBarcodeOptions): this {
    return this.append(barcodeBytes(options));
  }

  cut(partial = false): this {
    return this.append(cutPaper(partial));
  }

  cashDrawer(options?: CashDrawerOptions): this {
    return this.append(openCashDrawerPulse(options));
  }

  build(): number[] {
    return [...this.buffer];
  }

  private append(bytes: number[]): this {
    this.buffer.push(...bytes);
    return this;
  }
}

export function buildPrintTextData(options: PrintTextOptions): number[] {
  const builder = new EscPosBuilder();
  if (options.alignment) {
    builder.align(options.alignment);
  }
  builder.text(options.text, options.encoding);
  if (options.alignment && options.alignment !== 'left') {
    builder.align('left');
  }
  return builder.build();
}

export function buildPrintQRCodeData(options: PrintQRCodeOptions): number[] {
  return new EscPosBuilder().qrCode(options).build();
}

export function buildPrintBarcodeData(options: PrintBarcodeOptions): number[] {
  return new EscPosBuilder().barcode(options).build();
}
