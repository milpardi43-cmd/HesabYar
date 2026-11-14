import { describe, expect, it } from 'vitest'
import { csvCell, formatCompact, formatMoney, normalizeDigits, parseAmount } from './format'

describe('money and text formatting', () => {
  it('normalizes Persian and Arabic-Indic digits', () => {
    expect(normalizeDigits('۱۲۳٤٥٦789')).toBe('123456789')
  })

  it('parses grouped whole tomans from all supported digit forms', () => {
    expect(parseAmount('۲۵۰٬۰۰۰')).toBe(250_000)
    expect(parseAmount('٢٥٠,٠٠٠')).toBe(250_000)
    expect(parseAmount(' 250 000 ')).toBe(250_000)
  })

  it('rejects empty, fractional, negative, zero and oversized amounts', () => {
    for (const value of ['', ' ', '-1', '0', '1.5', '۱/۵', 'abc', '1e6', '1000000000000']) {
      expect(parseAmount(value)).toBeNull()
    }
  })

  it('formats Persian money and compact values', () => {
    expect(formatMoney(1234567)).toBe('۱٬۲۳۴٬۵۶۷ تومان')
    expect(formatCompact(24_300_000)).toContain('میلیون')
    expect(formatCompact(950_000)).toContain('هزار')
  })

  it('quotes CSV cells and neutralizes formulas', () => {
    expect(csvCell('سلام، "دنیا"')).toBe('"سلام، ""دنیا"""')
    expect(csvCell('=HYPERLINK("x")')).toBe('"\'=HYPERLINK(""x"")"')
    expect(csvCell('@cmd')).toBe('"\'@cmd"')
    expect(csvCell(250000)).toBe('"250000"')
  })
})
