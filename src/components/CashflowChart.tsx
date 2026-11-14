import { ChartNoAxesCombined } from 'lucide-react'
import type { MonthSeries } from '../lib/analytics'
import { formatCompact, formatMoney } from '../lib/format'
import { monthLabel, monthShortLabel } from '../lib/dates'
import { EmptyState } from './EmptyState'

function niceCeiling(value: number): number {
  if (value <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  const fraction = value / power
  const nice = [1, 2, 2.5, 5, 10].find((step) => fraction <= step) ?? 10
  return nice * power
}

export function CashflowChart({ series, onSelect }: { series: MonthSeries[]; onSelect?: (key: string) => void }) {
  const hasData = series.some((item) => item.income > 0 || item.expense > 0)
  if (!hasData) {
    return <EmptyState icon={<ChartNoAxesCombined size={29} />} title="هنوز چیزی برای نمایش نیست" description="با ثبت اولین تراکنش‌ها، نمودار این بخش شکل می‌گیرد." />
  }
  const max = niceCeiling(Math.max(...series.flatMap((item) => [item.income, item.expense])))
  const ticks = [1, 0.75, 0.5, 0.25, 0]

  return (
    <div className="cashflow-chart">
      <div className="chart-y-axis" aria-hidden="true">
        {ticks.map((tick) => <span key={tick} style={{ top: `${(1 - tick) * 100}%` }}>{tick === 0 ? '۰' : formatCompact(max * tick)}</span>)}
      </div>
      <div className="chart-plot">
        <div className="chart-gridlines" aria-hidden="true">
          {ticks.map((tick) => <span key={tick} style={{ top: `${(1 - tick) * 100}%` }} />)}
        </div>
        <div className="chart-columns">
          {series.map((item) => (
            <div className="chart-column" key={item.key}>
              <div className="bar-pair">
                <button
                  type="button" className="bar bar-income"
                  style={{ height: `${Math.max(item.income / max * 100, item.income > 0 ? 2 : 0)}%` }}
                  aria-label={`${monthLabel(item.key)}، درآمد ${formatMoney(item.income)}`}
                  onClick={() => onSelect?.(item.key)}
                >
                  <span className="bar-tooltip">درآمد: {formatMoney(item.income)}</span>
                </button>
                <button
                  type="button" className="bar bar-expense"
                  style={{ height: `${Math.max(item.expense / max * 100, item.expense > 0 ? 2 : 0)}%` }}
                  aria-label={`${monthLabel(item.key)}، هزینه ${formatMoney(item.expense)}`}
                  onClick={() => onSelect?.(item.key)}
                >
                  <span className="bar-tooltip">هزینه: {formatMoney(item.expense)}</span>
                </button>
              </div>
              <span className="chart-x-label">{monthShortLabel(item.key)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
