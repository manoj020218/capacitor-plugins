import { describe, expect, it } from 'vitest';
import { EscPosBuilder, buildPrintBarcodeData, buildPrintQRCodeData, buildPrintTextData } from './builder';

describe('EscPosBuilder', () => {
  it('builds a basic formatted receipt payload', () => {
    const bytes = new EscPosBuilder()
      .initialize()
      .align('center')
      .bold()
      .text('JENIX')
      .newline()
      .bold(false)
      .align('left')
      .separator(4)
      .feed(2)
      .cut()
      .build();

    expect(bytes).toEqual([
      0x1b, 0x40,
      0x1b, 0x61, 0x01,
      0x1b, 0x45, 0x01,
      74, 69, 78, 73, 88,
      0x0a,
      0x1b, 0x45, 0x00,
      0x1b, 0x61, 0x00,
      45, 45, 45, 45, 10,
      0x1b, 0x64, 0x02,
      0x1d, 0x56, 0x00,
    ]);
  });

  it('builds printText payloads with temporary alignment', () => {
    expect(buildPrintTextData({ text: 'Hi', alignment: 'right' })).toEqual([
      0x1b, 0x61, 0x02,
      72, 105,
      0x1b, 0x61, 0x00,
    ]);
  });

  it('builds QR and barcode helper payloads', () => {
    expect(buildPrintQRCodeData({ data: 'OK' })).toEqual(new EscPosBuilder().qrCode({ data: 'OK' }).build());
    expect(buildPrintBarcodeData({ data: '123', format: 'code128' })).toEqual(
      new EscPosBuilder().barcode({ data: '123', format: 'code128' }).build(),
    );
  });
});
