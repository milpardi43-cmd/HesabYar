package ir.hesabyar.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.todayEnd
import ir.hesabyar.feature.common.todayStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class DashboardState(
    val loading: Boolean = true,
    val cashBalance: Long = 0,
    val today: ReportSummary = ReportSummary(),
    val cashToday: CashReport = CashReport(0, 0, 0, 0),
    val receivables: Long = 0,
    val payables: Long = 0,
    val recent: List<TransactionHeader> = emptyList(),
    val lowStock: List<Product> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val contactRepository: ContactRepository,
    private val accountingRepository: AccountingRepository,
    private val productRepository: ProductRepository,
    private val reportRepository: ReportRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(businessId: Long): StateFlow<DashboardState> =
        combine(
            accountRepository.observeAccounts(businessId),
            contactRepository.observeContacts(businessId),
            accountingRepository.observeRecent(businessId, 10),
            productRepository.observeLowStock(businessId),
        ) { accounts, contacts, recent, low ->
            val today = reportRepository.dailyReport(businessId, todayStart(), todayEnd())
            val cash = reportRepository.cashReport(businessId, todayStart(), todayEnd())
            DashboardState(
                loading = false,
                cashBalance = accounts.sumOf { it.currentBalance },
                today = today,
                cashToday = cash,
                receivables = contacts.filter { it.currentBalance > 0 }.sumOf { it.currentBalance },
                payables = contacts.filter { it.currentBalance < 0 }.sumOf { -it.currentBalance },
                recent = recent,
                lowStock = low,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())
}
