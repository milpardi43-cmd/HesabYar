package ir.hesabyar.core.common

/** All stored money is whole Toman. No Float/Double is allowed in the accounting core. */
typealias Toman = Long
typealias Quantity = Long

const val MAX_MONEY: Toman = 999_999_999_999L
const val MAX_QUANTITY: Quantity = 1_000_000L

fun Long.safeAdd(other: Long): Long = Math.addExact(this, other)
fun Long.safeSub(other: Long): Long = Math.subtractExact(this, other)
fun Long.safeMul(other: Long): Long = Math.multiplyExact(this, other)
