package ir.hesabyar.feature.sales

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import ir.hesabyar.domain.accounting.ExpenseCommand
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ExpenseState(
    val title: String = "", val category: ExpenseCategory = ExpenseCategory.OTHER,
    val amountInput: String = "", val paidInput: String = "", val contacts: List<Contact> = emptyList(),
    val accounts: List<Account> = emptyList(), val contactId: Long? = null, val accountId: Long? = null,
    val method: PaymentMethod = PaymentMethod.CASH, val description: String = "", val invoicePath: String = "",
    val occurredAt: Long = System.currentTimeMillis(), val dueDate: Long? = null,
    val operationId: String = newOperationId(), val loading: Boolean = false, val error: String? = null, val successId: Long? = null,
)

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val contactRepository: ContactRepository, private val accountRepository: AccountRepository,
    private val accountingRepository: AccountingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ExpenseState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch { contactRepository.observeContacts(businessId).collect { c -> _state.value = _state.value.copy(contacts = c) } }
        viewModelScope.launch { accountRepository.observeAccounts(businessId).collect { a -> _state.value = _state.value.copy(accounts = a, accountId = _state.value.accountId ?: a.firstOrNull { it.accountType == AccountType.CASH }?.id) } }
    }
    fun update(block: (ExpenseState) -> ExpenseState) { _state.value = block(_state.value) }
    fun clearError() { update { it.copy(error = null) } }
    fun payFull() { update { it.copy(paidInput = it.amountInput) } }
    fun submit() {
        val s = _state.value; update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            accountingRepository.recordExpense(ExpenseCommand(s.operationId, s.occurredAt, s.title, s.category, parseMoneyInput(s.amountInput) ?: 0, parseMoneyInput(s.paidInput) ?: 0, s.accountId, s.contactId, s.method, s.description, s.invoicePath, s.dueDate)).handle(
                { tx -> update { it.copy(loading = false, successId = tx.id) } },
                { m -> update { it.copy(loading = false, error = m) } })
        }
    }
}

@Composable
fun ExpenseScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: ExpenseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    LaunchedEffect(state.successId) { state.successId?.let { navController.navigate(Routes.transaction(it)) { popUpTo(Routes.DASHBOARD) } } }
    var categoryPicker by remember { mutableStateOf(false) }
    var contactPicker by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val dir = File(context.filesDir, "invoices").apply { mkdirs() }
                val file = File(dir, "invoice-${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                viewModel.update { it.copy(invoicePath = file.absolutePath) }
            }.onFailure { viewModel.update { it.copy(error = "ذخیره تصویر فاکتور ممکن نشد.") } }
        }
    }
    HyScreen("ثبت هزینه", navController) {
        HyErrorBanner(state.error) { viewModel.clearError() }
        HyField(state.title, { v -> viewModel.update { it.copy(title = v) } }, "عنوان هزینه *")
        OutlinedButton(onClick = { categoryPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("دسته: ${state.category.label}") }
        MoneyField(state.amountInput, { v -> viewModel.update { it.copy(amountInput = v) } }, "مبلغ هزینه *")
        MoneyField(state.paidInput, { v -> viewModel.update { it.copy(paidInput = v) } }, "مبلغ پرداختی")
        HyOutlinedButton("پرداخت کامل", { viewModel.payFull() })
        Text("هزینهٔ پرداخت‌نشده به‌عنوان بدهی ثبت می‌شود و از صندوق کم نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { contactPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("شخص بستانکار: ${state.contacts.firstOrNull { it.id == state.contactId }?.name ?: "اختیاری / برای هزینهٔ نسیه لازم"}") }
        SearchPickerInline("حساب پرداخت", state.accounts, { it.title }, state.accountId) { id -> viewModel.update { it.copy(accountId = id.id) } }
        OutlinedButton(onClick = { viewModel.update { it.copy(method = if (it.method == PaymentMethod.CASH) PaymentMethod.BANK_TRANSFER else PaymentMethod.CASH) } }, modifier = Modifier.fillMaxWidth()) { Text("روش پرداخت: ${state.method.label}") }
        JalaliDateField(state.occurredAt, "تاریخ هزینه", { d -> viewModel.update { it.copy(occurredAt = d) } }, settings)
        JalaliDateField(state.dueDate ?: System.currentTimeMillis(), "سررسید (اختیاری)", { d -> viewModel.update { it.copy(dueDate = d) } }, settings)
        HyField(state.description, { v -> viewModel.update { it.copy(description = v) } }, "توضیح", singleLine = false)
        HyOutlinedButton(if (state.invoicePath.isEmpty()) "افزودن تصویر فاکتور" else "تغییر تصویر فاکتور", { picker.launch("image/*") })
        if (state.invoicePath.isNotEmpty()) Text("تصویر فاکتور ذخیره شد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        HyButton(if (state.loading) "در حال ثبت…" else "ثبت هزینه", { viewModel.submit() }, enabled = !state.loading)
    }
    if (categoryPicker) SelectorDialog("دسته هزینه", ExpenseCategory.entries, { it.label }, { categoryPicker = false }) { c -> viewModel.update { it.copy(category = c) }; categoryPicker = false }
    if (contactPicker) SearchPickerDialog("شخص بستانکار", state.contacts, { it.name }, onDismiss = { contactPicker = false }, onSelect = { c -> viewModel.update { it.copy(contactId = c.id) }; contactPicker = false })
}
