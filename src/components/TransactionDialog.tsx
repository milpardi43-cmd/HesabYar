import { useEffect, useId, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowDownLeft, ArrowUpRight, Check } from 'lucide-react'
import type { Transaction, TransactionInput, TransactionType } from '../types'
import { categoriesFor } from '../data/categories'
import { useLedger } from '../context/LedgerContext'
import { formatMoney, parseAmount } from '../lib/format'
import { isValidISODate, todayISO } from '../lib/dates'
import { Button } from './Button'
import { Modal } from './Modal'
import { PersianDatePicker } from './PersianDatePicker'
import { useToast } from './Toast'

interface TransactionDialogProps {
  open: boolean
  transaction?: Transaction
  onClose: () => void
}

interface FormState {
  type: TransactionType
  amount: string
  title: string
  note: string
  categoryId: string
  date: string
}

function initialState(transaction?: Transaction): FormState {
  return {
    type: transaction?.type ?? 'expense',
    amount: transaction ? String(transaction.amount) : '',
    title: transaction?.title ?? '',
    note: transaction?.note ?? '',
    categoryId: transaction?.categoryId ?? 'food',
    date: transaction?.date ?? todayISO(),
  }
}

export function TransactionDialog({ open, transaction, onClose }: TransactionDialogProps) {
  const { upsertTransaction } = useLedger()
  const { notify } = useToast()
  const [form, setForm] = useState<FormState>(() => initialState(transaction))
  const [errors, setErrors] = useState<Partial<Record<keyof FormState, string>>>({})
  const baseId = useId()

  useEffect(() => {
    if (open) {
      setForm(initialState(transaction))
      setErrors({})
    }
  }, [open, transaction])

  const update = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }))
    setErrors((current) => ({ ...current, [key]: undefined }))
  }

  const changeType = (type: TransactionType) => {
    setForm((current) => ({ ...current, type, categoryId: categoriesFor(type)[0].id }))
    setErrors({})
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const nextErrors: Partial<Record<keyof FormState, string>> = {}
    const amount = parseAmount(form.amount)
    if (amount === null) nextErrors.amount = 'یک مبلغ صحیح و مثبت به تومان وارد کنید (حداکثر ۹۹۹٬۹۹۹٬۹۹۹٬۹۹۹).'
    if (!form.title.trim()) nextErrors.title = 'برای تراکنش یک عنوان بنویسید.'
    if (form.title.trim().length > 120) nextErrors.title = 'عنوان باید حداکثر ۱۲۰ نویسه باشد.'
    if (form.note.length > 500) nextErrors.note = 'یادداشت باید حداکثر ۵۰۰ نویسه باشد.'
    if (!isValidISODate(form.date)) nextErrors.date = 'تاریخ معتبری انتخاب کنید.'
    const category = categoriesFor(form.type).find((item) => item.id === form.categoryId)
    if (!category) nextErrors.categoryId = 'یک دسته‌بندی انتخاب کنید.'
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0 || amount === null) return

    const input: TransactionInput = {
      type: form.type, amount, title: form.title.trim(), note: form.note.trim(),
      categoryId: form.categoryId, date: form.date,
    }
    const result = upsertTransaction(input, transaction?.id)
    if (!result.ok) {
      notify(result.message ?? 'ثبت تراکنش ممکن نشد.', 'error')
      return
    }
    notify(transaction ? 'تغییرات تراکنش ذخیره شد.' : 'تراکنش تازه ثبت شد.')
    onClose()
  }

  const parsedAmount = parseAmount(form.amount)
  const fieldId = (name: string) => `${baseId}-${name}`
  const errorFor = (name: keyof FormState) => errors[name]
    ? <p className="field-error" id={fieldId(`${name}-error`)} role="alert">{errors[name]}</p>
    : null

  return (
    <Modal
      open={open} onClose={onClose}
      title={transaction ? 'ویرایش تراکنش' : 'ثبت تراکنش تازه'}
      description={transaction ? 'جزئیات این تراکنش را به‌روز کنید.' : 'هر ورود و خروج پول را در چند ثانیه ثبت کنید.'}
    >
      <form className="transaction-form" onSubmit={submit} noValidate>
        <div className="type-switch" role="group" aria-label="نوع تراکنش">
          <button type="button" className={form.type === 'expense' ? 'is-active is-expense' : ''} aria-pressed={form.type === 'expense'} onClick={() => changeType('expense')}>
            <ArrowUpRight size={19} /> هزینه
          </button>
          <button type="button" className={form.type === 'income' ? 'is-active is-income' : ''} aria-pressed={form.type === 'income'} onClick={() => changeType('income')}>
            <ArrowDownLeft size={19} /> درآمد
          </button>
        </div>

        <div className="field">
          <label htmlFor={fieldId('amount')}>مبلغ <span className="required">*</span></label>
          <div className={`amount-input${errorFor('amount') ? ' is-invalid' : ''}`}>
            <input
              id={fieldId('amount')} type="text" inputMode="numeric" autoComplete="off"
              placeholder="۰" value={form.amount} dir="ltr"
              onChange={(event) => update('amount', event.target.value)}
              aria-invalid={Boolean(errorFor('amount'))}
              aria-describedby={errorFor('amount') ? fieldId('amount-error') : fieldId('amount-hint')}
            />
            <span>تومان</span>
          </div>
          {errorFor('amount') ?? <p className="field-hint" id={fieldId('amount-hint')}>{parsedAmount !== null ? formatMoney(parsedAmount) : 'رقم‌های فارسی و انگلیسی هر دو پذیرفته می‌شوند.'}</p>}
        </div>

        <div className="form-grid">
          <div className="field">
            <label htmlFor={fieldId('category')}>دسته‌بندی <span className="required">*</span></label>
            <select id={fieldId('category')} className="field-control" value={form.categoryId} onChange={(event) => update('categoryId', event.target.value)} aria-invalid={Boolean(errorFor('categoryId'))}>
              {categoriesFor(form.type).map((category) => <option key={category.id} value={category.id}>{category.label}</option>)}
            </select>
            {errorFor('categoryId')}
          </div>
          <div className="field">
            <label htmlFor={fieldId('date')}>تاریخ شمسی <span className="required">*</span></label>
            <PersianDatePicker id={fieldId('date')} value={form.date} onChange={(value) => update('date', value)} invalid={Boolean(errorFor('date'))} />
            {errorFor('date')}
          </div>
        </div>

        <div className="field">
          <label htmlFor={fieldId('title')}>عنوان تراکنش <span className="required">*</span></label>
          <input id={fieldId('title')} className="field-control" type="text" maxLength={120} placeholder="مثلاً خرید هفتگی خانه" value={form.title} onChange={(event) => update('title', event.target.value)} aria-invalid={Boolean(errorFor('title'))} aria-describedby={errorFor('title') ? fieldId('title-error') : undefined} />
          {errorFor('title')}
        </div>

        <div className="field">
          <label htmlFor={fieldId('note')}>یادداشت <span className="optional">(اختیاری)</span></label>
          <textarea id={fieldId('note')} className="field-control" rows={2} maxLength={500} placeholder="جزئیات بیشتر، اگر لازم است…" value={form.note} onChange={(event) => update('note', event.target.value)} />
          {errorFor('note')}
        </div>

        <footer className="modal-footer">
          <Button onClick={onClose}>انصراف</Button>
          <Button type="submit" variant="primary"><Check size={18} />{transaction ? 'ذخیرهٔ تغییرات' : 'ثبت تراکنش'}</Button>
        </footer>
      </form>
    </Modal>
  )
}
