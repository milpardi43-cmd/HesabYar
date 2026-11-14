package ir.hesabyar.core.validation

import ir.hesabyar.core.common.MAX_MONEY
import ir.hesabyar.core.common.MAX_QUANTITY
import ir.hesabyar.core.common.requireValid

object Validators {
    fun requiredName(value: String, label: String = "نام"): String {
        val trimmed = value.trim()
        requireValid(trimmed.isNotEmpty(), "$label الزامی است.")
        requireValid(trimmed.length <= 120, "$label باید حداکثر ۱۲۰ نویسه باشد.")
        return trimmed
    }

    fun optionalPhone(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val normalized = trimmed.replace(" ", "").replace("-", "")
        requireValid(normalized.matches(Regex("^09\\d{9}$|^\\+989\\d{9}$")), "شماره موبایل معتبر نیست.")
        return normalized
    }

    fun money(value: Long, allowZero: Boolean = false, label: String = "مبلغ"): Long {
        requireValid(value >= if (allowZero) 0 else 1, "$label باید مثبت باشد.")
        requireValid(value <= MAX_MONEY, "$label خارج از محدودهٔ مجاز است.")
        return value
    }

    fun quantity(value: Long): Long {
        requireValid(value >= 1, "تعداد باید حداقل ۱ باشد.")
        requireValid(value <= MAX_QUANTITY, "تعداد خارج از محدودهٔ مجاز است.")
        return value
    }
}
