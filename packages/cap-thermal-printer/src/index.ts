import type { PluginListenerHandle } from '@capacitor/core';
import { registerPlugin } from '@capacitor/core';
import type {
  CashDrawerOptions,
  CutOptions,
  FeedOptions,
  GetDevicesOptions,
  PrintBarcodeOptions,
  PrintImageOptions,
  PrintQRCodeOptions,
  PrintTextOptions,
  PrinterConnectionOptions,
  PrinterDevice,
  PrinterStatus,
  ScanOptions,
  ThermalPrinterPlugin,
  WriteOptions,
} from './definitions';
import { buildPrintBarcodeData, buildPrintQRCodeData, buildPrintTextData, EscPosBuilder } from './escpos';

const nativeThermalPrinter = registerPlugin<ThermalPrinterPlugin>('JenixThermalPrinter', {
  web: () => import('./web').then((m) => new m.ThermalPrinterWeb()),
});

export * from './bytes';
export * from './definitions';
export * from './escpos';
export * from './errors';
export * from './profile';
export * from './transport';

class ThermalPrinterClient implements ThermalPrinterPlugin {
  addListener = nativeThermalPrinter.addListener.bind(nativeThermalPrinter) as ThermalPrinterPlugin['addListener'];

  scan(options?: ScanOptions): Promise<{ devices: PrinterDevice[] }> {
    return nativeThermalPrinter.scan(options);
  }

  stopScan(): Promise<{ scanning: false }> {
    return nativeThermalPrinter.stopScan();
  }

  getDevices(options?: GetDevicesOptions): Promise<{ devices: PrinterDevice[] }> {
    return nativeThermalPrinter.getDevices(options);
  }

  connect(options: PrinterConnectionOptions): Promise<PrinterStatus> {
    return nativeThermalPrinter.connect(options);
  }

  disconnect(): Promise<{ connected: false; connectionState: 'disconnected' }> {
    return nativeThermalPrinter.disconnect();
  }

  isConnected(): Promise<{ connected: boolean }> {
    return nativeThermalPrinter.isConnected();
  }

  getStatus(): Promise<PrinterStatus> {
    return nativeThermalPrinter.getStatus();
  }

  write(options: WriteOptions): Promise<{ written: number }> {
    return nativeThermalPrinter.write(options);
  }

  printText(options: PrintTextOptions): Promise<{ written: number }> {
    return this.write({ data: buildPrintTextData(options) });
  }

  feed(options?: FeedOptions): Promise<{ written: number }> {
    return this.write({ data: new EscPosBuilder().feed(options?.lines ?? 1).build() });
  }

  printQRCode(options: PrintQRCodeOptions): Promise<{ written: number }> {
    return this.write({ data: buildPrintQRCodeData(options) });
  }

  printBarcode(options: PrintBarcodeOptions): Promise<{ written: number }> {
    return this.write({ data: buildPrintBarcodeData(options) });
  }

  printImage(options: PrintImageOptions): Promise<{ written: number }> {
    return nativeThermalPrinter.printImage(options);
  }

  cut(options?: CutOptions): Promise<{ written: number }> {
    return this.write({ data: new EscPosBuilder().cut(options?.partial ?? false).build() });
  }

  openCashDrawer(options?: CashDrawerOptions): Promise<{ written: number }> {
    return this.write({ data: new EscPosBuilder().cashDrawer(options).build() });
  }
}

const ThermalPrinter = new ThermalPrinterClient();

export { ThermalPrinter };
