package ir.hesabyar.core.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class JalaliCalendarTest {
    @Test fun knownConversions() {
        assertThat(JalaliCalendar.toJalali(LocalDate.of(2026, 11, 14))).isEqualTo(JalaliDate(1405, 8, 23))
        assertThat(JalaliCalendar.toGregorian(1405, 8, 23)).isEqualTo(LocalDate.of(2026, 11, 14))
        assertThat(JalaliCalendar.toGregorian(1403, 1, 1)).isEqualTo(LocalDate.of(2024, 3, 20))
    }

    @Test fun leapYearMonthLength() {
        assertThat(JalaliCalendar.monthLength(1403, 12)).isEqualTo(30)
        assertThat(JalaliCalendar.monthLength(1404, 12)).isEqualTo(29)
    }

    @Test fun roundTripManyDates() {
        var date = LocalDate.of(2020, 1, 1)
        repeat(2000) {
            val j = JalaliCalendar.toJalali(date)
            assertThat(JalaliCalendar.toGregorian(j.year, j.month, j.day)).isEqualTo(date)
            date = date.plusDays(1)
        }
    }
}
