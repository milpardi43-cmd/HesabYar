import { ChartPie } from 'lucide-react'
import type { CategoryTotal } from '../lib/analytics'
import { formatCompact, formatMoney, formatPercent } from '../lib/format'
import { EmptyState } from './EmptyState'

export function DonutChart({ items, total }: { items: CategoryTotal[]; total: number }) {
  if (total <= 0 || items.length === 0) {
    return <EmptyState icon={<ChartPie size={29} />} title="هزینه‌ای ثبت نشده" description="هزینه‌های این ماه اینجا به تفکیک دسته‌بندی دیده می‌شوند." />
  }

  const visible = items.slice(0, 4)
  const rest = items.slice(4)
  if (rest.length > 0) {
    visible.push({
      categoryId: 'rest', label: 'سایر دسته‌ها', color: '#b8c2bc',
      amount: rest.reduce((sum, item) => sum + item.amount, 0),
      percent: rest.reduce((sum, item) => sum + item.percent, 0),
    })
  }
  let cursor = 0
  const stops = visible.map((item) => {
    const start = cursor
    cursor += item.percent
    return `${item.color} ${start}% ${cursor}%`
  }).join(', ')

  return (
    <div className="donut-layout">
      <div className="donut" style={{ background: `conic-gradient(${stops})` }} role="img" aria-label={`مجموع هزینه‌ها ${formatMoney(total)}`}>
        <div className="donut-hole">
          <strong>{formatCompact(total)}</strong>
          <span>تومان هزینه</span>
        </div>
      </div>
      <ul className="donut-legend">
        {visible.map((item) => (
          <li key={item.categoryId}>
            <span className="legend-dot" style={{ backgroundColor: item.color }} aria-hidden="true" />
            <span className="legend-label">{item.label}</span>
            <span className="legend-percent">{formatPercent(item.percent)}</span>
            <span className="sr-only">{formatMoney(item.amount)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
