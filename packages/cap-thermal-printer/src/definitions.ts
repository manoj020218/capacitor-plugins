import type { PluginListenerHandle } from '@capacitor/core';

export type PrinterTransportType = 'ble' | 'usb';
export type PrinterAlignment = 'left' | 'center' | 'right';
export type PrinterConnectionState = 'disconnected' | 'connecting' | 'connected' | 'disconnecting' | 'reconnecting';
export type PrinterPaperWidth = 58 | 80;
export type ScanStopReason = 'manual' | 'timeout' | 'restarted' | 'failed';
export type PrinterErrorCode =
  | 'PERMISSION_DENIED'
  | 'DEVICE_NOT_FOUND'
  | 'CONNECTION_FAILED'
  | 'CONNECTION_TIMEOUT'
  | 'NOT_CONNECTED'
  | 'WRITE_FAILED'
  | 'NO_WRITABLE_CHARACTERISTIC'
  | 'USB_PERMISSION_DENIED'
  | 'USB_INTERFACE_NOT_FOUND'
  | 'USB_ENDPOINT_NOT_FOUND'
  | 'UNSUPPORTED_OPERATION'
  | 'INVALID_ARGUMENT';

export interface PrinterConnectionIssue {
  code: PrinterErrorCode;
  message: string;
}

export interface PrinterDevice {
  id: string;
  name?: string;
  transport: PrinterTransportType;
  connected?: boolean;
  permissionGranted?: boolean;
  rssi?: number;
  vendorId?: number;
  productId?: number;
  serviceUuid?: string;
  writeCharacteristicUuid?: string;
}

export interface ScanOptions {
  transport?: PrinterTransportType;
  namePrefix?: string;
  serviceUuid?: string;
  allowUnnamed?: boolean;
  timeoutMs?: number;
}

export interface GetDevicesOptions {
  transport?: PrinterTransportType;
}

export interface BleConnectionOptions {
  transport: 'ble';
  deviceId: string;
  serviceUuid?: string;
  writeCharacteristicUuid?: string;
  timeoutMs?: number;
  autoReconnect?: boolean;
  reconnectAttempts?: number;
  reconnectDelayMs?: number;
}

export interface UsbConnectionOptions {
  transport: 'usb';
  deviceId?: string;
  vendorId?: number;
  productId?: number;
  timeoutMs?: number;
}

export type PrinterConnectionOptions = BleConnectionOptions | UsbConnectionOptions;

export interface PrinterStatus {
  connected: boolean;
  transport?: PrinterTransportType;
  device?: PrinterDevice;
  connectionState?: PrinterConnectionState;
  reconnectAttempt?: number;
  reconnectMaxAttempts?: number;
  lastError?: PrinterConnectionIssue;
}

interface BasePrinterProfile {
  id: string;
  name: string;
  transport: PrinterTransportType;
  paperWidth?: PrinterPaperWidth;
  charsPerLine?: number;
  timeoutMs?: number;
}

export interface BlePrinterProfile extends BasePrinterProfile {
  transport: 'ble';
  deviceId: string;
  serviceUuid?: string;
  writeCharacteristicUuid?: string;
  autoReconnect?: boolean;
  reconnectAttempts?: number;
  reconnectDelayMs?: number;
}

export interface UsbPrinterProfile extends BasePrinterProfile {
  transport: 'usb';
  deviceId?: string;
  vendorId?: number;
  productId?: number;
}

export type PrinterProfile = BlePrinterProfile | UsbPrinterProfile;

export interface ScanStoppedEvent {
  reason: ScanStopReason;
  devices: PrinterDevice[];
  errorCode?: number;
}

export interface ConnectionErrorEvent {
  code: PrinterErrorCode;
  message: string;
  transport: PrinterTransportType;
}

export interface WriteOptions {
  data: number[];
  chunkSize?: number;
}

export interface PrintTextOptions {
  text: string;
  encoding?: string;
  alignment?: PrinterAlignment;
}

export interface FeedOptions {
  lines?: number;
}

export interface PrintQRCodeOptions {
  data: string;
  size?: number;
  alignment?: PrinterAlignment;
}

export interface PrintBarcodeOptions {
  data: string;
  format?: 'code128';
  width?: number;
  height?: number;
  alignment?: PrinterAlignment;
}

export interface PrintImageOptions {
  base64: string;
  width?: number;
  alignment?: PrinterAlignment;
}

export interface CutOptions {
  partial?: boolean;
}

export interface CashDrawerOptions {
  pin?: 2 | 5;
  onMs?: number;
  offMs?: number;
}

export interface ThermalPrinterPlugin {
  addListener(eventName: 'deviceFound', listenerFunc: (device: PrinterDevice) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'scanStopped', listenerFunc: (event: ScanStoppedEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'connected', listenerFunc: (status: PrinterStatus) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'disconnected', listenerFunc: (status: PrinterStatus) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'connectionError', listenerFunc: (event: ConnectionErrorEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'usbAttached', listenerFunc: (device: PrinterDevice) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'usbDetached', listenerFunc: (device: PrinterDevice) => void): Promise<PluginListenerHandle>;
  scan(options?: ScanOptions): Promise<{ devices: PrinterDevice[] }>;
  stopScan(): Promise<{ scanning: false }>;
  getDevices(options?: GetDevicesOptions): Promise<{ devices: PrinterDevice[] }>;
  connect(options: PrinterConnectionOptions): Promise<PrinterStatus>;
  disconnect(): Promise<{ connected: false; connectionState: 'disconnected' }>;
  isConnected(): Promise<{ connected: boolean }>;
  getStatus(): Promise<PrinterStatus>;
  write(options: WriteOptions): Promise<{ written: number }>;
  printText(options: PrintTextOptions): Promise<{ written: number }>;
  feed(options?: FeedOptions): Promise<{ written: number }>;
  printQRCode(options: PrintQRCodeOptions): Promise<{ written: number }>;
  printBarcode(options: PrintBarcodeOptions): Promise<{ written: number }>;
  printImage(options: PrintImageOptions): Promise<{ written: number }>;
  cut(options?: CutOptions): Promise<{ written: number }>;
  openCashDrawer(options?: CashDrawerOptions): Promise<{ written: number }>;
}
