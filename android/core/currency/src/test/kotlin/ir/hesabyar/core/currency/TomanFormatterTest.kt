package ir.hesabyar.core.currency

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TomanFormatterTest {
    @Test fun parsesPersianAndGroupedDigits() {
        assertThat(TomanFormatter.parse("۱۲۳٬۴۵۶")).isEqualTo(123456L)
        assertThat(TomanFormatter.parse("123,456")).isEqualTo(123456L)
        assertThat(TomanFormatter.parse("abc")).isNull()
        assertThat(TomanFormatter.parse("-1")).isNull()
    }

    @Test fun formatsGroupedPersian() {
        assertThat(TomanFormatter.format(1200000, withUnit = false)).isEqualTo("۱٬۲۰۰٬۰۰۰")
    }
}
