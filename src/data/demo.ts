import type { LedgerData, Transaction } from '../types'
import { currentMonthKey, isoToJalali, jalaliToISO, lastMonths, parseMonthKey, todayISO } from '../lib/dates'

interface DemoEntry {
  day: number
  categoryId: string
  title: string
  amount: number
  note?: string
}

const ENTRIES: DemoEntry[] = [
  { day: 1, categoryId: 'salary', title: 'حقوق ماهانه', amount: 45_000_000, note: 'واریز حقوق شرکت' },
  { day: 2, categoryId: 'housing', title: 'اجارهٔ خانه', amount: 12_000_000 },
  { day: 3, categoryId: 'food', title: 'خرید هفتگی خانه', amount: 1_850_000, note: 'میوه، سبزیجات و خرید سوپرمارکت' },
  { day: 5, categoryId: 'transport', title: 'شارژ کارت مترو', amount: 650_000 },
  { day: 6, categoryId: 'bills', title: 'قبض برق و اینترنت', amount: 1_200_000 },
  { day: 8, categoryId: 'freelance', title: 'پروژهٔ طراحی سایت', amount: 8_500_000, note: 'تسویهٔ مرحلهٔ دوم' },
  { day: 9, categoryId: 'shopping', title: 'خرید لباس', amount: 2_400_000 },
  { day: 11, categoryId: 'health', title: 'ویزیت و دارو', amount: 850_000 },
  { day: 13, categoryId: 'entertainment', title: 'سینما و کافه', amount: 1_100_000, note: 'آخر هفته با دوستان' },
  { day: 16, categoryId: 'food', title: 'خرید میوه و نان', amount: 1_450_000 },
  { day: 18, categoryId: 'food', title: 'کافه با دوستان', amount: 480_000 },
  { day: 20, categoryId: 'transport', title: 'تاکسی اینترنتی', amount: 720_000 },
  { day: 22, categoryId: 'education', title: 'دورهٔ آنلاین زبان', amount: 1_600_000 },
  { day: 24, categoryId: 'food', title: 'خرید سوپرمارکت', amount: 1_300_000 },
  { day: 26, categoryId: 'family', title: 'هدیهٔ تولد', amount: 1_500_000 },
  { day: 28, categoryId: 'investment', title: 'سود صندوق درآمد ثابت', amount: 950_000 },
]

/** Deterministic, clearly labelled sample data. Never includes future dates. */
export function createDemoLedger(now = new Date()): LedgerData {
  const current = currentMonthKey(now)
  const today = isoToJalali(todayISO(now))
  const transactions: Transaction[] = []

  lastMonths(current, 6).forEach((key, monthIndex) => {
    const { year, month } = parseMonthKey(key)
    const isCurrent = key === current
    const factor = 1 + (monthIndex % 3) * 0.035
    for (const entry of ENTRIES) {
      if (isCurrent && entry.day > today.jd) continue
      const date = jalaliToISO(year, month, entry.day)
      const timestamp = `${date}T10:00:00.000Z`
      transactions.push({
        id: `demo-${key}-${entry.categoryId}-${entry.day}`,
        type: ['salary', 'freelance', 'investment'].includes(entry.categoryId) ? 'income' : 'expense',
        amount: Math.round(entry.amount * (isCurrent ? 1 : factor) / 10_000) * 10_000,
        title: entry.title,
        note: entry.note ?? '',
        categoryId: entry.categoryId,
        date,
        createdAt: timestamp,
        updatedAt: timestamp,
      })
    }
  })

  return {
    schemaVersion: 1,
    currency: 'toman',
    isDemo: true,
    transactions,
    budgets: {
      food: 5_000_000,
      housing: 12_000_000,
      transport: 2_000_000,
      shopping: 3_000_000,
      bills: 2_000_000,
      entertainment: 1_500_000,
    },
  }
}
