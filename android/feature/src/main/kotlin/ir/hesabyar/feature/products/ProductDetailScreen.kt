package ir.hesabyar.feature.products

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
import ir.hesabyar.domain.accounting.StockAdjustmentCommand
import ir.hesabyar.domain.model.Product
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ProductRepository
import ir.hesabyar.domain.repository.StockMovementEntry
import ir.hesabyar.domain.repository.AccountingRepository
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProductDetailState(val product: Product? = null, val movements: List<StockMovementEntry> = emptyList(), val error: String? = null)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val productRepository: ProductRepository, private val accountingRepository: AccountingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProductDetailState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(id: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch { _state.value = _state.value.copy(product = productRepository.getById(id)); productRepository.observeStockMovements(id).collect { m -> _state.value = _state.value.copy(movements = m, product = productRepository.getById(id)) } }
    }
    fun adjust(delta: Long, unitCost: Long?, reason: String) {
        val product = _state.value.product ?: return
        viewModelScope.launch { accountingRepository.adjustStock(StockAdjustmentCommand(newOperationId(), System.currentTimeMillis(), product.id, delta, unitCost, reason)).handle({}, { m -> _state.value = _state.value.copy(error = m) }) }
    }
}

@Composable
fun ProductDetailScreen(id: Long, settings: AppSettings, navController: NavController, viewModel: ProductDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    var adjustDialog by remember { mutableStateOf(false) }
    var deltaInput by remember { mutableStateOf("") }
    var costInput by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var increase by remember { mutableStateOf(true) }
    val product = state.product
    HyScreen(product?.name ?: "کالا", navController) {
        HyErrorBanner(state.error) { }
        product?.let { p ->
            HyCard {
                Text("موجودی: ${formatNumber(p.stockQuantity, settings)}", style = MaterialTheme.typography.titleMedium)
                Text("ارزش موجودی: ${formatMoney(p.inventoryValue, settings)}", style = MaterialTheme.typography.bodyMedium)
                Text("میانگین بهای تمام‌شده: ${formatMoney(p.averageCost, settings)}", style = MaterialTheme.typography.bodySmall)
                Text("قیمت خرید: ${formatMoney(p.purchasePrice, settings)} | قیمت فروش: ${formatMoney(p.sellingPrice, settings)}", style = MaterialTheme.typography.bodySmall)
                if (p.isLowStock) Text("هشدار: موجودی به حداقل رسیده است.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HyOutlinedButton("ویرایش", { navController.navigate(Routes.productForm(p.id)) }, Modifier.weight(1f))
                HyOutlinedButton("اصلاح موجودی", { adjustDialog = true }, Modifier.weight(1f))
            }
            HySectionTitle("تاریخچهٔ موجودی")
            state.movements.forEach { m ->
                HyCard {
                    Text("${m.movementType.name} | تعداد ${formatNumber(m.quantity, settings)}", style = MaterialTheme.typography.bodyMedium)
                    Text("بهای واحد ${formatMoney(m.unitCost, settings)} | ارزش ${formatMoney(m.valueDelta, settings)}", style = MaterialTheme.typography.bodySmall)
                    Text("${formatDate(m.createdAt, settings)} | ${m.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (adjustDialog) AlertDialog(onDismissRequest = { adjustDialog = false }, title = { Text("اصلاح موجودی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HyField(deltaInput, { deltaInput = it.filter(Char::isDigit) }, "تعداد")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = increase, onClick = { increase = true }, label = { Text("افزایش") })
                    FilterChip(selected = !increase, onClick = { increase = false }, label = { Text("کاهش") })
                }
                if (increase) MoneyField(costInput, { costInput = it }, "بهای واحد (اختیاری)")
                HyField(reason, { reason = it }, "دلیل *")
            }
        },
        confirmButton = { TextButton(onClick = { val q = deltaInput.toLongOrNull() ?: 0; viewModel.adjust(if (increase) q else -q, parseMoneyInput(costInput), reason); adjustDialog = false }) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = { adjustDialog = false }) { Text("انصراف") } })
}
