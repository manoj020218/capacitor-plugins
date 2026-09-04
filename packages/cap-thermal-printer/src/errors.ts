import type { PrinterErrorCode } from './definitions';

const PRINTER_ERROR_CODES: PrinterErrorCode[] = [
  'PERMISSION_DENIED',
  'DEVICE_NOT_FOUND',
  'CONNECTION_FAILED',
  'CONNECTION_TIMEOUT',
  'NOT_CONNECTED',
  'WRITE_FAILED',
  'NO_WRITABLE_CHARACTERISTIC',
  'USB_PERMISSION_DENIED',
  'USB_INTERFACE_NOT_FOUND',
  'USB_ENDPOINT_NOT_FOUND',
  'UNSUPPORTED_OPERATION',
  'INVALID_ARGUMENT',
];

export class ThermalPrinterError extends Error {
  readonly code: PrinterErrorCode;
  readonly details?: Record<string, unknown>;

  constructor(code: PrinterErrorCode, message: string, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ThermalPrinterError';
    this.code = code;
    this.details = details;
  }
}

export function isPrinterErrorCode(value: unknown): value is PrinterErrorCode {
  return typeof value === 'string' && PRINTER_ERROR_CODES.includes(value as PrinterErrorCode);
}

export function printerError(
  code: PrinterErrorCode,
  message: string,
  details?: Record<string, unknown>,
): ThermalPrinterError {
  return new ThermalPrinterError(code, message, details);
}

export function toPrinterError(
  error: unknown,
  fallbackCode: PrinterErrorCode = 'CONNECTION_FAILED',
  fallbackMessage = 'Thermal printer request failed.',
): ThermalPrinterError {
  if (error instanceof ThermalPrinterError) {
    return error;
  }

  if (error instanceof Error) {
    return new ThermalPrinterError(fallbackCode, error.message || fallbackMessage);
  }

  return new ThermalPrinterError(fallbackCode, fallbackMessage, { cause: error });
}
