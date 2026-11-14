import { isValidJalaaliDate, jalaaliMonthLength, toGregorian, toJalaali } from 'jalaali-js'
import { formatDigits, normalizeDigits } from './format'

export const JALALI_MONTHS = [
  'فروردین', 'اردیبهشت', 'خرداد', 'تیر', 'مرداد', 'شهریور',
  'مهر', 'آبان', 'آذر', 'دی', 'بهمن', 'اسفند',
] as const

export const WEEKDAYS = ['ش', 'ی', 'د', 'س', 'چ', 'پ', 'ج'] as const
export const MIN_JALALI_YEAR = 1300
export const MAX_JALALI_YEAR = 1500

const pad = (value: number) => String(value).padStart(2, '0')

export function todayISO(now = new Date()): string {
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

export function isValidISODate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const [year, month, day] = value.split('-').map(Number)
  if (year < 1900 || year > 2200) return false
  const date = new Date(Date.UTC(year, month - 1, day))
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  )
}

export function isoToJalali(iso: string) {
  if (!isValidISODate(iso)) throw new RangeError(`Invalid calendar date: ${iso}`)
  const [year, month, day] = iso.split('-').map(Number)
  return toJalaali(year, month, day)
}

export function jalaliToISO(year: number, month: number, day: number): string {
  if (
    year < MIN_JALALI_YEAR || year > MAX_JALALI_YEAR ||
    !isValidJalaaliDate(year, month, day)
  ) {
    throw new RangeError(`Invalid Jalali date: ${year}/${month}/${day}`)
  }
  const gregorian = toGregorian(year, month, day)
  return `${gregorian.gy}-${pad(gregorian.gm)}-${pad(gregorian.gd)}`
}

export function monthKey(iso: string): string {
  const { jy, jm } = isoToJalali(iso)
  return `${jy}-${pad(jm)}`
}

export function currentMonthKey(now = new Date()): string {
  return monthKey(todayISO(now))
}

export function parseMonthKey(key: string): { year: number; month: number } {
  const match = /^(\d{4})-(\d{2})$/.exec(normalizeDigits(key))
  if (!match) throw new RangeError(`Invalid month: ${key}`)
  const year = Number(match[1])
  const month = Number(match[2])
  if (year < MIN_JALALI_YEAR || year > MAX_JALALI_YEAR || month < 1 || month > 12) {
    throw new RangeError(`Invalid month: ${key}`)
  }
  return { year, month }
}

export function shiftMonth(key: string, delta: number): string {
  const { year, month } = parseMonthKey(key)
  const index = year * 12 + month - 1 + delta
  const nextYear = Math.floor(index / 12)
  const nextMonth = index % 12 + 1
  return `${nextYear}-${pad(nextMonth)}`
}

export function monthLabel(key: string, withYear = true): string {
  const { year, month } = parseMonthKey(key)
  return `${JALALI_MONTHS[month - 1]}${withYear ? ` ${formatDigits(year)}` : ''}`
}

export function monthShortLabel(key: string): string {
  const { month } = parseMonthKey(key)
  return JALALI_MONTHS[month - 1]
}

export function formatDate(iso: string): string {
  const { jy, jm, jd } = isoToJalali(iso)
  return `${formatDigits(jd)} ${JALALI_MONTHS[jm - 1]} ${formatDigits(jy)}`
}

export function formatFullDate(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number)
  return new Intl.DateTimeFormat('fa-IR', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
  }).format(new Date(year, month - 1, day, 12))
}

export function lastMonths(endKey: string, count: number): string[] {
  return Array.from({ length: count }, (_, index) => shiftMonth(endKey, index - count + 1))
}

export function daysInMonth(key: string): number {
  const { year, month } = parseMonthKey(key)
  return jalaaliMonthLength(year, month)
}

/** Saturday-first weekday index, independent of the viewer's timezone. */
export function firstWeekday(key: string): number {
  const { year: jalaliYear, month: jalaliMonth } = parseMonthKey(key)
  const iso = jalaliToISO(jalaliYear, jalaliMonth, 1)
  const [year, month, day] = iso.split('-').map(Number)
  return (new Date(year, month - 1, day, 12).getDay() + 1) % 7
}
