import { useRef, useState } from 'react'
import { AlertTriangle, CalendarDays, Coins, Database, Download, FlaskConical, HardDrive, ShieldCheck, Trash2, Upload } from 'lucide-react'
import type { LedgerData } from '../types'
import { useLedger } from '../context/LedgerContext'
import { formatNumber } from '../lib/format'
import { todayISO } from '../lib/dates'
import { downloadBackup, downloadText } from '../lib/download'
import { MAX_BACKUP_BYTES, parseBackup } from '../lib/storage'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { useToast } from '../components/Toast'

type PendingAction = { kind: 'clear' } | { kind: 'demo' } | { kind: 'import'; data: LedgerData; filename: string } | null

export function Settings() {
  const { data, issue, recoveryRaw, replaceData, clearAll, loadDemo } = useLedger()
  const { notify } = useToast()
  const fileRef = useRef<HTMLInputElement>(null)
  const [pending, setPending] = useState<PendingAction>(null)
  const [importing, setImporting] = useState(false)

  const onFile = async (file: File | undefined) => {
    if (!file) return
    if (file.size > MAX_BACKUP_BYTES) { notify('حجم فایل پشتیبان باید کمتر از ۵ مگابایت باشد.', 'error'); return }
    setImporting(true)
    try {
      const content = await file.text()
      const parsed = parseBackup(content)
      setPending({ kind: 'import', data: parsed, filename: file.name })
    } catch (error) {
      notify(error instanceof Error ? error.message : 'خواندن فایل ممکن نشد.', 'error')
    } finally {
      setImporting(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const confirm = () => {
    if (!pending) return
    const result = pending.kind === 'clear' ? clearAll() : pending.kind === 'demo' ? loadDemo() : replaceData(pending.data)
    const message = pending.kind === 'clear' ? 'همهٔ داده‌ها پاک شدند و دفتر خالی آماده است.' : pending.kind === 'demo' ? 'دفتر نمونه بارگذاری شد.' : 'پشتیبان با موفقیت بازیابی شد.'
    notify(result.ok ? message : result.message ?? 'انجام عملیات ممکن نشد.', result.ok ? 'success' : 'error')
  }

  const dialogText = pending?.kind === 'import'
    ? { title: 'بازیابی این پشتیبان؟', message: `فایل «${pending.filename}» شامل ${formatNumber(pending.data.transactions.length)} تراکنش و ${formatNumber(Object.keys(pending.data.budgets).length)} سقف بودجه است. داده‌های فعلی دفتر با آن جایگزین می‌شوند؛ اگر لازم است، ابتدا از دفتر فعلی پشتیبان بگیرید.`, label: 'جایگزینی و بازیابی' }
    : pending?.kind === 'demo'
      ? { title: 'بارگذاری دفتر نمونه؟', message: 'تراکنش‌ها و بودجه‌های فعلی با داده‌های نمونه جایگزین می‌شوند. پیش از ادامه، از اطلاعات واقعی خود پشتیبان بگیرید.', label: 'بارگذاری نمونه' }
      : { title: 'پاک کردن همهٔ داده‌ها؟', message: 'همهٔ تراکنش‌ها و سقف‌های بودجه برای همیشه پاک می‌شوند. این کار قابل بازگشت نیست؛ دریافت پشتیبان پیش از حذف توصیه می‌شود.', label: 'پاک کردن همه' }

  return (
    <div className="page settings-page">
      <div className="page-heading"><div><h1>داده‌ها و تنظیمات</h1><p>دفتر شما روی مرورگر خودتان است؛ نگهداری و پشتیبان‌گیری آن هم دست شماست.</p></div></div>

      <section className="card storage-status-card">
        <span className="settings-hero-icon"><ShieldCheck size={29} /></span>
        <div>
          <h2>ذخیره‌سازی محلی و خصوصی</h2>
          <p>حساب‌یار در این نسخه حساب کاربری، همگام‌سازی ابری یا ارسال داده به سرور ندارد. داده‌ها در حافظهٔ همین مرورگر می‌مانند و با پاک کردن داده‌های مرورگر یا تغییر دستگاه از دسترس خارج می‌شوند.</p>
          <div className="storage-facts">
            <span><Database size={16} />{formatNumber(data.transactions.length)} تراکنش</span>
            <span><Coins size={16} />واحد پول: تومان</span>
            <span><CalendarDays size={16} />تقویم شمسی</span>
            <span className={issue ? 'text-danger' : 'text-green'}><HardDrive size={16} />{issue ? 'نیاز به بررسی ذخیره‌سازی' : 'ذخیره‌سازی فعال'}</span>
          </div>
        </div>
      </section>

      {recoveryRaw !== null && (
        <section className="card recovery-card" role="alert">
          <AlertTriangle size={24} />
          <div><h2>نسخهٔ بازیابی داده‌های قبلی</h2><p>داده‌های ذخیره‌شده قابل خواندن نبودند و بدون تغییر نگه داشته شده‌اند. نسخهٔ خام را دانلود کنید، سپس در صورت نیاز یک پشتیبان معتبر وارد کنید یا دفتر را خالی کنید.</p></div>
          <Button onClick={() => downloadText(`hesabyar-recovery-${todayISO()}.txt`, recoveryRaw, 'text/plain')}><Download size={17} />دریافت نسخهٔ خام</Button>
        </section>
      )}

      <div className="settings-grid">
        <section className="card settings-card">
          <span className="soft-icon"><Download size={23} /></span>
          <h2>دریافت پشتیبان</h2>
          <p>همهٔ تراکنش‌ها و بودجه‌ها را در یک فایل JSON بگیرید و در جای امن نگه دارید. این فایل برای بازیابی در همین برنامه ساخته می‌شود.</p>
          <Button variant="primary" onClick={() => { downloadBackup(data); notify('فایل پشتیبان دفتر آماده شد.') }}><Download size={17} />دریافت فایل پشتیبان</Button>
        </section>

        <section className="card settings-card">
          <span className="soft-icon"><Upload size={23} /></span>
          <h2>بازیابی از پشتیبان</h2>
          <p>فایل JSON خروجی حساب‌یار را انتخاب کنید. ساختار، مبلغ‌ها، تاریخ‌ها و دسته‌ها پیش از جایگزینی بررسی می‌شوند.</p>
          <input ref={fileRef} type="file" accept=".json,application/json" className="sr-only" aria-label="انتخاب فایل پشتیبان" onChange={(event) => { void onFile(event.target.files?.[0]) }} />
          <Button onClick={() => fileRef.current?.click()} disabled={importing}><Upload size={17} />{importing ? 'در حال بررسی…' : 'انتخاب فایل پشتیبان'}</Button>
        </section>

        <section className="card settings-card">
          <span className="soft-icon is-demo"><FlaskConical size={23} /></span>
          <h2>آزمایش با دفتر نمونه</h2>
          <p>برای آشنایی با نمودارها و بودجه‌بندی، داده‌های نمونه را بارگذاری کنید. این کار دفتر فعلی را جایگزین می‌کند.</p>
          <Button onClick={() => setPending({ kind: 'demo' })}><FlaskConical size={17} />بارگذاری دفتر نمونه</Button>
        </section>

        <section className="card settings-card danger-card">
          <span className="soft-icon is-expense"><Trash2 size={23} /></span>
          <h2>شروع دوباره</h2>
          <p>همهٔ تراکنش‌ها و بودجه‌ها را پاک کنید و یک دفتر خالی بسازید. این کار برگشت‌پذیر نیست.</p>
          <Button variant="danger-soft" onClick={() => setPending({ kind: 'clear' })}><Trash2 size={17} />پاک کردن همهٔ داده‌ها</Button>
        </section>
      </div>

      <p className="settings-footnote">حساب‌یار نسخهٔ ۰.۱ · ابزار ثبت شخصی، نه مشاورهٔ مالی · پشتیبان‌های مالی خود را محرمانه نگه دارید.</p>

      <ConfirmDialog open={pending !== null} onClose={() => setPending(null)} title={dialogText.title} message={dialogText.message} confirmLabel={dialogText.label} danger={pending?.kind !== 'import'} onConfirm={confirm} />
    </div>
  )
}
