import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  ArrowDownLeft, ArrowLeft, ArrowUpRight, ChartNoAxesCombined, ChevronLeft,
  PiggyBank, Plus, ReceiptText, Target, TrendingDown, TrendingUp, Wallet,
} from 'lucide-react'
import { useLedger } from '../context/LedgerContext'
import { useDialogs } from '../context/DialogContext'
import { getCategory } from '../data/categories'
import {
  budgetSummary, categoryTotals, inMonth, monthlySeries, percentChange, sortTransactions, totals,
} from '../lib/analytics'
import { currentMonthKey, lastMonths, monthLabel, shiftMonth } from '../lib/dates'
import { formatMoney, formatPercent } from '../lib/format'
import { Button } from '../components/Button'
import { CashflowChart } from '../components/CashflowChart'
import { CategoryIcon } from '../components/CategoryIcon'
import { DonutChart } from '../components/DonutChart'
import { EmptyState } from '../components/EmptyState'
import { PeriodPicker } from '../components/PeriodPicker'
import { TransactionRow } from '../components/TransactionRow'

function TrendBadge({ current, previous, kind }: { current: number; previous: number; kind: 'income' | 'expense' }) {
  const change = percentChange(current, previous)
  if (change === null) return <span className="stat-comparison is-neutral">هنوز مقایسه‌ای با ماه قبل نیست</span>
  if (Math.round(change) === 0) return <span className="stat-comparison is-neutral">بدون تغییر نسبت به ماه قبل</span>
  const up = change > 0
  const good = kind === 'income' ? up : !up
  const Icon = up ? TrendingUp : TrendingDown
  return (
    <span className={`stat-comparison ${good ? 'is-good' : 'is-bad'}`}>
      <span className="trend-chip"><Icon size={14} />{formatPercent(Math.abs(change))}</span>
      {up ? 'بیشتر' : 'کمتر'} از ماه قبل
    </span>
  )
}

