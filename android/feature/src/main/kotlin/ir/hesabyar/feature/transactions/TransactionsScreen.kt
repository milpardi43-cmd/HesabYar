package ir.hesabyar.feature.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import ir.hesabyar.domain.model.Contact
import ir.hesabyar.domain.model.TransactionHeader
import ir.hesabyar.domain.model.TransactionStatus
import ir.hesabyar.domain.model.TransactionType
import ir.hesabyar.domain.repository.AccountingRepository
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ContactRepository
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.dashboard.transactionTypeLabel
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsState(
    val transactions: List<TransactionHeader> = emptyList(), val contacts: List<Contact> = emptyList(),
    val query: String = "", val type: TransactionType? = null, val ascending: Boolean = false,
    val from: Long? = null, val to: Long? = null, val contactId: Long? = null,
    val status: TransactionStatus? = null, val unpaidOnly: Boolean = false,
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: AccountingRepository, private val contactRepository: ContactRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TransactionsState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch { repository.observeTransactions(businessId).collect { t -> _state.value = _state.value.copy(transactions = t) } }
        viewModelScope.launch { contactRepository.observeContacts(businessId).collect { c -> _state.value = _state.value.copy(contacts = c) } }
    }
    fun update(block: (TransactionsState) -> TransactionsState) { _state.value = block(_state.value) }
}

@Composable
fun TransactionsScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    var typePicker by remember { mutableStateOf(false) }
    var contactPicker by remember { mutableStateOf(false) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val filtered = state.transactions
        .filter { it.transactionCode.contains(state.query, true) || it.description.contains(state.query, true) || transactionTypeLabel(it.type).contains(state.query) }
        .filter { state.type == null || it.type == state.type }
        .filter { state.from == null || it.occurredAt >= state.from!! }
        .filter { state.to == null || it.occurredAt <= state.to!! }
        .filter { state.contactId == null || it.contactId == state.contactId }
        .filter { state.status == null || it.status == state.status }
        .filter { !state.unpaidOnly || it.remainingAmount > 0 }
        .let { if (state.ascending) it.sortedBy { t -> t.occurredAt } else it.sortedByDescending { t -> t.occurredAt } }
    Column(Modifier.fillMaxSize()) {
        Text("تراکنش‌ها", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HyField(state.query, { v -> viewModel.update { it.copy(query = v) } }, "جستجو")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { typePicker = true }, modifier = Modifier.weight(1f)) { Text(state.type?.let { transactionTypeLabel(it) } ?: "همه") }
                OutlinedButton(onClick = { viewModel.update { it.copy(ascending = !it.ascending) } }, modifier = Modifier.weight(1f)) { Text(if (state.ascending) "قدیمی‌تر اول" else "جدیدتر اول") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFrom = true }, modifier = Modifier.weight(1f)) { Text(state.from?.let { "از ${formatDate(it, settings)}" } ?: "از تاریخ") }
                OutlinedButton(onClick = { pickTo = true }, modifier = Modifier.weight(1f)) { Text(state.to?.let { "تا ${formatDate(it, settings)}" } ?: "تا تاریخ") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { contactPicker = true }, modifier = Modifier.weight(1f)) { Text(state.contacts.firstOrNull { it.id == state.contactId }?.name ?: "شخص") }
                FilterChip(selected = state.unpaidOnly, onClick = { viewModel.update { it.copy(unpaidOnly = !it.unpaidOnly) } }, label = { Text("مانده‌دار") })
                FilterChip(selected = state.status == TransactionStatus.REVERSED, onClick = { viewModel.update { it.copy(status = if (it.status == TransactionStatus.REVERSED) null else TransactionStatus.REVERSED) } }, label = { Text("اصلاح‌شده") })
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item { HyEmptyState("تراکنشی نیست", "با ثبت فروش، هزینه یا خرید، تاریخچه اینجا نمایش داده می‌شود.", "ثبت فروش") { navController.navigate(Routes.SALE) } }
            items(filtered, key = { it.id }) { tx ->
                HyCard(Modifier.clickable { navController.navigate(Routes.transaction(tx.id)) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(transactionTypeLabel(tx.type), style = MaterialTheme.typography.titleSmall)
                        Text(formatMoney(tx.amount, settings), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("${tx.transactionCode} | ${formatDateTime(tx.occurredAt, settings)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (tx.status == TransactionStatus.REVERSED) Text("اصلاح‌شده", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    if (tx.remainingAmount > 0) Text("مانده: ${formatMoney(tx.remainingAmount, settings)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (typePicker) SelectorDialog("نوع تراکنش", listOf<TransactionType?>(null) + TransactionType.entries, { it?.let { t -> transactionTypeLabel(t) } ?: "همه" }, { typePicker = false }) { t -> viewModel.update { it.copy(type = t) }; typePicker = false }
    if (contactPicker) SearchPickerDialog("شخص", listOf<Contact?>(null) + state.contacts, { it?.name ?: "همه" }, onDismiss = { contactPicker = false }, onSelect = { c -> viewModel.update { it.copy(contactId = c?.id) }; contactPicker = false })
    if (pickFrom) JalaliDatePickerDialog(state.from ?: System.currentTimeMillis(), { pickFrom = false }) { d -> viewModel.update { it.copy(from = d) }; pickFrom = false }
    if (pickTo) JalaliDatePickerDialog(state.to ?: System.currentTimeMillis(), { pickTo = false }) { d -> viewModel.update { it.copy(to = d.toLocalDate().endMillis()) }; pickTo = false }
}
