package ir.hesabyar.feature.transactions

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
import ir.hesabyar.domain.accounting.ReverseCommand
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.dashboard.transactionTypeLabel
import ir.hesabyar.feature.navigation.Routes
import ir.hesabyar.feature.sales.SummaryRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionDetailState(
    val tx: TransactionHeader? = null, val items: List<Pair<SaleItemSnapshot, String>> = emptyList(),
    val expense: ExpenseDetail? = null, val contactName: String = "", val accountName: String = "",
    val error: String? = null, val reversed: Boolean = false,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val accountingRepository: AccountingRepository, private val productRepository: ProductRepository,
    private val contactRepository: ContactRepository, private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TransactionDetailState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(id: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch {
            val tx = accountingRepository.getTransaction(id)
            val items = accountingRepository.getSaleItems(id).map { it to (productRepository.getById(it.productId)?.name ?: "کالا") }
            val expense = accountingRepository.getExpense(id)
            val contact = tx?.contactId?.let { contactRepository.getById(it)?.name } ?: ""
            val account = tx?.accountId?.let { accountRepository.getById(it)?.title } ?: ""
            _state.value = TransactionDetailState(tx, items, expense, contact, account)
        }
    }
    fun reverse(reason: String) {
        val tx = _state.value.tx ?: return
        viewModelScope.launch {
            accountingRepository.reverseTransaction(ReverseCommand(newOperationId(), System.currentTimeMillis(), tx.id, reason)).handle(
                { _state.value = _state.value.copy(reversed = true, error = null) },
                { m -> _state.value = _state.value.copy(error = m) })
        }
    }
}

@Composable
fun TransactionDetailScreen(id: Long, settings: AppSettings, navController: NavController, viewModel: TransactionDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.reversed) {
        if (state.reversed) {
            val route = when (state.tx?.type) {
                TransactionType.CASH_SALE, TransactionType.CREDIT_SALE, TransactionType.MIXED_SALE -> Routes.SALE
                TransactionType.EXPENSE -> Routes.EXPENSE
                TransactionType.PURCHASE -> Routes.PURCHASE
                else -> null
            }
            if (route != null) navController.navigate(route) { popUpTo(Routes.DASHBOARD) } else navController.popBackStack()
        }
    }
    var reverseDialog by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val tx = state.tx
    HyScreen(tx?.transactionCode ?: "تراکنش", navController) {
        HyErrorBanner(state.error) { }
        tx?.let { t ->
            HyCard {
                Text(transactionTypeLabel(t.type), style = MaterialTheme.typography.titleMedium)
                Text(formatDateTime(t.occurredAt, settings), style = MaterialTheme.typography.bodySmall)
                SummaryRow("مبلغ", formatMoney(t.amount, settings))
                SummaryRow("پرداخت‌شده", formatMoney(t.paidAmount, settings))
                SummaryRow("مانده", formatMoney(t.remainingAmount, settings))
                if (state.contactName.isNotEmpty()) SummaryRow("شخص", state.contactName)
                if (state.accountName.isNotEmpty()) SummaryRow("حساب", state.accountName)
                if (t.description.isNotEmpty()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                Text(if (t.status == TransactionStatus.POSTED) "ثبت‌شده" else "اصلاح‌شده", style = MaterialTheme.typography.bodySmall, color = if (t.status == TransactionStatus.POSTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            state.expense?.let { e -> HyCard { Text("عنوان: ${e.title}"); Text("دسته: ${e.category.label}"); if (e.invoicePath.isNotEmpty()) Text("تصویر فاکتور ذخیره شده است.", style = MaterialTheme.typography.bodySmall) } }
            if (state.items.isNotEmpty()) {
                HySectionTitle("اقلام فروش")
                state.items.forEach { (item, name) ->
                    HyCard {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text("تعداد ${formatNumber(item.quantity, settings)} | قیمت ${formatMoney(item.unitPrice, settings)} | تخفیف ${formatMoney(item.discountAmount, settings)}", style = MaterialTheme.typography.bodySmall)
                        Text("جمع ${formatMoney(item.totalAmount, settings)} | بهای تمام‌شده ${formatMoney(item.cogs, settings)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            HyOutlinedButton("اشتراک رسید", {
                val text = buildString {
                    appendLine("رسید ${t.transactionCode}")
                    appendLine(transactionTypeLabel(t.type))
                    appendLine(formatDateTime(t.occurredAt, settings))
                    appendLine("مبلغ: ${formatMoney(t.amount, settings)}")
                    appendLine("پرداخت‌شده: ${formatMoney(t.paidAmount, settings)}")
                    appendLine("مانده: ${formatMoney(t.remainingAmount, settings)}")
                    state.items.forEach { (item, name) -> appendLine("$name × ${item.quantity} = ${formatMoney(item.totalAmount, settings)}") }
                }
                shareText(context, text)
            })
            if (t.remainingAmount > 0 && t.contactId != null && t.status == TransactionStatus.POSTED) {
                val direction = if (t.type == TransactionType.PURCHASE || t.type == TransactionType.EXPENSE) "pay" else "receive"
                HyButton("تسویه مانده", { navController.navigate(Routes.payment(t.contactId!!, direction)) })
            }
            if (t.type in setOf(TransactionType.CASH_SALE, TransactionType.CREDIT_SALE, TransactionType.MIXED_SALE) && t.status == TransactionStatus.POSTED) {
                HyOutlinedButton("برگشت فروش", { navController.navigate(Routes.returnRoute(t.id)) })
            }
            if (t.status == TransactionStatus.POSTED && t.type != TransactionType.REVERSAL) {
                HyOutlinedButton("اصلاح (برگشت و ثبت مجدد)", { reverseDialog = true })
            }
        }
    }
    if (reverseDialog) AlertDialog(onDismissRequest = { reverseDialog = false }, title = { Text("اصلاح تراکنش") },
        text = { Column { Text("تراکنش تأییدشده حذف نمی‌شود؛ ابتدا اثر آن برگشت می‌خورد و سپس فرم ثبت مجدد باز می‌شود."); HyField(reason, { reason = it }, "دلیل اصلاح *") } },
        confirmButton = { TextButton(onClick = { viewModel.reverse(reason); reverseDialog = false }) { Text("برگشت و اصلاح") } },
        dismissButton = { TextButton(onClick = { reverseDialog = false }) { Text("انصراف") } })
}
