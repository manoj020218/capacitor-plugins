import { describe, expect, it } from 'vitest';
import { chunkBytes, encodeAscii, toByteNumbers, toUint8Array } from './bytes';

describe('byte helpers', () => {
  it('converts number arrays to Uint8Array', () => {
    expect(Array.from(toUint8Array([27, 64, 10]))).toEqual([27, 64, 10]);
  });

  it('rejects invalid byte values', () => {
    expect(() => toUint8Array([256])).toThrow('Byte values must be integers between 0 and 255.');
  });

  it('chunks byte arrays without losing order', () => {
    const chunks = chunkBytes([1, 2, 3, 4, 5], 2).map((chunk) => Array.from(chunk));
    expect(chunks).toEqual([[1, 2], [3, 4], [5]]);
  });

  it('normalizes Uint8Array to number arrays', () => {
    expect(toByteNumbers(new Uint8Array([10, 20]))).toEqual([10, 20]);
  });

  it('encodes unsupported characters as question marks for ASCII fallback', () => {
    expect(Array.from(encodeAscii('A₹B'))).toEqual([65, 63, 66]);
  });
});
