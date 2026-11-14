import { Pencil } from 'lucide-react'
import type { Transaction } from '../types'
import { getCategory } from '../data/categories'
import { formatDate } from '../lib/dates'
import { formatNumber } from '../lib/format'
import { CategoryIcon } from './CategoryIcon'

export function TransactionRow({ transaction, onEdit }: { transaction: Transaction; onEdit: (transaction: Transaction) => void }) {
  const category = getCategory(transaction.categoryId)
  return (
    <li className="transaction-row">
      <CategoryIcon category={category} />
      <div className="transaction-info">
        <button type="button" className="transaction-title" onClick={() => onEdit(transaction)}>{transaction.title}</button>
        <span>{category.label}<span className="row-separator">·</span>{formatDate(transaction.date)}</span>
      </div>
      <div className={`transaction-value ${transaction.type}`}>
        <span className="transaction-number"><span dir="ltr">{transaction.type === 'income' ? '+' : '−'}</span>{formatNumber(transaction.amount)}</span>
        <small>تومان</small>
      </div>
      <button type="button" className="icon-btn row-edit" onClick={() => onEdit(transaction)} aria-label={`ویرایش ${transaction.title}`}><Pencil size={16} /></button>
    </li>
  )
}
