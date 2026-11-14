import type { BackupFile, LedgerData, Transaction } from '../types'
import { getCategory } from '../data/categories'
import { csvCell } from './format'
import { formatDate, todayISO } from './dates'

export function downloadText(filename: string, content: string, mime: string): void {
  const blob = new Blob([content], { type: `${mime};charset=utf-8` })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000)
}

export function downloadBackup(data: LedgerData): void {
  const backup: BackupFile = { app: 'HesabYar', exportedAt: new Date().toISOString(), ...data }
  downloadText(`hesabyar-backup-${todayISO()}.json`, JSON.stringify(backup, null, 2), 'application/json')
}

export function downloadTransactionsCSV(transactions: Transaction[]): void {
  const header = ['عنوان', 'نوع', 'دسته‌بندی', 'مبلغ (تومان)', 'تاریخ میلادی', 'تاریخ شمسی', 'یادداشت']
  const rows = transactions.map((transaction) => [
    transaction.title,
    transaction.type === 'income' ? 'درآمد' : 'هزینه',
    getCategory(transaction.categoryId).label,
    transaction.amount,
    transaction.date,
    formatDate(transaction.date),
    transaction.note,
  ].map(csvCell).join(','))
  downloadText(
    `hesabyar-transactions-${todayISO()}.csv`,
    `\uFEFF${[header.map(csvCell).join(','), ...rows].join('\r\n')}`,
    'text/csv',
  )
}
