import { describe, expect, it } from 'vitest'
import { decimal } from '../src/decimal'

describe('financial display', () => {
  it('preserves precise large amounts and trims only fractional zeroes', () => {
    expect(decimal('99999999999999.123456', 'KRW')).toBe('99,999,999,999,999.123456 KRW')
    expect(decimal('-1234.050000', 'USD')).toBe('-1,234.05 USD')
    expect(decimal('1000.000000')).toBe('1,000')
    expect(decimal('0.000001')).toBe('0.000001')
    expect(decimal(null)).toBe('미제공')
  })
})
