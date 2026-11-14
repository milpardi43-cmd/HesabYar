package ir.hesabyar.feature.sales

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.accounting.PaymentCommand
import ir.hesabyar.domain.accounting.PaymentDirection
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaymentState(
    val contact: Contact? = null, val accounts: List<Account> = emptyList(), val accountId: Long? = null,
    val amountInput: String = "", val method: PaymentMethod = PaymentMethod.CASH, val description: String = "",
    val operationId: String = newOperationId(), val loading: Boolean = false, val error: String? = null, val successId: Long? = null,
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val contactRepository: ContactRepository, private val accountRepository: AccountRepository,
    private val accountingRepository: AccountingRepository, private val businessRepository: BusinessRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PaymentState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(contactId: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch {
            val contact = contactRepository.getById(contactId)
            val business = businessRepository.getBusiness()
            _state.value = _state.value.copy(contact = contact, amountInput = kotlin.math.abs(contact?.currentBalance ?: 0).toString())
            if (business != null) accountRepository.observeAccounts(business.id).collect { a -> _state.value = _state.value.copy(accounts = a, accountId = _state.value.accountId ?: a.firstOrNull { it.accountType == AccountType.CASH }?.id) }
        }
    }
    fun update(block: (PaymentState) -> PaymentState) { _state.value = block(_state.value) }
    fun settle() { update { it.copy(amountInput = kotlin.math.abs(it.contact?.currentBalance ?: 0).toString()) } }
    fun submit(contactId: Long, direction: PaymentDirection) {
        val s = _state.value; update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            accountingRepository.recordPayment(PaymentCommand(s.operationId, System.currentTimeMillis(), contactId, s.accountId ?: 0, parseMoneyInput(s.amountInput) ?: 0, direction, s.method, s.description)).handle(
                { tx -> update { it.copy(loading = false, successId = tx.id) } },
                { m -> update { it.copy(loading = false, error = m) } })
        }
    }
}

@Composable
fun PaymentScreen(contactId: Long, directionArg: String, settings: AppSettings, navController: NavController, viewModel: PaymentViewModel = hiltViewModel()) {
    val direction = if (directionArg == "receive") PaymentDirection.RECEIVE else PaymentDirection.PAY
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(contactId) { viewModel.load(contactId) }
    LaunchedEffect(state.successId) { state.successId?.let { navController.navigate(Routes.transaction(it)) { popUpTo(Routes.DASHBOARD) } } }
    val title = if (direction == PaymentDirection.RECEIVE) "دریافت وجه" else "پرداخت وجه"
    HyScreen(title, navController) {
        HyErrorBanner(state.error) { viewModel.update { it.copy(error = null) } }
        state.contact?.let { c ->
            HyCard {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(if (c.currentBalance > 0) "بدهکار: ${formatMoney(c.currentBalance, settings)}" else if (c.currentBalance < 0) "بستانکار: ${formatMoney(-c.currentBalance, settings)}" else "تسویه", style = MaterialTheme.typography.bodyMedium)
            }
        }
        MoneyField(state.amountInput, { v -> viewModel.update { it.copy(amountInput = v) } }, "مبلغ")
        HyOutlinedButton("تسویهٔ کامل", { viewModel.settle() })
        Text(if (direction == PaymentDirection.RECEIVE) "دریافت طلب، درآمد جدید نیست؛ فقط صندوق زیاد و بدهی مشتری کم می‌شود." else "پرداخت بدهی، هزینهٔ جدید نیست؛ فقط صندوق کم و بدهی شما کم می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SearchPickerInline("حساب", state.accounts, { it.title }, state.accountId) { a -> viewModel.update { it.copy(accountId = a.id) } }
        OutlinedButton(onClick = { viewModel.update { it.copy(method = if (it.method == PaymentMethod.CASH) PaymentMethod.BANK_TRANSFER else PaymentMethod.CASH) } }, modifier = Modifier.fillMaxWidth()) { Text("روش: ${state.method.label}") }
        HyField(state.description, { v -> viewModel.update { it.copy(description = v) } }, "توضیح")
        HyButton(if (state.loading) "در حال ثبت…" else title, { viewModel.submit(contactId, direction) }, enabled = !state.loading)
    }
}
