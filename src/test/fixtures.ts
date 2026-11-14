import type { LedgerData, Transaction } from '../types'

export function makeTransaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: 'tx-1',
    type: 'expense',
    amount: 250_000,
    title: 'خرید نان',
    note: '',
    categoryId: 'food',
    date: '2026-11-14',
    createdAt: '2026-11-14T10:00:00.000Z',
    updatedAt: '2026-11-14T10:00:00.000Z',
    ...overrides,
  }
}

export function makeLedger(overrides: Partial<LedgerData> = {}): LedgerData {
  return {
    schemaVersion: 1,
    currency: 'toman',
    isDemo: false,
    transactions: [makeTransaction()],
    budgets: { food: 1_000_000 },
    ...overrides,
  }
}
