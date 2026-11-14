import type { Category, TransactionType } from '../types'

export const CATEGORIES: Category[] = [
  { id: 'food', label: 'خوراک و خرید روزانه', type: 'expense', color: '#df8a5e', icon: 'basket' },
  { id: 'housing', label: 'مسکن و اجاره', type: 'expense', color: '#7189d8', icon: 'home' },
  { id: 'transport', label: 'رفت‌وآمد', type: 'expense', color: '#d9ad45', icon: 'bus' },
  { id: 'shopping', label: 'پوشاک و خرید', type: 'expense', color: '#a18ad2', icon: 'bag' },
  { id: 'bills', label: 'قبض و خدمات', type: 'expense', color: '#5fa9b7', icon: 'receipt' },
  { id: 'health', label: 'سلامت و درمان', type: 'expense', color: '#d77f98', icon: 'heart' },
  { id: 'entertainment', label: 'تفریح و سرگرمی', type: 'expense', color: '#6eaf8c', icon: 'ticket' },
  { id: 'education', label: 'آموزش', type: 'expense', color: '#7398c8', icon: 'book' },
  { id: 'family', label: 'خانواده', type: 'expense', color: '#c39368', icon: 'users' },
  { id: 'other-expense', label: 'سایر هزینه‌ها', type: 'expense', color: '#929eaa', icon: 'shapes' },
  { id: 'salary', label: 'حقوق', type: 'income', color: '#328a68', icon: 'briefcase' },
  { id: 'freelance', label: 'پروژه و فریلنسری', type: 'income', color: '#5b96c5', icon: 'laptop' },
  { id: 'investment', label: 'سود سرمایه‌گذاری', type: 'income', color: '#9b83ca', icon: 'chart' },
  { id: 'gift', label: 'هدیه و دریافتی', type: 'income', color: '#d1a247', icon: 'gift' },
  { id: 'other-income', label: 'سایر درآمدها', type: 'income', color: '#879a91', icon: 'plus' },
]

export const CATEGORY_MAP = new Map(CATEGORIES.map((category) => [category.id, category]))
export const EXPENSE_CATEGORIES = CATEGORIES.filter((category) => category.type === 'expense')
export const INCOME_CATEGORIES = CATEGORIES.filter((category) => category.type === 'income')

export function categoriesFor(type: TransactionType): Category[] {
  return type === 'expense' ? EXPENSE_CATEGORIES : INCOME_CATEGORIES
}

export function getCategory(id: string): Category {
  return CATEGORY_MAP.get(id) ?? CATEGORIES.find((category) => category.id === 'other-expense')!
}
