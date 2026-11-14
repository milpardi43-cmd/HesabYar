export type TransactionType = 'income' | 'expense'

export type CategoryIcon =
  | 'basket'
  | 'home'
  | 'bus'
  | 'bag'
  | 'receipt'
  | 'heart'
  | 'ticket'
  | 'book'
  | 'users'
  | 'shapes'
  | 'briefcase'
  | 'laptop'
  | 'chart'
  | 'gift'
  | 'plus'

export interface Category {
  id: string
  label: string
  type: TransactionType
  color: string
  icon: CategoryIcon
}

export interface Transaction {
  id: string
  type: TransactionType
  amount: number
  title: string
  note: string
  categoryId: string
  /** Gregorian ISO calendar date (YYYY-MM-DD), displayed as Jalali in the UI. */
  date: string
  createdAt: string
  updatedAt: string
}

export type TransactionInput = Omit<Transaction, 'id' | 'createdAt' | 'updatedAt'>

export interface LedgerData {
  schemaVersion: 1
  currency: 'toman'
  isDemo: boolean
  transactions: Transaction[]
  /** Monthly recurring limit per expense category, in whole tomans. */
  budgets: Record<string, number>
}

export interface BackupFile extends LedgerData {
  exportedAt: string
  app: 'HesabYar'
}

export type StorageIssue = 'corrupt' | 'unavailable' | 'write' | null

export interface ActionResult {
  ok: boolean
  message?: string
}
