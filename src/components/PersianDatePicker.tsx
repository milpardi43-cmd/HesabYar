import { useEffect, useId, useRef, useState } from 'react'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react'
import {
  daysInMonth, firstWeekday, formatDate, jalaliToISO, MAX_JALALI_YEAR, MIN_JALALI_YEAR,
  monthKey, monthLabel, parseMonthKey, shiftMonth, todayISO, WEEKDAYS,
} from '../lib/dates'
import { formatNumber } from '../lib/format'

interface PersianDatePickerProps {
  value: string
  onChange: (iso: string) => void
  id?: string
  invalid?: boolean
}

export function PersianDatePicker({ value, onChange, id, invalid = false }: PersianDatePickerProps) {
  const [open, setOpen] = useState(false)
  const [view, setView] = useState(() => monthKey(value))
  const rootRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()
  const today = todayISO()

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        event.stopPropagation()
        setOpen(false)
        triggerRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const toggle = () => {
    setView(monthKey(value))
    setOpen((current) => !current)
  }
  const select = (iso: string) => {
    onChange(iso)
    setOpen(false)
    triggerRef.current?.focus()
  }
  const { year, month } = parseMonthKey(view)
  const leading = firstWeekday(view)
  const count = daysInMonth(view)

  return (
    <div className="date-picker" ref={rootRef}>
      <button
        ref={triggerRef} id={id} type="button"
        className={`field-control date-trigger${invalid ? ' is-invalid' : ''}`}
        aria-haspopup="dialog" aria-expanded={open} aria-controls={open ? panelId : undefined}
        onClick={toggle}
      >
        <CalendarDays size={18} aria-hidden="true" />
        <span>{formatDate(value)}</span>
        <ChevronDown size={16} aria-hidden="true" />
      </button>
      {open && (
        <div className="calendar-popover" id={panelId} role="dialog" aria-label="انتخاب تاریخ شمسی">
          <div className="calendar-header">
            <button type="button" className="icon-btn" aria-label="ماه قبل" disabled={view <= `${MIN_JALALI_YEAR}-01`} onClick={() => setView(shiftMonth(view, -1))}><ChevronRight size={18} /></button>
            <strong>{monthLabel(view)}</strong>
            <button type="button" className="icon-btn" aria-label="ماه بعد" disabled={view >= `${MAX_JALALI_YEAR}-12`} onClick={() => setView(shiftMonth(view, 1))}><ChevronLeft size={18} /></button>
          </div>
          <div className="calendar-grid calendar-weekdays" aria-hidden="true">
            {WEEKDAYS.map((day) => <span key={day}>{day}</span>)}
          </div>
          <div className="calendar-grid">
            {Array.from({ length: leading }, (_, index) => <span key={`blank-${index}`} />)}
            {Array.from({ length: count }, (_, index) => {
              const day = index + 1
              const iso = jalaliToISO(year, month, day)
              const selected = iso === value
              return (
                <button
                  key={day} type="button"
                  className={`calendar-day${selected ? ' is-selected' : ''}${iso === today ? ' is-today' : ''}`}
                  aria-label={formatDate(iso)} aria-pressed={selected}
                  onClick={() => select(iso)}
                >{formatNumber(day)}</button>
              )
            })}
          </div>
          <button type="button" className="calendar-today" onClick={() => select(today)}>برو به امروز</button>
        </div>
      )}
    </div>
  )
}
