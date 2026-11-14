import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import type { Transaction } from '../types'
import { TransactionDialog } from '../components/TransactionDialog'

interface DialogContextValue {
  openAdd: () => void
  openEdit: (transaction: Transaction) => void
}

const DialogContext = createContext<DialogContextValue | null>(null)

export function DialogProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ open: boolean; transaction?: Transaction }>({ open: false })
  const openAdd = useCallback(() => setState({ open: true }), [])
  const openEdit = useCallback((transaction: Transaction) => setState({ open: true, transaction }), [])
  const close = useCallback(() => setState((current) => ({ ...current, open: false })), [])
  const value = useMemo(() => ({ openAdd, openEdit }), [openAdd, openEdit])
  return (
    <DialogContext.Provider value={value}>
      {children}
      <TransactionDialog open={state.open} transaction={state.transaction} onClose={close} />
    </DialogContext.Provider>
  )
}

export function useDialogs(): DialogContextValue {
  const context = useContext(DialogContext)
  if (!context) throw new Error('useDialogs must be used inside DialogProvider')
  return context
}
