export const MAX_AMOUNT = 999_999_999_999

const numberFormatter = new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 0 })
const decimalFormatter = new Intl.NumberFormat('fa-IR', { maximumFractionDigits: 1 })

/** Accept Persian and Arabic-Indic digits everywhere a number is entered. */
export function normalizeDigits(value: string): string {
  return value
    .replace(/[۰-۹]/g, (digit) => String(digit.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (digit) => String(digit.charCodeAt(0) - 0x0660))
}

export function parseAmount(value: string): number | null {
  const normalized = normalizeDigits(value).trim().replace(/[٬,\s]/g, '')
  if (!/^\d+$/.test(normalized)) return null
  const amount = Number(normalized)
  if (!Number.isSafeInteger(amount) || amount <= 0 || amount > MAX_AMOUNT) return null
  return amount
}

export function formatNumber(value: number): string {
  return numberFormatter.format(value)
}

/** Digits without grouping separators, for years and other non-quantity numbers. */
export function formatDigits(value: string | number): string {
  return String(value).replace(/\d/g, (digit) => String.fromCharCode(0x06f0 + Number(digit)))
}

export function formatMoney(value: number): string {
  return `${formatNumber(value)} تومان`
}

export function formatCompact(value: number): string {
  const absolute = Math.abs(value)
  if (absolute >= 1_000_000_000) return `${decimalFormatter.format(value / 1_000_000_000)} میلیارد`
  if (absolute >= 1_000_000) return `${decimalFormatter.format(value / 1_000_000)} میلیون`
  if (absolute >= 1_000) return `${numberFormatter.format(value / 1_000)} هزار`
  return formatNumber(value)
}

export function formatPercent(value: number): string {
  return `${formatNumber(Math.round(value))}٪`
}

/** Quote CSV cells and neutralize spreadsheet formulas in user-entered text. */
export function csvCell(value: string | number): string {
  let text = String(value)
  if (typeof value === 'string' && /^\s*[=+\-@]|^[\t\r]/.test(text)) text = `'${text}`
  return `"${text.replace(/"/g, '""')}"`
}
