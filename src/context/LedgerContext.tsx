import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { ActionResult, LedgerData, StorageIssue, Transaction, TransactionInput } from '../types'
import { CATEGORY_MAP } from '../data/categories'
import { createDemoLedger } from '../data/demo'
import { MAX_AMOUNT } from '../lib/format'
import {
  createEmptyLedger, loadLedger, parseBackup, parseLedger, saveLedger,
  STORAGE_KEY, validateTransactionInput,
} from '../lib/storage'

interface LedgerContextValue {
  data: LedgerData
  issue: StorageIssue
  recoveryRaw: string | null
  upsertTransaction: (input: TransactionInput, id?: string) => ActionResult
  deleteTransaction: (id: string) => ActionResult
  setBudget: (categoryId: string, limit: number) => ActionResult
  removeBudget: (categoryId: string) => ActionResult
  replaceData: (data: LedgerData) => ActionResult
  clearAll: () => ActionResult
  loadDemo: () => ActionResult
}

const LedgerContext = createContext<LedgerContextValue | null>(null)

function newId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `tx-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function LedgerProvider({ children }: { children: ReactNode }) {
  const [initial] = useState(() => loadLedger())
  const [data, setData] = useState(initial.data)
  const [issue, setIssue] = useState<StorageIssue>(initial.issue)
  const [recoveryRaw, setRecoveryRaw] = useState(initial.recoveryRaw)

  const commit = useCallback((next: LedgerData, force = false): ActionResult => {
    if (issue === 'corrupt' && !force) {
      return { ok: false, message: 'ابتدا داده‌های بازیابی را در تنظیمات بررسی و دفتر را بازنشانی یا پشتیبان وارد کنید.' }
    }
    let persisted = false
    try {
      saveLedger(next)
      setIssue(null)
      persisted = true
    } catch {
      // Keep the user's work in memory and make the persistence problem visible.
      setIssue('write')
    }
    // If an explicit replacement could not be persisted, keep the recovery copy available.
    if (force && persisted) setRecoveryRaw(null)
    setData(next)
    return { ok: true }
  }, [issue])

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY || event.storageArea !== window.localStorage) return
      if (event.newValue === null) {
        setData(createEmptyLedger())
        setIssue(null)
        setRecoveryRaw(null)
        return
      }
      try {
        setData(parseBackup(event.newValue))
        setIssue(null)
        setRecoveryRaw(null)
      } catch {
        setData(createEmptyLedger())
        setIssue('corrupt')
        setRecoveryRaw(event.newValue)
      }
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const upsertTransaction = useCallback((input: TransactionInput, id?: string): ActionResult => {
    try {
      const clean = validateTransactionInput(input)
      const now = new Date().toISOString()
      if (id) {
        const existing = data.transactions.find((transaction) => transaction.id === id)
        if (!existing) return { ok: false, message: 'این تراکنش دیگر در دفتر وجود ندارد.' }
        const updated: Transaction = { ...existing, ...clean, updatedAt: now }
        return commit({ ...data, transactions: data.transactions.map((transaction) => transaction.id === id ? updated : transaction) })
      }
      const transaction: Transaction = { id: newId(), ...clean, createdAt: now, updatedAt: now }
      return commit({ ...data, transactions: [...data.transactions, transaction] })
    } catch (error) {
      return { ok: false, message: error instanceof Error ? error.message : 'ثبت تراکنش ممکن نشد.' }
    }
  }, [commit, data.transactions])

  const deleteTransaction = useCallback((id: string): ActionResult => (
    commit({ ...data, transactions: data.transactions.filter((transaction) => transaction.id !== id) })
  ), [commit, data])

  const setBudget = useCallback((categoryId: string, limit: number): ActionResult => {
    const category = CATEGORY_MAP.get(categoryId)
    if (!category || category.type !== 'expense') return { ok: false, message: 'بودجه فقط برای دسته‌های هزینه تعیین می‌شود.' }
    if (!Number.isSafeInteger(limit) || limit <= 0 || limit > MAX_AMOUNT) return { ok: false, message: 'سقف بودجه معتبر نیست.' }
    return commit({ ...data, budgets: { ...data.budgets, [categoryId]: limit } })
  }, [commit, data])

  const removeBudget = useCallback((categoryId: string): ActionResult => {
    const budgets = { ...data.budgets }
    delete budgets[categoryId]
    return commit({ ...data, budgets })
  }, [commit, data])

  const replaceData = useCallback((next: LedgerData): ActionResult => {
    try {
      return commit(parseLedger(next), true)
    } catch (error) {
      return { ok: false, message: error instanceof Error ? error.message : 'بازیابی فایل ممکن نشد.' }
    }
  }, [commit])

  const clearAll = useCallback(() => commit(createEmptyLedger(), true), [commit])
  const loadDemo = useCallback(() => commit(createDemoLedger(), true), [commit])

  const value = useMemo(() => ({
    data, issue, recoveryRaw, upsertTransaction, deleteTransaction,
    setBudget, removeBudget, replaceData, clearAll, loadDemo,
  }), [data, issue, recoveryRaw, upsertTransaction, deleteTransaction, setBudget, removeBudget, replaceData, clearAll, loadDemo])

  return <LedgerContext.Provider value={value}>{children}</LedgerContext.Provider>
}

export function useLedger(): LedgerContextValue {
  const context = useContext(LedgerContext)
  if (!context) throw new Error('useLedger must be used inside LedgerProvider')
  return context
}