export function Overview() {
  const { data } = useLedger()
  const { openAdd, openEdit } = useDialogs()
  const navigate = useNavigate()
  const [month, setMonth] = useState(currentMonthKey)

  const view = useMemo(() => {
    const monthly = inMonth(data.transactions, month)
    const previous = totals(inMonth(data.transactions, shiftMonth(month, -1)))
    const monthTotals = totals(monthly)
    return {
      monthly, monthTotals, previous,
      allTotals: totals(data.transactions),
      series: monthlySeries(data.transactions, lastMonths(month, 6)),
      categories: categoryTotals(monthly, 'expense'),
      recent: sortTransactions(monthly).slice(0, 5),
      budget: budgetSummary(data, month),
    }
  }, [data, month])

  const savingsRate = view.monthTotals.income > 0
    ? Math.round(view.monthTotals.balance / view.monthTotals.income * 100)
    : null
  const budgetPercent = Math.min(Math.max(view.budget.percent, 0), 100)
  const budgetItems = [...view.budget.items].sort((a, b) => b.percent - a.percent).slice(0, 3)

  return (
    <div className="page overview-page">
      <div className="page-heading">
        <div>
          <h1>نمای کلی</h1>
          <p>یک نگاه روشن به درآمد، هزینه و پس‌انداز شما.</p>
        </div>
        <Button variant="primary" onClick={openAdd}><Plus size={19} />ثبت تراکنش</Button>
      </div>

      <div className="page-toolbar">
        <PeriodPicker value={month} onChange={setMonth} />
        <span className="currency-note">همهٔ مبلغ‌ها به تومان</span>
      </div>

      <section className="stats-grid" aria-label="خلاصهٔ مالی">
        <article className="stat-card stat-balance">
          <div className="stat-top"><span>مانده کل دفتر</span><span className="stat-icon"><Wallet size={21} /></span></div>
          <strong className="stat-value">{formatMoney(view.allTotals.balance)}</strong>
          <div className="stat-bottom">
            <span>بر پایهٔ همهٔ تراکنش‌ها</span>
            {savingsRate !== null && <span className="savings-chip"><PiggyBank size={14} />پس‌انداز این ماه {formatPercent(savingsRate)}</span>}
          </div>
        </article>
        <article className="stat-card">
          <div className="stat-top"><span>درآمد {monthLabel(month, false)}</span><span className="stat-icon is-income"><ArrowDownLeft size={22} /></span></div>
          <strong className="stat-value">{formatMoney(view.monthTotals.income)}</strong>
          <TrendBadge current={view.monthTotals.income} previous={view.previous.income} kind="income" />
        </article>
        <article className="stat-card">
          <div className="stat-top"><span>هزینهٔ {monthLabel(month, false)}</span><span className="stat-icon is-expense"><ArrowUpRight size={22} /></span></div>
          <strong className="stat-value">{formatMoney(view.monthTotals.expense)}</strong>
          <TrendBadge current={view.monthTotals.expense} previous={view.previous.expense} kind="expense" />
        </article>
      </section>

      <div className="dashboard-grid charts-grid">
        <section className="card chart-card">
          <header className="card-header">
            <div><h2>جریان درآمد و هزینه</h2><p>شش ماه منتهی به {monthLabel(month)}</p></div>
            <div className="chart-legend" aria-hidden="true"><span><i className="dot-income" />درآمد</span><span><i className="dot-expense" />هزینه</span></div>
          </header>
          <CashflowChart series={view.series} onSelect={setMonth} />
          <p className="chart-hint"><ChartNoAxesCombined size={14} />برای دیدن خلاصهٔ هر ماه، روی ستون‌های آن بزنید.</p>
        </section>

        <section className="card donut-card">
          <header className="card-header">
            <div><h2>هزینه‌ها به تفکیک</h2><p>{monthLabel(month)}</p></div>
            <button type="button" className="icon-btn" aria-label="دیدن جزئیات هزینه‌ها" onClick={() => navigate(`/transactions?type=expense&month=${month}`)}><ChevronLeft size={19} /></button>
          </header>
          <DonutChart items={view.categories} total={view.monthTotals.expense} />
          {view.monthTotals.expense > 0 && <Link className="card-footer-link" to={`/transactions?type=expense&month=${month}`}>دیدن جزئیات هزینه‌ها <ArrowLeft size={15} /></Link>}
        </section>
      </div>

      <div className="dashboard-grid bottom-grid">
        <section className="card recent-card">
          <header className="card-header">
            <div><h2>آخرین تراکنش‌ها</h2><p>{monthLabel(month)}</p></div>
            <Link className="text-link" to="/transactions">همهٔ تراکنش‌ها <ArrowLeft size={15} /></Link>
          </header>
          {view.recent.length > 0 ? (
            <ul className="transaction-list">{view.recent.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction} onEdit={openEdit} />)}</ul>
          ) : (
            <EmptyState icon={<ReceiptText size={29} />} title="این ماه هنوز خالی است" description="اولین درآمد یا هزینهٔ این ماه را ثبت کنید." action={<Button small variant="primary" onClick={openAdd}><Plus size={16} />ثبت تراکنش</Button>} />
          )}
        </section>

        <section className="card budget-snapshot">
          <header className="card-header">
            <div><h2>نبض بودجهٔ این ماه</h2><p>{monthLabel(month)}</p></div>
            <span className="soft-icon"><Target size={20} /></span>
          </header>
          {view.budget.limit > 0 ? (
            <div className="budget-snapshot-body">
              <span className="muted">باقی‌ماندهٔ سقف بودجه</span>
              <strong className={view.budget.remaining < 0 ? 'text-danger' : 'text-green'}>{formatMoney(view.budget.remaining)}</strong>
              <div className={`progress-track${view.budget.percent > 100 ? ' is-over' : view.budget.percent >= 80 ? ' is-near' : ''}`} role="progressbar" aria-label="مصرف کل بودجه" aria-valuemin={0} aria-valuemax={100} aria-valuenow={budgetPercent} aria-valuetext={`${formatMoney(view.budget.spent)} از ${formatMoney(view.budget.limit)}`}>
                <span style={{ width: `${budgetPercent}%` }} />
              </div>
              <p className="budget-caption">{formatMoney(view.budget.spent)} از {formatMoney(view.budget.limit)}</p>
              <ul className="mini-budget-list">
                {budgetItems.map((item) => (
                  <li key={item.categoryId}>
                    <CategoryIcon id={item.categoryId} size={15} />
                    <span>{getCategory(item.categoryId).label}</span>
                    <strong className={item.percent > 100 ? 'text-danger' : ''}>{formatPercent(item.percent)}</strong>
                  </li>
                ))}
              </ul>
              <Link className="card-footer-link" to="/budgets">مدیریت بودجه‌ها <ArrowLeft size={15} /></Link>
            </div>
          ) : (
            <EmptyState icon={<Target size={29} />} title="برای پولتان برنامه بگذارید" description="برای دسته‌های هزینه سقف ماهانه تعیین کنید." action={<Button small onClick={() => navigate('/budgets')}><Plus size={16} />تعیین بودجه</Button>} />
          )}
        </section>
      </div>
    </div>
  )
}
