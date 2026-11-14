import { useEffect, useId, useState } from 'react'
import type { FormEvent } from 'react'
import { Check, Pencil, Plus, Target, Trash2, Wallet } from 'lucide-react'
import { EXPENSE_CATEGORIES, getCategory } from '../data/categories'
import { useLedger } from '../context/LedgerContext'
import { budgetSummary } from '../lib/analytics'
import { currentMonthKey } from '../lib/dates'
import { formatMoney, formatPercent, parseAmount } from '../lib/format'
import { Button } from '../components/Button'
import { CategoryIcon } from '../components/CategoryIcon'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { EmptyState } from '../components/EmptyState'
import { Modal } from '../components/Modal'
import { PeriodPicker } from '../components/PeriodPicker'
import { useToast } from '../components/Toast'

interface BudgetDialogProps {
  open: boolean
  categoryId?: string
  onClose: () => void
}

function BudgetDialog({ open, categoryId, onClose }: BudgetDialogProps) {
  const { data, setBudget } = useLedger()
  const { notify } = useToast()
  const [category, setCategory] = useState('food')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const id = useId()
  const editing = categoryId !== undefined

  useEffect(() => {
    if (!open) return
    const selected = categoryId ?? EXPENSE_CATEGORIES.find((item) => data.budgets[item.id] === undefined)?.id ?? 'food'
    setCategory(selected)
    setAmount(data.budgets[selected] ? String(data.budgets[selected]) : '')
    setError('')
  }, [open, categoryId, data.budgets])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const parsed = parseAmount(amount)
    if (parsed === null) { setError('یک سقف صحیح و مثبت به تومان وارد کنید.'); return }
    const result = setBudget(category, parsed)
    if (!result.ok) { notify(result.message ?? 'ذخیرهٔ بودجه ممکن نشد.', 'error'); return }
    notify('سقف ماهانهٔ بودجه ذخیره شد.')
    onClose()
  }

  return (
    <Modal open={open} onClose={onClose} title={editing ? 'ویرایش سقف بودجه' : 'تعیین سقف بودجه'} description="این سقف برای همهٔ ماه‌ها اعمال می‌شود و مصرف آن هر ماه جداگانه سنجیده می‌شود.">
      <form onSubmit={submit} className="budget-form" noValidate>
        <div className="field">
          <label htmlFor={`${id}-category`}>دسته‌بندی هزینه</label>
          <select id={`${id}-category`} className="field-control" value={category} onChange={(event) => setCategory(event.target.value)} disabled={editing}>
            {EXPENSE_CATEGORIES.map((item) => <option key={item.id} value={item.id}>{item.label}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor={`${id}-amount`}>سقف ماهانه (تومان)</label>
          <input id={`${id}-amount`} className={`field-control${error ? ' is-invalid' : ''}`} type="text" inputMode="numeric" dir="ltr" placeholder="مثلاً ۵٬۰۰۰٬۰۰۰" value={amount} onChange={(event) => { setAmount(event.target.value); setError('') }} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined} />
          {error && <p className="field-error" id={`${id}-error`} role="alert">{error}</p>}
        </div>
        <footer className="modal-footer">
          <Button onClick={onClose}>انصراف</Button>
          <Button type="submit" variant="primary"><Check size={18} />ذخیرهٔ سقف</Button>
        </footer>
      </form>
    </Modal>
  )
}

