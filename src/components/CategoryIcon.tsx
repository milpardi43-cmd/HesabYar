import {
  ShoppingBasket, House, BusFront, ShoppingBag, ReceiptText, HeartPulse, Ticket,
  BookOpen, Users, Shapes, BriefcaseBusiness, Laptop, TrendingUp, Gift, CirclePlus,
} from 'lucide-react'
import type { Category, CategoryIcon as CategoryIconName } from '../types'
import { getCategory } from '../data/categories'

const ICONS = {
  basket: ShoppingBasket, home: House, bus: BusFront, bag: ShoppingBag,
  receipt: ReceiptText, heart: HeartPulse, ticket: Ticket, book: BookOpen,
  users: Users, shapes: Shapes, briefcase: BriefcaseBusiness, laptop: Laptop,
  chart: TrendingUp, gift: Gift, plus: CirclePlus,
} satisfies Record<CategoryIconName, typeof ShoppingBasket>

export function CategoryIcon({ category, id, size = 19 }: { category?: Category; id?: string; size?: number }) {
  const item = category ?? getCategory(id ?? '')
  const Icon = ICONS[item.icon]
  return (
    <span className="category-icon" style={{ color: item.color, backgroundColor: `${item.color}1a` }} aria-hidden="true">
      <Icon size={size} strokeWidth={1.8} />
    </span>
  )
}
