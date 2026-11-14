import type { LedgerData, StorageIssue, Transaction, TransactionInput } from '../types'
import { CATEGORY_MAP } from '../data/categories'
import { createDemoLedger } from '../data/demo'
import { MAX_AMOUNT } from './format'
import { isValidISODate } from './dates'

export const STORAGE_KEY = 'hesabyar:ledger:v1'
// Keeps even the worst-case aggregate below Number.MAX_SAFE_INTEGER.
export const MAX_TRANSACTIONS = 5_000
export const MAX_BACKUP_BYTES = 5 * 1024 * 1024

type StorageAdapter = Pick<Storage, 'getItem' | 'setItem'>

export interface LoadedLedger {
  data: LedgerData
  issue: StorageIssue
  recoveryRaw: string | null
}

export function createEmptyLedger(): LedgerData {
  return { schemaVersion: 1, currency: 'toman', isDemo: false, transactions: [], budgets: {} }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function text(value: unknown, max: number, allowEmpty = false): string {
  if (typeof value !== 'string') throw new Error('ساختار متن در فایل معتبر نیست.')
  const trimmed = value.trim()
  if ((!allowEmpty && trimmed.length === 0) || trimmed.length > max) {
    throw new Error('طول یکی از متن‌ها خارج از محدودهٔ مجاز است.')
  }
  return trimmed
}

function amount(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value <= 0 || value > MAX_AMOUNT) {
    throw new Error('مبلغ‌ها باید عددهای صحیح و مثبت تومان باشند.')
  }
  return value
}

function datetime(value: unknown): string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T/.test(value) || !Number.isFinite(Date.parse(value))) {
    throw new Error('زمان ثبت یکی از تراکنش‌ها معتبر نیست.')
  }
  return value
}

export function validateTransactionInput(input: TransactionInput): TransactionInput {
  const category = CATEGORY_MAP.get(input.categoryId)
  if (!category || category.type !== input.type) throw new Error('دسته‌بندی با نوع تراکنش هم‌خوانی ندارد.')
  if (input.type !== 'income' && input.type !== 'expense') throw new Error('نوع تراکنش معتبر نیست.')
  if (!isValidISODate(input.date)) throw new Error('تاریخ تراکنش معتبر نیست.')
  return {
    type: input.type,
    amount: amount(input.amount),
    title: text(input.title, 120),
    note: text(input.note, 500, true),
    categoryId: input.categoryId,
    date: input.date,
  }
}

/** Strictly validate imports and persisted data; never coerce unknown money values. */
export function parseLedger(value: unknown): LedgerData {
  if (!isRecord(value)) throw new Error('فایل پشتیبان باید یک شیء JSON معتبر باشد.')
  if (value.schemaVersion !== 1) throw new Error('نسخهٔ فایل پشتیبان پشتیبانی نمی‌شود.')
  if (value.currency !== 'toman') throw new Error('واحد پول فایل باید تومان باشد.')
  if (typeof value.isDemo !== 'boolean') throw new Error('وضعیت دفتر نمونه در فایل معتبر نیست.')
  if (!Array.isArray(value.transactions) || value.transactions.length > MAX_TRANSACTIONS) {
    throw new Error('فهرست تراکنش‌ها معتبر نیست یا بیش از حد بزرگ است.')
  }

  const ids = new Set<string>()
  const transactions: Transaction[] = value.transactions.map((item: unknown) => {
    if (!isRecord(item)) throw new Error('یکی از تراکنش‌ها ساختار معتبری ندارد.')
    const id = text(item.id, 100)
    if (ids.has(id)) throw new Error('شناسهٔ تکراری در تراکنش‌ها وجود دارد.')
    ids.add(id)
    const input = validateTransactionInput({
      type: item.type as TransactionInput['type'],
      amount: item.amount as number,
      title: item.title as string,
      note: item.note as string,
      categoryId: item.categoryId as string,
      date: item.date as string,
    })
    return { id, ...input, createdAt: datetime(item.createdAt), updatedAt: datetime(item.updatedAt) }
  })

  if (!isRecord(value.budgets)) throw new Error('ساختار بودجه‌ها معتبر نیست.')
  const budgets: Record<string, number> = {}
  for (const [categoryId, limit] of Object.entries(value.budgets)) {
    const category = CATEGORY_MAP.get(categoryId)
    if (!category || category.type !== 'expense') throw new Error('یکی از دسته‌های بودجه شناخته‌شده نیست.')
    budgets[categoryId] = amount(limit)
  }

  return { schemaVersion: 1, currency: 'toman', isDemo: value.isDemo, transactions, budgets }
}

export function parseBackup(content: string): LedgerData {
  let parsed: unknown
  try {
    parsed = JSON.parse(content)
  } catch {
    throw new Error('فایل انتخاب‌شده JSON معتبر نیست.')
  }
  return parseLedger(parsed)
}

export function saveLedger(data: LedgerData, storage: StorageAdapter = window.localStorage): void {
  storage.setItem(STORAGE_KEY, JSON.stringify(data))
}

export function loadLedger(storage: StorageAdapter = window.localStorage): LoadedLedger {
  let raw: string | null
  try {
    raw = storage.getItem(STORAGE_KEY)
  } catch {
    return { data: createDemoLedger(), issue: 'unavailable', recoveryRaw: null }
  }

  if (raw === null) {
    const data = createDemoLedger()
    try {
      saveLedger(data, storage)
      return { data, issue: null, recoveryRaw: null }
    } catch {
      return { data, issue: 'unavailable', recoveryRaw: null }
    }
  }

  try {
    return { data: parseBackup(raw), issue: null, recoveryRaw: null }
  } catch {
    // Keep the original bytes untouched. Only an explicit import/reset may replace them.
    return { data: createEmptyLedger(), issue: 'corrupt', recoveryRaw: raw }
  }
}
