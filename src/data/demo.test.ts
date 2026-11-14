import { describe, expect, it } from 'vitest'
import { createDemoLedger } from './demo'
import { parseLedger } from '../lib/storage'
import { todayISO } from '../lib/dates'

describe('demo ledger', () => {
  it('is valid, labelled, and never contains future transactions', () => {
    const now = new Date(2026, 10, 14, 12)
    const demo = createDemoLedger(now)
    expect(demo.isDemo).toBe(true)
    expect(() => parseLedger(demo)).not.toThrow()
    expect(demo.transactions.every((transaction) => transaction.date <= todayISO(now))).toBe(true)
    expect(demo.transactions.some((transaction) => transaction.type === 'income')).toBe(true)
    expect(demo.transactions.some((transaction) => transaction.type === 'expense')).toBe(true)
  })
})
