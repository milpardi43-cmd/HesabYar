import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { LedgerProvider } from '../context/LedgerContext'
import { ToastProvider } from './Toast'
import { TransactionDialog } from './TransactionDialog'
import { STORAGE_KEY } from '../lib/storage'

function setup() {
  const onClose = vi.fn()
  render(
    <ToastProvider>
      <LedgerProvider>
        <TransactionDialog open onClose={onClose} />
      </LedgerProvider>
    </ToastProvider>,
  )
  return { onClose, user: userEvent.setup() }
}

describe('TransactionDialog', () => {
  it('shows field-level validation before saving', async () => {
    const { user, onClose } = setup()
    await user.click(screen.getByRole('button', { name: 'ثبت تراکنش' }))
    expect(screen.getByText(/یک مبلغ صحیح و مثبت به تومان وارد کنید/)).toBeInTheDocument()
    expect(screen.getByText('برای تراکنش یک عنوان بنویسید.')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('accepts Persian digits and persists a new expense', async () => {
    const { user, onClose } = setup()
    await user.type(screen.getByLabelText(/مبلغ/), '۱۲۳٬۴۵۶')
    await user.type(screen.getByLabelText(/عنوان تراکنش/), 'تست خرید نان')
    await user.click(screen.getByRole('button', { name: 'ثبت تراکنش' }))
    expect(onClose).toHaveBeenCalledTimes(1)
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    const transaction = saved.transactions.find((item: { title: string }) => item.title === 'تست خرید نان')
    expect(transaction).toMatchObject({ amount: 123456, type: 'expense', categoryId: 'food' })
  })
})
