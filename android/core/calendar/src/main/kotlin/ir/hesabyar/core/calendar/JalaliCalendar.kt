package ir.hesabyar.core.calendar

import java.time.LocalDate

data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    override fun toString(): String = "%04d/%02d/%02d".format(year, month, day)
}

/**
 * Gregorian dates are the storage standard. Jalali is a display/calendar-input concern.
 * Algorithm compatible with the widely used jalaali-js implementation for supported years.
 */
object JalaliCalendar {
    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)

    fun toJalali(date: LocalDate): JalaliDate = toJalali(date.year, date.monthValue, date.dayOfMonth)

    fun toJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val jdn = g2d(gy, gm, gd)
        return d2j(jdn)
    }

    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        requireValid(jy in 1200..1600, "سال شمسی پشتیبانی نمی‌شود.")
        requireValid(jm in 1..12, "ماه شمسی نامعتبر است.")
        val length = monthLength(jy, jm)
        requireValid(jd in 1..length, "روز شمسی نامعتبر است.")
        val g = d2g(j2d(jy, jm, jd))
        return LocalDate.of(g[0], g[1], g[2])
    }

    fun isLeap(jy: Int): Boolean = jalCal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeap(jy) -> 30
        else -> 29
    }

    fun format(date: LocalDate, withYear: Boolean = true): String {
        val j = toJalali(date)
        val month = monthNames[j.month - 1]
        return if (withYear) "${j.day} $month ${j.year}" else "${j.day} $month"
    }

    private data class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCal {
        var gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun j2d(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Int): JalaliDate {
        val gy = d2g(jdn)[0]
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                val jm = 1 + k / 31
                val jd = (k % 31) + 1
                return JalaliDate(jy, jm, jd)
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        val jm = 7 + k / 30
        val jd = (k % 30) + 1
        return JalaliDate(jy, jm, jd)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4
        d += (153 * ((gm + 9) % 12) + 2) / 5
        d += gd - 34840408
        d = d - ((gy + 100100 + (gm - 8) / 6) / 100) * 3 / 4 + 752
        return d
    }

    private fun d2g(jdn: Int): IntArray {
        var j = 4 * jdn + 139361631
        j += ((((4 * jdn + 183187720) / 146097) * 3) / 4) * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gd = ((i % 153) / 5) + 1
        val gm = ((i / 153) % 12) + 1
        val gy = j / 1461 - 100100 + (8 - gm) / 6
        return intArrayOf(gy, gm, gd)
    }

    private fun requireValid(condition: Boolean, message: String) {
        require(condition) { message }
    }
}
