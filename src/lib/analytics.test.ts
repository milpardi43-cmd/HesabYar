import { describe, expect, it } from 'vitest'
import { makeLedger, makeTransaction } from '../test/fixtures'
import { budgetSummary, categoryTotals, filterTransactions, inMonth, monthlySeries, percentChange, totals } from './analytics'

const transactions = [
  makeTransaction({ id: '1', type: 'income', amount: 10_000_000, categoryId: 'salary', title: 'حقوق', date: '2026-11-01' }),
  makeTransaction({ id: '2', amount: 2_000_000, categoryId: 'food', title: 'خرید خانه', date: '2026-11-14' }),
  makeTransaction({ id: '3', amount: 500_000, categoryId: 'transport', title: 'مترو', date: '2026-11-10' }),
  makeTransaction({ id: '4', amount: 700_000, categoryId: 'food', title: 'نان', date: '2026-10-10' }),
]

describe('financial analytics', () => {
  it('sums income, expense and net balance', () => {
    expect(totals(transactions)).toEqual({ income: 10_000_000, expense: 3_200_000, balance: 6_800_000 })
  })

  it('filters by Jalali month, type, category and Persian search text', () => {
    expect(inMonth(transactions, '1405-08')).toHaveLength(3)
    expect(filterTransactions(transactions, { type: 'expense', month: '1405-08' })).toHaveLength(2)
    expect(filterTransactions(transactions, { categoryId: 'food' })).toHaveLength(2)
    expect(filterTransactions(transactions, { query: 'حقوق' })).toHaveLength(1)
    expect(filterTransactions(transactions, { month: 'all', type: 'all', categoryId: 'all' })).toHaveLength(4)
  })

  it('breaks expenses down by category with percentages', () => {
    const items = categoryTotals(inMonth(transactions, '1405-08'), 'expense')
    expect(items[0]).toMatchObject({ categoryId: 'food', amount: 2_000_000, percent: 80 })
    expect(items[1]).toMatchObject({ categoryId: 'transport', amount: 500_000, percent: 20 })
  })

  it('builds a dense monthly series including empty months', () => {
    expect(monthlySeries(transactions, ['1405-07', '1405-08'])).toEqual([
      { key: '1405-07', income: 0, expense: 700_000 },
      { key: '1405-08', income: 10_000_000, expense: 2_500_000 },
    ])
  })

  it('summarizes only budgeted categories and detects overspend', () => {
    const data = makeLedger({ transactions, budgets: { food: 1_500_000, transport: 1_000_000 } })
    const summary = budgetSummary(data, '1405-08')
    expect(summary.limit).toBe(2_500_000)
    expect(summary.spent).toBe(2_500_000)
    expect(summary.remaining).toBe(0)
    expect(summary.items.find((item) => item.categoryId === 'food')?.percent).toBeCloseTo(133.333, 2)
  })

  it('does not divide by zero when comparing months', () => {
    expect(percentChange(100, 0)).toBeNull()
    expect(percentChange(150, 100)).toBe(50)
    expect(percentChange(80, 100)).toBe(-20)
  })
})
