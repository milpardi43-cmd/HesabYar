import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info, X, XCircle } from 'lucide-react'

type ToastTone = 'success' | 'error' | 'warning' | 'info'
interface ToastItem { id: number; message: string; tone: ToastTone }
interface ToastContextValue { notify: (message: string, tone?: ToastTone) => void }

const ToastContext = createContext<ToastContextValue | null>(null)
let sequence = 0

function Toast({ item, onDismiss }: { item: ToastItem; onDismiss: (id: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(item.id), 5_000)
    return () => window.clearTimeout(timer)
  }, [item.id, onDismiss])
  const Icon = item.tone === 'success' ? CheckCircle2 : item.tone === 'error' ? XCircle : item.tone === 'warning' ? AlertTriangle : Info
  return (
    <div className={`toast toast-${item.tone}`} role="status">
      <Icon size={20} aria-hidden="true" />
      <span>{item.message}</span>
      <button type="button" onClick={() => onDismiss(item.id)} aria-label="بستن پیام"><X size={16} /></button>
    </div>
  )
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const dismiss = useCallback((id: number) => setItems((current) => current.filter((item) => item.id !== id)), [])
  const notify = useCallback((message: string, tone: ToastTone = 'success') => {
    sequence += 1
    const id = sequence
    setItems((current) => [...current.slice(-2), { id, message, tone }])
  }, [])
  const value = useMemo(() => ({ notify }), [notify])
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" aria-live="polite" aria-relevant="additions">
        {items.map((item) => <Toast key={item.id} item={item} onDismiss={dismiss} />)}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside ToastProvider')
  return context
}
