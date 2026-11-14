package ir.hesabyar.feature.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.accounting.ContactAdjustmentCommand
import ir.hesabyar.domain.model.Contact
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ContactLedgerEntry
import ir.hesabyar.domain.repository.ContactRepository
import ir.hesabyar.domain.repository.AccountingRepository
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.dashboard.transactionTypeLabel
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetailState(val contact: Contact? = null, val ledger: List<ContactLedgerEntry> = emptyList(), val error: String? = null)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository, private val accountingRepository: AccountingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ContactDetailState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(id: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch {
            val contact = contactRepository.getById(id)
            _state.value = _state.value.copy(contact = contact)
            contactRepository.observeLedger(id).collect { l -> _state.value = _state.value.copy(ledger = l) }
        }
        viewModelScope.launch {
            // refresh contact balance when ledger changes
            contactRepository.observeLedger(id).collect { _state.value = _state.value.copy(contact = contactRepository.getById(id)) }
        }
    }
    fun adjust(delta: Long, reason: String) {
        val contact = _state.value.contact ?: return
        viewModelScope.launch {
            accountingRepository.adjustContact(ContactAdjustmentCommand(newOperationId(), System.currentTimeMillis(), contact.id, delta, reason)).handle(
                { _state.value = _state.value.copy(error = null) },
                { m -> _state.value = _state.value.copy(error = m) })
        }
    }
}

@Composable
fun ContactDetailScreen(id: Long, settings: AppSettings, navController: NavController, viewModel: ContactDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(id) { viewModel.load(id) }
    var adjustDialog by remember { mutableStateOf(false) }
    var adjustAmount by remember { mutableStateOf("") }
    var adjustReason by remember { mutableStateOf("") }
    var adjustDebt by remember { mutableStateOf(true) }
    val contact = state.contact
    HyScreen(contact?.name ?: "شخص", navController) {
        HyErrorBanner(state.error) { }
        contact?.let { c ->
            HyCard {
                Text(balanceLabel(c, settings), style = MaterialTheme.typography.titleMedium)
                Text(c.contactType.label, style = MaterialTheme.typography.bodySmall)
                if (c.phone.isNotEmpty()) Text(c.phone, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HyButton("دریافت", { navController.navigate(Routes.payment(c.id, "receive")) }, Modifier.weight(1f), enabled = c.currentBalance > 0)
                HyButton("پرداخت", { navController.navigate(Routes.payment(c.id, "pay")) }, Modifier.weight(1f), enabled = c.currentBalance < 0)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HyOutlinedButton("ویرایش", { navController.navigate(Routes.contactForm(c.id)) }, Modifier.weight(1f))
                HyOutlinedButton("اصلاح مانده", { adjustDialog = true }, Modifier.weight(1f))
            }
            HyOutlinedButton("ارسال صورت‌حساب", {
                val text = buildString {
                    appendLine("صورت‌حساب ${c.name}")
                    appendLine(balanceLabel(c, settings))
                    state.ledger.forEach { appendLine("${formatDate(it.createdAt, settings)} | ${transactionTypeLabel(it.transactionType)} | بدهکار ${formatMoney(it.debit, settings)} | بستانکار ${formatMoney(it.credit, settings)} | مانده ${formatMoney(it.balanceAfter, settings)}") }
                }
                shareText(context, text)
            })
            HySectionTitle("دفتر حساب")
            state.ledger.forEach { entry ->
                HyCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(transactionTypeLabel(entry.transactionType), style = MaterialTheme.typography.bodyMedium)
                        Text(entry.transactionCode, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatDate(entry.createdAt, settings), style = MaterialTheme.typography.bodySmall)
                    Text("بدهکار: ${formatMoney(entry.debit, settings)} | بستانکار: ${formatMoney(entry.credit, settings)} | مانده: ${formatMoney(entry.balanceAfter, settings)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (adjustDialog) AlertDialog(onDismissRequest = { adjustDialog = false }, title = { Text("اصلاح مانده اولیه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(adjustAmount, { adjustAmount = it }, "مبلغ")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = adjustDebt, onClick = { adjustDebt = true }, label = { Text("افزایش بدهی مشتری") })
                    FilterChip(selected = !adjustDebt, onClick = { adjustDebt = false }, label = { Text("افزایش بدهی ما") })
                }
                HyField(adjustReason, { adjustReason = it }, "دلیل *")
            }
        },
        confirmButton = { TextButton(onClick = { val raw = parseMoneyInput(adjustAmount) ?: 0; viewModel.adjust(if (adjustDebt) raw else -raw, adjustReason); adjustDialog = false }) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = { adjustDialog = false }) { Text("انصراف") } })
}
