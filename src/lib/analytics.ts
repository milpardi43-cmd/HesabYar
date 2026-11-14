import type { LedgerData, Transaction, TransactionType } from '../types'
import { getCategory } from '../data/categories'
import { monthKey } from './dates'

export interface Totals {
  income: number
  expense: number
  balance: number
}

export interface CategoryTotal {
  categoryId: string
  label: string
  color: string
  amount: number
  percent: number
}

export interface MonthSeries {
  key: string
  income: number
  expense: number
}

export interface TransactionFilters {
  query?: string
  type?: TransactionType | 'all'
  categoryId?: string
  month?: string
}

export function totals(transactions: Transaction[]): Totals {
  let income = 0
  let expense = 0
  for (const transaction of transactions) {
    if (transaction.type === 'income') income += transaction.amount
    else expense += transaction.amount
  }
  return { income, expense, balance: income - expense }
}

export function inMonth(transactions: Transaction[], key: string): Transaction[] {
  return transactions.filter((transaction) => monthKey(transaction.date) === key)
}

export function sortTransactions(transactions: Transaction[]): Transaction[] {
  return [...transactions].sort(
    (a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id),
  )
}

export function filterTransactions(transactions: Transaction[], filters: TransactionFilters): Transaction[] {
  const query = filters.query?.trim().toLocaleLowerCase('fa') ?? ''
  return sortTransactions(transactions).filter((transaction) => {
    if (filters.type && filters.type !== 'all' && transaction.type !== filters.type) return false
    if (filters.categoryId && filters.categoryId !== 'all' && transaction.categoryId !== filters.categoryId) return false
    if (filters.month && filters.month !== 'all' && monthKey(transaction.date) !== filters.month) return false
    if (query) {
      const haystack = `${transaction.title} ${transaction.note} ${getCategory(transaction.categoryId).label}`.toLocaleLowerCase('fa')
      if (!haystack.includes(query)) return false
    }
    return true
  })
}

export function categoryTotals(transactions: Transaction[], type: TransactionType): CategoryTotal[] {
  const sums = new Map<string, number>()
  let total = 0
  for (const transaction of transactions) {
    if (transaction.type !== type) continue
    sums.set(transaction.categoryId, (sums.get(transaction.categoryId) ?? 0) + transaction.amount)
    total += transaction.amount
  }
  return [...sums.entries()]
    .map(([categoryId, amount]) => {
      const category = getCategory(categoryId)
      return {
        categoryId,
        label: category.label,
        color: category.color,
        amount,
        percent: total > 0 ? amount / total * 100 : 0,
      }
    })
    .sort((a, b) => b.amount - a.amount)
}

export function monthlySeries(transactions: Transaction[], months: string[]): MonthSeries[] {
  const series = new Map(months.map((key) => [key, { key, income: 0, expense: 0 }]))
  for (const transaction of transactions) {
    const entry = series.get(monthKey(transaction.date))
    if (entry) entry[transaction.type] += transaction.amount
  }
  return [...series.values()]
}

export function budgetSummary(data: LedgerData, key: string) {
  const expenses = inMonth(data.transactions, key).filter((transaction) => transaction.type === 'expense')
  const spentByCategory = new Map<string, number>()
  for (const transaction of expenses) {
    spentByCategory.set(transaction.categoryId, (spentByCategory.get(transaction.categoryId) ?? 0) + transaction.amount)
  }
  const items = Object.entries(data.budgets).map(([categoryId, limit]) => {
    const spent = spentByCategory.get(categoryId) ?? 0
    return {
      categoryId,
      limit,
      spent,
      remaining: limit - spent,
      percent: limit > 0 ? spent / limit * 100 : 0,
    }
  })
  const limit = items.reduce((sum, item) => sum + item.limit, 0)
  const spent = items.reduce((sum, item) => sum + item.spent, 0)
  return { items, limit, spent, remaining: limit - spent, percent: limit > 0 ? spent / limit * 100 : 0 }
}

export function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return null
  return (current - previous) / previous * 100
}
