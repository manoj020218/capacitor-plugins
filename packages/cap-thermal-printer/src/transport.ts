import type { PrinterConnectionOptions, PrinterDevice, PrinterStatus, PrinterTransportType, WriteOptions } from './definitions';

export interface PrinterTransport {
  readonly type: PrinterTransportType;
  connect(options: PrinterConnectionOptions): Promise<PrinterStatus>;
  disconnect(): Promise<void>;
  getDevices(): Promise<PrinterDevice[]>;
  getStatus(): Promise<PrinterStatus>;
  isConnected(): Promise<boolean>;
  write(options: WriteOptions): Promise<number>;
}

export function createDisconnectedStatus(transport?: PrinterTransportType): PrinterStatus {
  return {
    connected: false,
    transport,
    connectionState: 'disconnected',
  };
}