export function Budgets() {
  const { data, removeBudget } = useLedger()
  const { notify } = useToast()
  const [month, setMonth] = useState(currentMonthKey)
  const [dialog, setDialog] = useState<{ open: boolean; categoryId?: string }>({ open: false })
  const [deleting, setDeleting] = useState<string | null>(null)
  const summary = budgetSummary(data, month)
  const items = [...summary.items].sort((a, b) => b.percent - a.percent)
  const unbudgeted = EXPENSE_CATEGORIES.filter((category) => data.budgets[category.id] === undefined)

  const confirmDelete = () => {
    if (!deleting) return
    const result = removeBudget(deleting)
    notify(result.ok ? 'سقف بودجه حذف شد؛ تراکنش‌ها دست‌نخورده ماندند.' : result.message ?? 'حذف بودجه ممکن نشد.', result.ok ? 'success' : 'error')
  }

  return (
    <div className="page budgets-page">
      <div className="page-heading">
        <div><h1>بودجه‌بندی</h1><p>برای هر دسته یک سقف بگذارید و با خیال راحت‌تر خرج کنید.</p></div>
        <Button variant="primary" onClick={() => setDialog({ open: true })}><Plus size={19} />تعیین سقف بودجه</Button>
      </div>
      <div className="page-toolbar">
        <PeriodPicker value={month} onChange={setMonth} />
        <span className="currency-note">سقف‌های ماهانه، به تومان</span>
      </div>

      <section className="budget-summary-grid" aria-label="خلاصهٔ بودجه">
        <article className="card budget-summary-card"><span className="soft-icon"><Wallet size={21} /></span><div><span>مجموع سقف بودجه</span><strong>{formatMoney(summary.limit)}</strong></div></article>
        <article className="card budget-summary-card"><span className="soft-icon is-expense"><Target size={21} /></span><div><span>هزینه در دسته‌های بودجه‌دار</span><strong>{formatMoney(summary.spent)}</strong></div></article>
        <article className="card budget-summary-card"><span className={`soft-icon${summary.remaining < 0 ? ' is-expense' : ' is-income'}`}><Wallet size={21} /></span><div><span>{summary.remaining < 0 ? 'عبور از سقف بودجه' : 'باقی‌ماندهٔ بودجه'}</span><strong className={summary.remaining < 0 ? 'text-danger' : 'text-green'}>{formatMoney(Math.abs(summary.remaining))}</strong></div></article>
      </section>

      {items.length > 0 ? (
        <div className="budget-cards-grid">
          {items.map((item) => {
            const category = getCategory(item.categoryId)
            const status = item.percent > 100 ? 'over' : item.percent >= 80 ? 'near' : 'ok'
            const statusLabel = status === 'over' ? 'عبور از سقف' : status === 'near' ? 'نزدیک سقف' : 'در مسیر درست'
            return (
              <article className="card budget-card" key={item.categoryId}>
                <header>
                  <div className="budget-card-title"><CategoryIcon category={category} /><h2>{category.label}</h2></div>
                  <div className="budget-card-actions">
                    <button type="button" className="icon-btn" onClick={() => setDialog({ open: true, categoryId: item.categoryId })} aria-label={`ویرایش بودجهٔ ${category.label}`}><Pencil size={16} /></button>
                    <button type="button" className="icon-btn is-danger" onClick={() => setDeleting(item.categoryId)} aria-label={`حذف بودجهٔ ${category.label}`}><Trash2 size={16} /></button>
                  </div>
                </header>
                <div className="budget-card-amount"><strong>{formatMoney(item.spent)}</strong><span>از {formatMoney(item.limit)}</span></div>
                <div className={`progress-track is-${status}`} role="progressbar" aria-label={`مصرف بودجهٔ ${category.label}`} aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(100, Math.round(item.percent))} aria-valuetext={`${formatMoney(item.spent)} از ${formatMoney(item.limit)}`}><span style={{ width: `${Math.min(100, item.percent)}%` }} /></div>
                <footer><span className={`status-chip status-${status}`}>{statusLabel}</span><span>{formatPercent(item.percent)} مصرف شده</span></footer>
                <p className="budget-remaining">{item.remaining >= 0 ? `${formatMoney(item.remaining)} تا سقف مانده` : `${formatMoney(Math.abs(item.remaining))} بیشتر از سقف خرج شده`}</p>
              </article>
            )
          })}
        </div>
      ) : (
        <section className="card">
          <EmptyState icon={<Target size={32} />} title="اولین سقف بودجه را تعیین کنید" description="مثلاً برای خوراک، رفت‌وآمد یا تفریح یک مبلغ ماهانه مشخص کنید. هزینه‌های همان ماه خودکار با آن مقایسه می‌شوند." action={<Button variant="primary" onClick={() => setDialog({ open: true })}><Plus size={17} />تعیین سقف بودجه</Button>} />
        </section>
      )}

      {unbudgeted.length > 0 && (
        <section className="unbudgeted-section">
          <h2>دسته‌های بدون سقف</h2>
          <p>اگر برای این دسته‌ها هم برنامه دارید، یک سقف ماهانه اضافه کنید.</p>
          <div className="unbudgeted-chips">
            {unbudgeted.map((category) => <button type="button" key={category.id} onClick={() => setDialog({ open: true, categoryId: category.id })}><CategoryIcon category={category} size={15} />{category.label}<Plus size={15} /></button>)}
          </div>
        </section>
      )}

      <BudgetDialog open={dialog.open} categoryId={dialog.categoryId} onClose={() => setDialog({ open: false })} />
      <ConfirmDialog open={deleting !== null} onClose={() => setDeleting(null)} title="حذف سقف بودجه؟" message={deleting ? `سقف ماهانهٔ «${getCategory(deleting).label}» حذف می‌شود. تراکنش‌های ثبت‌شده تغییری نمی‌کنند.` : ''} confirmLabel="حذف سقف" onConfirm={confirmDelete} />
    </div>
  )
}
