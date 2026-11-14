package ir.hesabyar.core.currency

import ir.hesabyar.core.common.MAX_MONEY
import ir.hesabyar.core.common.Toman

object TomanFormatter {
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun normalizeDigits(value: String): String = buildString {
        for (ch in value) {
            val p = PERSIAN_DIGITS.indexOf(ch)
            val a = ARABIC_DIGITS.indexOf(ch)
            append(if (p >= 0) '0' + p else if (a >= 0) '0' + a else ch)
        }
    }

    fun parse(value: String): Toman? {
        val normalized = normalizeDigits(value).trim().replace("٬", "").replace(",", "").replace(" ", "")
        if (normalized.isEmpty() || normalized.any { !it.isDigit() }) return null
        val amount = normalized.toLongOrNull() ?: return null
        return if (amount in 0..MAX_MONEY) amount else null
    }

    fun group(value: Long): String {
        val negative = value < 0
        val digits = kotlin.math.abs(value).toString()
        val grouped = buildString {
            for ((index, ch) in digits.withIndex()) {
                if (index > 0 && (digits.length - index) % 3 == 0) append('٬')
                append(ch)
            }
        }
        return (if (negative) "−" else "") + grouped
    }

    fun toPersianDigits(value: String): String = buildString {
        for (ch in value) append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch)
    }

    fun format(amount: Toman, persianDigits: Boolean = true, withUnit: Boolean = true): String {
        val grouped = group(amount)
        val text = if (persianDigits) toPersianDigits(grouped) else grouped
        return if (withUnit) "$text تومان" else text
    }
}
