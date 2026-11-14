package ir.hesabyar.feature.accounts

import androidx.compose.foundation.clickable
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
import ir.hesabyar.domain.accounting.AccountAdjustmentCommand
import ir.hesabyar.domain.accounting.TransferCommand
import ir.hesabyar.domain.model.Account
import ir.hesabyar.domain.repository.AccountLedgerEntry
import ir.hesabyar.domain.repository.AccountRepository
import ir.hesabyar.domain.repository.AccountingRepository
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.sales.SummaryRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountsState(val accounts: List<Account> = emptyList(), val statement: List<AccountLedgerEntry> = emptyList(), val selected: Long? = null, val error: String? = null)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository, private val accountingRepository: AccountingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AccountsState())
    val state = _state.asStateFlow()
    private var loaded = false
    private var statementJob: kotlinx.coroutines.Job? = null
    fun load(businessId: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch { accountRepository.observeAccounts(businessId).collect { a -> _state.value = _state.value.copy(accounts = a, selected = _state.value.selected ?: a.firstOrNull()?.id); _state.value.selected?.let { observeStatement(it) } } }
    }
    fun select(id: Long) { _state.value = _state.value.copy(selected = id); observeStatement(id) }
    private fun observeStatement(id: Long) { statementJob?.cancel(); statementJob = viewModelScope.launch { accountRepository.observeStatement(id).collect { s -> _state.value = _state.value.copy(statement = s) } } }
    fun transfer(from: Long, to: Long, amount: Long) { viewModelScope.launch { accountingRepository.recordTransfer(TransferCommand(newOperationId(), System.currentTimeMillis(), from, to, amount)).handle({}, { m -> _state.value = _state.value.copy(error = m) }) } }
    fun adjust(accountId: Long, delta: Long, reason: String) { viewModelScope.launch { accountingRepository.adjustAccount(AccountAdjustmentCommand(newOperationId(), System.currentTimeMillis(), accountId, delta, reason)).handle({}, { m -> _state.value = _state.value.copy(error = m) }) } }
}

@Composable
fun AccountsScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: AccountsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    var transferDialog by remember { mutableStateOf(false) }
    var adjustDialog by remember { mutableStateOf(false) }
    var fromId by remember { mutableStateOf<Long?>(null) }
    var toId by remember { mutableStateOf<Long?>(null) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var increase by remember { mutableStateOf(true) }
    HyScreen("صندوق و حساب‌ها", navController) {
        HyErrorBanner(state.error) { }
        state.accounts.forEach { account ->
            HyCard(Modifier.clickable { viewModel.select(account.id) }) {
                SummaryRow(account.title, formatMoney(account.currentBalance, settings))
                Text(account.accountType.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HyButton("انتقال بین حساب‌ها", { transferDialog = true }, Modifier.weight(1f))
            HyOutlinedButton("اصلاح صندوق", { adjustDialog = true }, Modifier.weight(1f))
        }
        Text("انتقال بین حساب‌های خودت درآمد یا هزینه نیست.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HySectionTitle("گردش حساب انتخاب‌شده")
        state.statement.forEach { entry ->
            HyCard {
                SummaryRow(if (entry.delta >= 0) "دریافت" else "پرداخت", formatMoney(kotlin.math.abs(entry.delta), settings))
                Text("${formatDate(entry.createdAt, settings)} | ${entry.transactionCode} | مانده ${formatMoney(entry.balanceAfter, settings)}", style = MaterialTheme.typography.bodySmall)
                if (entry.description.isNotEmpty()) Text(entry.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (transferDialog) AlertDialog(onDismissRequest = { transferDialog = false }, title = { Text("انتقال بین حساب‌ها") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorInline("از", state.accounts, { it.title }, fromId) { fromId = it.id }
            SelectorInline("به", state.accounts, { it.title }, toId) { toId = it.id }
            MoneyField(amount, { amount = it }, "مبلغ")
        } },
        confirmButton = { TextButton(onClick = { viewModel.transfer(fromId ?: 0, toId ?: 0, parseMoneyInput(amount) ?: 0); transferDialog = false }) { Text("انتقال") } },
        dismissButton = { TextButton(onClick = { transferDialog = false }) { Text("انصراف") } })
    if (adjustDialog) AlertDialog(onDismissRequest = { adjustDialog = false }, title = { Text("اصلاح صندوق") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectorInline("حساب", state.accounts, { it.title }, state.selected) { viewModel.select(it.id) }
            MoneyField(amount, { amount = it }, "مبلغ اصلاح")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = increase, onClick = { increase = true }, label = { Text("افزایش") }); FilterChip(selected = !increase, onClick = { increase = false }, label = { Text("کاهش") }) }
            HyField(reason, { reason = it }, "دلیل *")
        } },
        confirmButton = { TextButton(onClick = { val raw = parseMoneyInput(amount) ?: 0; viewModel.adjust(state.selected ?: 0, if (increase) raw else -raw, reason); adjustDialog = false }) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = { adjustDialog = false }) { Text("انصراف") } })
}

@Composable private fun <T> SelectorInline(title: String, options: List<T>, label: (T) -> String, selectedId: Long?, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { (it as? Account)?.id == selectedId }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$title: ${selected?.let(label) ?: "انتخاب"}") }
    if (open) SelectorDialog(title, options, label, { open = false }) { onSelect(it); open = false }
}
