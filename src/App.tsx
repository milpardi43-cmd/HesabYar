import { Route, Routes, Link } from 'react-router-dom'
import { Compass } from 'lucide-react'
import { LedgerProvider } from './context/LedgerContext'
import { DialogProvider } from './context/DialogContext'
import { ToastProvider } from './components/Toast'
import { Layout } from './components/Layout'
import { EmptyState } from './components/EmptyState'
import { Overview } from './pages/Overview'
import { Transactions } from './pages/Transactions'
import { Budgets } from './pages/Budgets'
import { Settings } from './pages/Settings'

function NotFound() {
  return (
    <div className="card not-found">
      <EmptyState icon={<Compass size={34} />} title="این صفحه پیدا نشد" description="نشانی را بررسی کنید یا به نمای کلی دفتر برگردید." action={<Link className="btn btn-primary" to="/">بازگشت به نمای کلی</Link>} />
    </div>
  )
}

export default function App() {
  return (
    <ToastProvider>
      <LedgerProvider>
        <DialogProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Overview />} />
              <Route path="transactions" element={<Transactions />} />
              <Route path="budgets" element={<Budgets />} />
              <Route path="settings" element={<Settings />} />
              <Route path="*" element={<NotFound />} />
            </Route>
          </Routes>
        </DialogProvider>
      </LedgerProvider>
    </ToastProvider>
  )
}
