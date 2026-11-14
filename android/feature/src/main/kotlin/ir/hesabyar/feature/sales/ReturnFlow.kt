package ir.hesabyar.feature.sales

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.accounting.SaleReturnCommand
import ir.hesabyar.domain.accounting.SaleReturnLineCommand
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReturnLineState(val item: SaleItemSnapshot, val productName: String, val quantity: Long, val sellable: Boolean)
data class ReturnState(
    val original: TransactionHeader? = null, val lines: List<ReturnLineState> = emptyList(),
    val accounts: List<Account> = emptyList(), val accountId: Long? = null, val refundInput: String = "",
    val reason: String = "", val operationId: String = newOperationId(),
    val loading: Boolean = false, val error: String? = null, val successId: Long? = null,
)

@HiltViewModel
class ReturnViewModel @Inject constructor(
    private val accountingRepository: AccountingRepository, private val productRepository: ProductRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReturnState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(transactionId: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch {
            val tx = accountingRepository.getTransaction(transactionId)
            val items = accountingRepository.getSaleItems(transactionId)
            val lines = items.map { item -> ReturnLineState(item, productRepository.getById(item.productId)?.name ?: "کالا", 0, true) }
            _state.value = _state.value.copy(original = tx, lines = lines)
            if (tx != null) accountRepository.observeAccounts(tx.businessId).collect { a -> _state.value = _state.value.copy(accounts = a, accountId = _state.value.accountId ?: a.firstOrNull { it.accountType == AccountType.CASH }?.id) }
        }
    }
    fun updateLine(index: Int, quantity: Long? = null, sellable: Boolean? = null) {
        val lines = _state.value.lines.toMutableList(); if (index !in lines.indices) return
        lines[index] = lines[index].copy(quantity = quantity ?: lines[index].quantity, sellable = sellable ?: lines[index].sellable)
        _state.value = _state.value.copy(lines = lines)
    }
    fun update(block: (ReturnState) -> ReturnState) { _state.value = block(_state.value) }
    fun submit() {
        val s = _state.value; val original = s.original ?: return
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val lines = s.lines.filter { it.quantity > 0 }.map { SaleReturnLineCommand(it.item.id, it.quantity, it.sellable) }
            accountingRepository.recordSaleReturn(SaleReturnCommand(s.operationId, System.currentTimeMillis(), original.id, lines, parseMoneyInput(s.refundInput) ?: 0, s.accountId, reason = s.reason)).handle(
                { tx -> update { it.copy(loading = false, successId = tx.id) } },
                { m -> update { it.copy(loading = false, error = m) } })
        }
    }
}

@Composable
fun ReturnScreen(transactionId: Long, settings: AppSettings, navController: NavController, viewModel: ReturnViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(transactionId) { viewModel.load(transactionId) }
    LaunchedEffect(state.successId) { state.successId?.let { navController.navigate(Routes.transaction(it)) { popUpTo(Routes.DASHBOARD) } } }
    HyScreen("برگشت فروش", navController) {
        HyErrorBanner(state.error) { viewModel.update { it.copy(error = null) } }
        state.original?.let { Text("فروش ${it.transactionCode} | ${formatMoney(it.amount, settings)}", style = MaterialTheme.typography.titleSmall) }
        state.lines.forEachIndexed { i, line ->
            val available = line.item.quantity - line.item.returnedQuantity
            HyCard {
                Text(line.productName, style = MaterialTheme.typography.titleSmall)
                Text("فروش‌رفته: ${formatNumber(line.item.quantity, settings)} | قبلاً مرجوع: ${formatNumber(line.item.returnedQuantity, settings)} | قابل مرجوع: ${formatNumber(available, settings)}", style = MaterialTheme.typography.bodySmall)
                HyField(line.quantity.toString(), { v -> v.toLongOrNull()?.let { viewModel.updateLine(i, quantity = it.coerceIn(0, available)) } }, "تعداد مرجوعی")
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = line.sellable, onCheckedChange = { viewModel.updateLine(i, sellable = it) })
                    Text("کالای سالم است و به موجودی برگردد")
                }
            }
        }
        Text("کالای غیرقابل‌فروش به موجودی برنمی‌گردد و بهای آن به‌عنوان زیان در بهای تمام‌شده باقی می‌ماند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyField(state.refundInput, { v -> viewModel.update { it.copy(refundInput = v) } }, "مبلغ بازپرداخت نقدی")
        SearchPickerInline("حساب بازپرداخت", state.accounts, { it.title }, state.accountId) { a -> viewModel.update { it.copy(accountId = a.id) } }
        HyField(state.reason, { v -> viewModel.update { it.copy(reason = v) } }, "دلیل برگشت *", singleLine = false)
        HyButton(if (state.loading) "در حال ثبت…" else "ثبت برگشت فروش", { viewModel.submit() }, enabled = !state.loading)
    }
}
