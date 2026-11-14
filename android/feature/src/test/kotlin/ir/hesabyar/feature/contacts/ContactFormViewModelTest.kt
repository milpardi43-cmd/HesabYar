package ir.hesabyar.feature.contacts

import com.google.common.truth.Truth.assertThat
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.domain.model.Contact
import ir.hesabyar.domain.model.ContactType
import ir.hesabyar.domain.repository.ContactCommand
import ir.hesabyar.domain.repository.ContactLedgerEntry
import ir.hesabyar.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContactFormViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeRepo : ContactRepository {
        var last: ContactCommand? = null
        override fun observeLedger(contactId: Long): Flow<List<ContactLedgerEntry>> = emptyFlow()
        override fun observeContacts(businessId: Long): Flow<List<Contact>> = emptyFlow()
        override fun searchContacts(businessId: Long, query: String): Flow<List<Contact>> = emptyFlow()
        override suspend fun getById(id: Long): Contact? = null
        override suspend fun add(command: ContactCommand): OperationResult<Contact> {
            last = command
            return OperationResult.Success(Contact(id = 1, businessId = 1, name = command.name, phone = command.phone, contactType = command.contactType, initialBalance = command.initialBalance, currentBalance = command.initialBalance))
        }
        override suspend fun update(command: ContactCommand): OperationResult<Contact> = add(command)
    }

    @Test fun creditorOpeningBalanceIsNegative() {
        val repo = FakeRepo()
        val vm = ContactFormViewModel(repo)
        vm.update { it.copy(name = "تأمین‌کننده", type = ContactType.SUPPLIER, balanceInput = "500", debtor = false) }
        vm.submit()
        assertThat(repo.last?.initialBalance).isEqualTo(-500L)
        assertThat(vm.state.value.saved).isTrue()
    }

    @Test fun debtorOpeningBalanceIsPositive() {
        val repo = FakeRepo()
        val vm = ContactFormViewModel(repo)
        vm.update { it.copy(name = "مشتری", balanceInput = "۳۰۰", debtor = true) }
        vm.submit()
        assertThat(repo.last?.initialBalance).isEqualTo(300L)
    }
}
