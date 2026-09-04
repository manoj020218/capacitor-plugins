import { WebPlugin } from '@capacitor/core';
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
  PrinterStatus,
  ScanOptions,
  ThermalPrinterPlugin,
  WriteOptions,
} from './definitions';

const unsupported = <T>() => Promise.reject<T>(new Error('Android only plugin'));

export class ThermalPrinterWeb extends WebPlugin implements ThermalPrinterPlugin {
  scan(_options?: ScanOptions) { return unsupported<{ devices: [] }>(); }
  stopScan() { return unsupported<{ scanning: false }>(); }
  getDevices(_options?: GetDevicesOptions) { return unsupported<{ devices: [] }>(); }
  connect(_options: PrinterConnectionOptions): Promise<PrinterStatus> { return unsupported(); }
  disconnect() { return unsupported<{ connected: false; connectionState: 'disconnected' }>(); }
  isConnected() { return unsupported<{ connected: boolean }>(); }
  getStatus(): Promise<PrinterStatus> { return unsupported(); }
  write(_options: WriteOptions) { return unsupported<{ written: number }>(); }
  printText(_options: PrintTextOptions) { return unsupported<{ written: number }>(); }
  feed(_options?: FeedOptions) { return unsupported<{ written: number }>(); }
  printQRCode(_options: PrintQRCodeOptions) { return unsupported<{ written: number }>(); }
  printBarcode(_options: PrintBarcodeOptions) { return unsupported<{ written: number }>(); }
  printImage(_options: PrintImageOptions) { return unsupported<{ written: number }>(); }
  cut(_options?: CutOptions) { return unsupported<{ written: number }>(); }
  openCashDrawer(_options?: CashDrawerOptions) { return unsupported<{ written: number }>(); }
}
