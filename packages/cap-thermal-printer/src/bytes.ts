const INVALID_BYTE_ERROR = 'Byte values must be integers between 0 and 255.';

export type ByteSource = number[] | Uint8Array | ArrayBuffer;

export function toUint8Array(value: ByteSource): Uint8Array {
  if (value instanceof Uint8Array) {
    return new Uint8Array(value);
  }

  if (value instanceof ArrayBuffer) {
    return new Uint8Array(value);
  }

  if (!Array.isArray(value)) {
    throw new TypeError('Expected an array of bytes, Uint8Array, or ArrayBuffer.');
  }

  return Uint8Array.from(value.map(assertByte));
}

export function toByteNumbers(value: ByteSource): number[] {
  return Array.from(toUint8Array(value));
}

export function chunkBytes(value: ByteSource, chunkSize: number): Uint8Array[] {
  const bytes = toUint8Array(value);
  const size = Math.trunc(chunkSize);

  if (size < 1) {
    throw new RangeError('Chunk size must be at least 1.');
  }

  if (bytes.length === 0) {
    return [];
  }

  const chunks: Uint8Array[] = [];
  for (let index = 0; index < bytes.length; index += size) {
    chunks.push(bytes.slice(index, index + size));
  }
  return chunks;
}

export function encodeAscii(text: string): Uint8Array {
  return Uint8Array.from(Array.from(text, encodeAsciiCodePoint));
}

function assertByte(value: number): number {
  if (!Number.isInteger(value) || value < 0 || value > 0xff) {
    throw new RangeError(INVALID_BYTE_ERROR);
  }
  return value;
}

function encodeAsciiCodePoint(char: string): number {
  const codePoint = char.codePointAt(0) ?? 0x3f;
  return codePoint <= 0x7f ? codePoint : 0x3f;
}
