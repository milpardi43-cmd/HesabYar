import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { currentMonthKey, MAX_JALALI_YEAR, MIN_JALALI_YEAR, monthLabel, shiftMonth } from '../lib/dates'

export function PeriodPicker({ value, onChange }: { value: string; onChange: (key: string) => void }) {
  const current = currentMonthKey()
  return (
    <div className="period-picker" aria-label="ماه گزارش">
      <button
        type="button" className="icon-btn" aria-label="ماه قبل" title="ماه قبل"
        disabled={value <= `${MIN_JALALI_YEAR}-01`}
        onClick={() => onChange(shiftMonth(value, -1))}
      ><ChevronRight size={18} /></button>
      <span className="period-label"><CalendarDays size={17} aria-hidden="true" />{monthLabel(value)}</span>
      <button
        type="button" className="icon-btn" aria-label="ماه بعد" title="ماه بعد"
        disabled={value >= `${MAX_JALALI_YEAR}-12`}
        onClick={() => onChange(shiftMonth(value, 1))}
      ><ChevronLeft size={18} /></button>
      {value !== current && <button type="button" className="period-today" onClick={() => onChange(current)}>این ماه</button>}
    </div>
  )
}
