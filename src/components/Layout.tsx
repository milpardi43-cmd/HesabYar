import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowLeft, ArrowLeftRight, CalendarDays, ChartPie, CircleHelp, FlaskConical,
  LayoutDashboard, Menu, ShieldCheck, WalletMinimal, X, Settings2, Download, AlertTriangle,
} from 'lucide-react'
import { useLedger } from '../context/LedgerContext'
import { useDialogs } from '../context/DialogContext'
import { formatFullDate, todayISO } from '../lib/dates'
import { downloadBackup } from '../lib/download'
import { Button } from './Button'
import { ConfirmDialog } from './ConfirmDialog'
import { Modal } from './Modal'
import { useToast } from './Toast'

const NAV_ITEMS = [
  { to: '/', label: 'نمای کلی', icon: LayoutDashboard },
  { to: '/transactions', label: 'تراکنش‌ها', icon: ArrowLeftRight },
  { to: '/budgets', label: 'بودجه‌بندی', icon: ChartPie },
  { to: '/settings', label: 'داده‌ها و تنظیمات', icon: Settings2 },
]

export function Layout() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const [guideOpen, setGuideOpen] = useState(false)
  const [clearOpen, setClearOpen] = useState(false)
  const { data, issue, clearAll } = useLedger()
  const { notify } = useToast()
  const { openAdd } = useDialogs()
  const location = useLocation()
  const navigate = useNavigate()
  const pageLabel = NAV_ITEMS.find((item) => item.to === location.pathname)?.label ?? 'صفحه پیدا نشد'

  useEffect(() => { setMobileOpen(false) }, [location.pathname])
  useEffect(() => {
    if (!mobileOpen) return
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setMobileOpen(false) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [mobileOpen])

  const startEmpty = () => {
    const result = clearAll()
    notify(result.ok ? 'دفتر خالی شد؛ حالا می‌توانید اولین تراکنش واقعی را ثبت کنید.' : result.message ?? 'انجام عملیات ممکن نشد.', result.ok ? 'success' : 'error')
  }

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">رفتن به محتوای اصلی</a>
      {mobileOpen && <button type="button" className="sidebar-overlay" aria-label="بستن منو" onClick={() => setMobileOpen(false)} />}
      <aside className={`sidebar${mobileOpen ? ' is-open' : ''}`} aria-label="ناوبری اصلی">
        <div className="sidebar-top">
          <Link to="/" className="brand" aria-label="حساب‌یار، نمای کلی">
            <span className="brand-mark"><WalletMinimal size={27} strokeWidth={1.8} /></span>
            <span><strong>حساب‌یار</strong><small>همراه پولِ شما</small></span>
          </Link>
          <button type="button" className="icon-btn sidebar-close" onClick={() => setMobileOpen(false)} aria-label="بستن منو"><X size={20} /></button>
        </div>

        <nav className="main-nav">
          <span className="nav-caption">دفتر مالی شما</span>
          {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => `nav-item${isActive ? ' is-active' : ''}`}>
              <Icon size={20} strokeWidth={1.8} />
              <span>{label}</span>
              {to === '/transactions' && data.transactions.length > 0 && <span className="nav-count">{new Intl.NumberFormat('fa-IR').format(data.transactions.length)}</span>}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="privacy-card">
            <span className="privacy-icon"><ShieldCheck size={22} /></span>
            <strong>حساب شما، فقط برای شما</strong>
            <p>داده‌ها روی همین مرورگر ذخیره می‌شوند؛ بدون حساب کاربری و ارسال به سرور.</p>
            <Link to="/settings">مدیریت داده‌ها <ArrowLeft size={15} /></Link>
          </div>
          <div className="sidebar-profile">
            <span className="profile-avatar">ح</span>
            <span><strong>دفتر شخصی</strong><small>نسخهٔ محلی · ۰.۱</small></span>
            <span className="local-dot" title="ذخیره‌سازی محلی" />
          </div>
        </div>
      </aside>

      <div className="main-shell">
        <header className="topbar">
          <div className="topbar-start">
            <button type="button" className="icon-btn mobile-menu" onClick={() => setMobileOpen(true)} aria-label="باز کردن منو" aria-expanded={mobileOpen}><Menu size={22} /></button>
            <div className="breadcrumb"><span>دفتر شخصی</span><ArrowLeft size={14} aria-hidden="true" /><strong>{pageLabel}</strong></div>
          </div>
          <div className="topbar-actions">
            <span className="today-pill"><CalendarDays size={17} aria-hidden="true" />{formatFullDate(todayISO())}</span>
            <button type="button" className="icon-btn help-button" onClick={() => setGuideOpen(true)} aria-label="راهنمای استفاده" title="راهنمای استفاده"><CircleHelp size={21} /></button>
          </div>
        </header>

        <main id="main" className="main-content" tabIndex={-1}>
          {issue === 'corrupt' ? (
            <div className="notice-banner notice-danger" role="alert">
              <AlertTriangle size={20} />
              <p><strong>داده‌های قبلی خوانده نشد.</strong> برای جلوگیری از بازنویسی، نسخهٔ اصلی حفظ شده است. آن را از تنظیمات دانلود و بررسی کنید.</p>
              <Button small onClick={() => navigate('/settings')}>بررسی داده‌ها</Button>
            </div>
          ) : issue ? (
            <div className="notice-banner notice-warning" role="alert">
              <AlertTriangle size={20} />
              <p><strong>ذخیره‌سازی مرورگر در دسترس نیست.</strong> تغییرات فعلاً فقط در حافظهٔ این صفحه‌اند؛ پیش از بستن صفحه پشتیبان بگیرید.</p>
              <Button small onClick={() => downloadBackup(data)}><Download size={16} />دریافت پشتیبان</Button>
            </div>
          ) : null}

          {data.isDemo && (
            <div className="demo-banner">
              <FlaskConical size={19} aria-hidden="true" />
              <p><strong>دفتر نمونه</strong><span>این عددها فقط برای آزمایش هستند؛ برای شروع دفتر خودتان، ابتدا دفتر نمونه را خالی کنید.</span></p>
              <Button small variant="ghost" onClick={() => setClearOpen(true)}>شروع با دفتر خالی <ArrowLeft size={15} /></Button>
            </div>
          )}

          <Outlet />
        </main>
      </div>

      <Modal open={guideOpen} onClose={() => setGuideOpen(false)} title="به حساب‌یار خوش آمدید" description="چند قدم ساده برای روشن‌تر شدن تصویر مالی شما.">
        <div className="guide-content">
          <div><span>۱</span><p><strong>ثبت درآمد و هزینه</strong>با دکمهٔ «ثبت تراکنش»، مبلغ، دسته و تاریخ شمسی را وارد کنید.</p></div>
          <div><span>۲</span><p><strong>دیدن تصویر ماه</strong>در نمای کلی، ماه را عوض کنید و نمودارها و خلاصهٔ همان ماه را ببینید.</p></div>
          <div><span>۳</span><p><strong>تعیین سقف بودجه</strong>برای دسته‌های هزینه سقف ماهانه بگذارید و میزان مصرف را دنبال کنید.</p></div>
          <div className="guide-privacy"><ShieldCheck size={21} /><p>داده‌ها فقط در همین مرورگر هستند. پاک کردن داده‌های مرورگر آن‌ها را حذف می‌کند؛ از بخش تنظیمات مرتب پشتیبان بگیرید.</p></div>
          <Button variant="primary" onClick={() => { setGuideOpen(false); openAdd() }}>ثبت اولین تراکنش</Button>
        </div>
      </Modal>

      <ConfirmDialog
        open={clearOpen} onClose={() => setClearOpen(false)}
        title="شروع با دفتر خالی؟"
        message="همهٔ تراکنش‌ها و بودجه‌های نمونه پاک می‌شوند. اگر چیزی به دفتر نمونه اضافه کرده‌اید، ابتدا پشتیبان بگیرید."
        confirmLabel="پاک کردن و شروع"
        onConfirm={startEmpty}
      />
    </div>
  )
}
