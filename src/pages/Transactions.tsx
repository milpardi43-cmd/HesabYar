import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, Download, Pencil, Plus, ReceiptText, Search, SlidersHorizontal, Trash2, X } from 'lucide-react'
import type { Transaction, TransactionType } from '../types'
import { CATEGORIES, CATEGORY_MAP, getCategory } from '../data/categories'
import { useLedger } from '../context/LedgerContext'
import { useDialogs } from '../context/DialogContext'
import { filterTransactions, totals } from '../lib/analytics'
import { currentMonthKey, formatDate, monthKey, monthLabel, parseMonthKey } from '../lib/dates'
import { formatMoney, formatNumber } from '../lib/format'
import { downloadTransactionsCSV } from '../lib/download'
import { Button } from '../components/Button'
import { CategoryIcon } from '../components/CategoryIcon'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { EmptyState } from '../components/EmptyState'
import { useToast } from '../components/Toast'

const PAGE_SIZE = 10

export function Transactions() {
  const { data, deleteTransaction } = useLedger()
  const { openAdd, openEdit } = useDialogs()
  const { notify } = useToast()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = useState(1)
  const [deleting, setDeleting] = useState<Transaction | null>(null)

  const query = params.get('q') ?? ''
  const typeParam = params.get('type')
  const type: TransactionType | 'all' = typeParam === 'income' || typeParam === 'expense' ? typeParam : 'all'
  const categoryParam = params.get('category') ?? 'all'
  const categoryId = CATEGORY_MAP.has(categoryParam) ? categoryParam : 'all'
  const monthParam = params.get('month') ?? 'all'
  let month = 'all'
  try { parseMonthKey(monthParam); month = monthParam } catch { /* fall back to all months */ }

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params)
    if (!value || value === 'all') next.delete(key)
    else next.set(key, value)
    setParams(next, { replace: true })
    setPage(1)
  }
  const resetFilters = () => { setParams({}, { replace: true }); setPage(1) }
  const hasFilters = Boolean(query || type !== 'all' || categoryId !== 'all' || month !== 'all')

  const filtered = useMemo(() => filterTransactions(data.transactions, { query, type, categoryId, month }), [data.transactions, query, type, categoryId, month])
  const sums = useMemo(() => totals(filtered), [filtered])
  const months = useMemo(() => {
    const keys = new Set(data.transactions.map((transaction) => monthKey(transaction.date)))
    keys.add(currentMonthKey())
    if (month !== 'all') keys.add(month)
    return [...keys].sort().reverse()
  }, [data.transactions, month])

  useEffect(() => { setPage(1) }, [query, type, categoryId, month])
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const pageItems = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE)

  const confirmDelete = () => {
    if (!deleting) return
    const result = deleteTransaction(deleting.id)
    notify(result.ok ? 'تراکنش حذف شد.' : result.message ?? 'حذف تراکنش ممکن نشد.', result.ok ? 'success' : 'error')
  }

  return (
    <div className="page transactions-page">
      <div className="page-heading">
        <div><h1>تراکنش‌ها</h1><p>همهٔ ورود و خروج‌های پول، یک‌جا و قابل جست‌وجو.</p></div>
        <div className="heading-actions">
          <Button onClick={() => { downloadTransactionsCSV(filtered); notify('فایل CSV تراکنش‌های فیلترشده آماده شد.') }} disabled={filtered.length === 0}><Download size={17} />خروجی CSV</Button>
          <Button variant="primary" onClick={openAdd}><Plus size={19} />ثبت تراکنش</Button>
        </div>
      </div>

      <section className="card filters-card" aria-label="فیلتر تراکنش‌ها">
        <div className="filters-top">
          <div className="search-field">
            <Search size={19} aria-hidden="true" />
            <input type="search" aria-label="جست‌وجوی تراکنش" placeholder="جست‌وجو در عنوان، یادداشت یا دسته…" value={query} onChange={(event) => setFilter('q', event.target.value)} />
            {query && <button type="button" className="icon-btn" onClick={() => setFilter('q', '')} aria-label="پاک کردن جست‌وجو"><X size={16} /></button>}
          </div>
          <div className="type-tabs" role="group" aria-label="نوع تراکنش">
            {([['all', 'همه'], ['income', 'درآمدها'], ['expense', 'هزینه‌ها']] as const).map(([value, label]) => (
              <button key={value} type="button" className={type === value ? 'is-active' : ''} aria-pressed={type === value} onClick={() => setFilter('type', value)}>{label}</button>
            ))}
          </div>
        </div>
        <div className="filters-bottom">
          <span className="filter-label"><SlidersHorizontal size={16} />فیلترها</span>
          <label className="select-filter"><span className="sr-only">دسته‌بندی</span>
            <select value={categoryId} onChange={(event) => setFilter('category', event.target.value)}>
              <option value="all">همهٔ دسته‌بندی‌ها</option>
              <optgroup label="هزینه‌ها">{CATEGORIES.filter((category) => category.type === 'expense').map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}</optgroup>
              <optgroup label="درآمدها">{CATEGORIES.filter((category) => category.type === 'income').map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}</optgroup>
            </select>
          </label>
          <label className="select-filter"><span className="sr-only">ماه</span>
            <select value={month} onChange={(event) => setFilter('month', event.target.value)}>
              <option value="all">همهٔ ماه‌ها</option>
              {months.map((key) => <option key={key} value={key}>{monthLabel(key)}</option>)}
            </select>
          </label>
          {hasFilters && <button type="button" className="reset-filters" onClick={resetFilters}><X size={14} />حذف فیلترها</button>}
        </div>
      </section>

      <div className="results-summary">
        <span><strong>{formatNumber(filtered.length)}</strong> تراکنش</span>
        <span className="summary-income">درآمد: <strong>{formatMoney(sums.income)}</strong></span>
        <span className="summary-expense">هزینه: <strong>{formatMoney(sums.expense)}</strong></span>
        <span className="summary-balance">مانده: <strong className={sums.balance < 0 ? 'text-danger' : ''}>{formatMoney(sums.balance)}</strong></span>
      </div>

      <section className="card table-card" aria-label="فهرست تراکنش‌ها">
        {pageItems.length > 0 ? (
          <>
            <div className="table-wrap">
              <table className="transactions-table">
                <thead><tr><th scope="col">شرح تراکنش</th><th scope="col">دسته‌بندی</th><th scope="col">تاریخ</th><th scope="col">مبلغ</th><th scope="col"><span className="sr-only">عملیات</span></th></tr></thead>
                <tbody>
                  {pageItems.map((transaction) => {
                    const category = getCategory(transaction.categoryId)
                    return (
                      <tr key={transaction.id}>
                        <td className="cell-title">
                          <button type="button" onClick={() => openEdit(transaction)}>{transaction.title}</button>
                          {transaction.note && <span>{transaction.note}</span>}
                        </td>
                        <td className="cell-category"><span className="category-badge"><CategoryIcon category={category} size={15} />{category.label}</span></td>
                        <td className="cell-date">{formatDate(transaction.date)}</td>
                        <td className={`cell-amount ${transaction.type}`}><span dir="ltr" className="amount-sign">{transaction.type === 'income' ? '+' : '−'}</span>{formatMoney(transaction.amount)}</td>
                        <td className="cell-actions">
                          <button type="button" className="icon-btn" onClick={() => openEdit(transaction)} aria-label={`ویرایش ${transaction.title}`}><Pencil size={16} /></button>
                          <button type="button" className="icon-btn is-danger" onClick={() => setDeleting(transaction)} aria-label={`حذف ${transaction.title}`}><Trash2 size={16} /></button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
            <footer className="table-pagination">
              <span>نمایش {formatNumber((currentPage - 1) * PAGE_SIZE + 1)} تا {formatNumber(Math.min(currentPage * PAGE_SIZE, filtered.length))} از {formatNumber(filtered.length)}</span>
              <div>
                <button type="button" className="icon-btn" disabled={currentPage <= 1} onClick={() => setPage(currentPage - 1)} aria-label="صفحه قبل"><ChevronRight size={18} /></button>
                <span className="page-number">{formatNumber(currentPage)} / {formatNumber(totalPages)}</span>
                <button type="button" className="icon-btn" disabled={currentPage >= totalPages} onClick={() => setPage(currentPage + 1)} aria-label="صفحه بعد"><ChevronLeft size={18} /></button>
              </div>
            </footer>
          </>
        ) : (
          <EmptyState
            icon={<ReceiptText size={31} />}
            title={data.transactions.length === 0 ? 'دفترتان آمادهٔ اولین ثبت است' : 'چیزی پیدا نشد'}
            description={data.transactions.length === 0 ? 'با ثبت یک درآمد یا هزینه، تصویر مالی شما کم‌کم کامل می‌شود.' : 'عبارت جست‌وجو یا فیلترها را تغییر دهید و دوباره امتحان کنید.'}
            action={data.transactions.length === 0
              ? <Button variant="primary" onClick={openAdd}><Plus size={17} />ثبت تراکنش</Button>
              : <Button onClick={resetFilters}><X size={16} />حذف فیلترها</Button>}
          />
        )}
      </section>

      <ConfirmDialog
        open={deleting !== null} onClose={() => setDeleting(null)}
        title="حذف این تراکنش؟"
        message={deleting ? `«${deleting.title}» به مبلغ ${formatMoney(deleting.amount)} برای همیشه حذف می‌شود. این کار قابل بازگشت نیست.` : ''}
        confirmLabel="حذف تراکنش"
        onConfirm={confirmDelete}
      />
    </div>
  )
}
