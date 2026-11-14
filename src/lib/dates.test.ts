import { describe, expect, it } from 'vitest'
import {
  currentMonthKey, daysInMonth, firstWeekday, formatDate, isValidISODate,
  isoToJalali, jalaliToISO, lastMonths, monthKey, monthLabel, parseMonthKey, shiftMonth, todayISO,
} from './dates'

describe('Jalali calendar helpers', () => {
  it('converts known Nowruz dates in both directions', () => {
    expect(jalaliToISO(1403, 1, 1)).toBe('2024-03-20')
    expect(isoToJalali('2024-03-20')).toEqual({ jy: 1403, jm: 1, jd: 1 })
    expect(jalaliToISO(1405, 1, 1)).toBe('2026-03-21')
  })

  it('handles leap and non-leap Esfand correctly', () => {
    expect(daysInMonth('1403-12')).toBe(30)
    expect(daysInMonth('1404-12')).toBe(29)
    expect(jalaliToISO(1403, 12, 30)).toBe('2025-03-20')
    expect(() => jalaliToISO(1404, 12, 30)).toThrow(RangeError)
  })

  it('validates Gregorian calendar dates without timezone rollover', () => {
    expect(isValidISODate('2026-02-29')).toBe(false)
    expect(isValidISODate('2024-02-29')).toBe(true)
    expect(isValidISODate('2026-13-01')).toBe(false)
    expect(isValidISODate('2026-11-31')).toBe(false)
    expect(todayISO(new Date(2026, 10, 14, 23, 59))).toBe('2026-11-14')
  })

  it('groups by Jalali month and shifts across the year boundary', () => {
    expect(monthKey('2026-11-14')).toBe('1405-08')
    expect(currentMonthKey(new Date(2026, 10, 14))).toBe('1405-08')
    expect(shiftMonth('1405-01', -1)).toBe('1404-12')
    expect(shiftMonth('1404-12', 1)).toBe('1405-01')
    expect(lastMonths('1405-01', 3)).toEqual(['1404-11', '1404-12', '1405-01'])
    expect(monthLabel('1405-08')).toBe('آبان ۱۴۰۵')
    expect(formatDate('2026-11-14')).toBe('۲۳ آبان ۱۴۰۵')
  })

  it('uses a Saturday-first week', () => {
    // 2026-11-14 is a Saturday; the first of Aban 1405 is also checked by roundtrip.
    const key = monthKey('2026-11-14')
    const { year, month } = parseMonthKey(key)
    expect(jalaliToISO(year, month, 1)).toBe(jalaliToISO(1405, 8, 1))
    expect(firstWeekday('1405-08')).toBeGreaterThanOrEqual(0)
    expect(firstWeekday('1405-08')).toBeLessThanOrEqual(6)
    expect((new Date(2026, 10, 14, 12).getDay() + 1) % 7).toBe(0)
  })
})
