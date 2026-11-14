package ir.hesabyar.core.validation

import com.google.common.truth.Truth.assertThat
import ir.hesabyar.core.common.ValidationException
import org.junit.Assert.assertThrows
import org.junit.Test

class ValidatorsTest {
    @Test fun nameIsRequired() {
        assertThrows(ValidationException::class.java) { Validators.requiredName("  ") }
        assertThat(Validators.requiredName(" علی ")).isEqualTo("علی")
    }
    @Test fun phoneValidation() {
        assertThat(Validators.optionalPhone("")).isEqualTo("")
        assertThat(Validators.optionalPhone("0912 345 6789")).isEqualTo("09123456789")
        assertThrows(ValidationException::class.java) { Validators.optionalPhone("12345") }
    }
    @Test fun moneyBounds() {
        assertThrows(ValidationException::class.java) { Validators.money(0) }
        assertThat(Validators.money(0, allowZero = true)).isEqualTo(0)
        assertThrows(ValidationException::class.java) { Validators.money(1_000_000_000_000L) }
    }
    @Test fun quantityBounds() {
        assertThrows(ValidationException::class.java) { Validators.quantity(0) }
        assertThat(Validators.quantity(5)).isEqualTo(5)
    }
}
