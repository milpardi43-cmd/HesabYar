package ir.hesabyar.feature.sales

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import ir.hesabyar.domain.accounting.*
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PurchaseState(
    val products: List<Product> = emptyList(), val suppliers: List<Contact> = emptyList(), val accounts: List<Account> = emptyList(),
    val cart: List<CartLine> = emptyList(), val supplierId: Long? = null, val accountId: Long? = null,
    val paidInput: String = "", val directCostInput: String = "", val method: PaymentMethod = PaymentMethod.CASH,
    val description: String = "", val dueDate: Long? = null, val operationId: String = newOperationId(),
    val preview: PurchaseResult? = null, val loading: Boolean = false, val error: String? = null, val successId: Long? = null,
)

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val productRepository: ProductRepository, private val contactRepository: ContactRepository,
    private val accountRepository: AccountRepository, private val accountingRepository: AccountingRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PurchaseState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long) {
        if (loaded) return; loaded = true
        viewModelScope.launch { productRepository.observeProducts(businessId).collect { p -> _state.value = _state.value.copy(products = p) } }
        viewModelScope.launch { contactRepository.observeContacts(businessId).collect { c -> _state.value = _state.value.copy(suppliers = c.filter { it.contactType != ContactType.CUSTOMER }) } }
        viewModelScope.launch { accountRepository.observeAccounts(businessId).collect { a -> _state.value = _state.value.copy(accounts = a, accountId = _state.value.accountId ?: a.firstOrNull()?.id) } }
    }
    fun addProduct(p: Product) { val c = _state.value.cart.toMutableList(); val i = c.indexOfFirst { it.productId == p.id }; if (i >= 0) c[i] = c[i].copy(quantity = c[i].quantity + 1) else c.add(CartLine(p.id, p.name, p.stockQuantity, 1, p.purchasePrice, 0)); _state.value = _state.value.copy(cart = c); preview() }
    fun updateLine(i: Int, q: Long? = null, p: Long? = null, d: Long? = null) { val c = _state.value.cart.toMutableList(); if (i !in c.indices) return; c[i] = c[i].copy(quantity = q ?: c[i].quantity, unitPrice = p ?: c[i].unitPrice, discount = d ?: c[i].discount); _state.value = _state.value.copy(cart = c); preview() }
    fun removeLine(i: Int) { val c = _state.value.cart.toMutableList(); if (i in c.indices) c.removeAt(i); _state.value = _state.value.copy(cart = c); preview() }
    fun setSupplier(id: Long) { _state.value = _state.value.copy(supplierId = id) }
    fun setAccount(id: Long) { _state.value = _state.value.copy(accountId = id) }
    fun setPaid(v: String) { _state.value = _state.value.copy(paidInput = v); preview() }
    fun setDirectCost(v: String) { _state.value = _state.value.copy(directCostInput = v); preview() }
    fun setMethod(m: PaymentMethod) { _state.value = _state.value.copy(method = m) }
    fun setDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun setDueDate(v: Long?) { _state.value = _state.value.copy(dueDate = v) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun addSupplier(name: String) { viewModelScope.launch { contactRepository.add(ContactCommand(name = name, contactType = ContactType.SUPPLIER)).handle({ _state.value = _state.value.copy(supplierId = it.id) }, { m -> _state.value = _state.value.copy(error = m) }) } }
    private fun preview() {
        val s = _state.value
        val r = runCatching { AccountingEngine.computePurchase(PurchaseCommand(s.operationId, 1, s.supplierId ?: 1, s.cart.map { PurchaseLineCommand(it.productId, it.quantity, it.unitPrice, it.discount) }, parseMoneyInput(s.paidInput) ?: 0, s.accountId ?: 1, s.method, parseMoneyInput(s.directCostInput) ?: 0)) }.getOrNull()
        _state.value = s.copy(preview = r)
    }
    fun submit() {
        val s = _state.value; _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            accountingRepository.recordPurchase(PurchaseCommand(s.operationId, System.currentTimeMillis(), s.supplierId ?: 0, s.cart.map { PurchaseLineCommand(it.productId, it.quantity, it.unitPrice, it.discount) }, parseMoneyInput(s.paidInput) ?: 0, s.accountId, s.method, parseMoneyInput(s.directCostInput) ?: 0, s.description, s.dueDate)).handle(
                { tx -> _state.value = _state.value.copy(loading = false, successId = tx.id) },
                { m -> _state.value = _state.value.copy(loading = false, error = m) })
        }
    }
}

@Composable
fun PurchaseScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: PurchaseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    LaunchedEffect(state.successId) { state.successId?.let { navController.navigate(Routes.transaction(it)) { popUpTo(Routes.DASHBOARD) } } }
    var productPicker by remember { mutableStateOf(false) }
    var supplierPicker by remember { mutableStateOf(false) }
    var newSupplier by remember { mutableStateOf(false) }
    var supplierName by remember { mutableStateOf("") }
    HyScreen("ثبت خرید کالا", navController) {
        HyErrorBanner(state.error) { viewModel.clearError() }
        OutlinedButton(onClick = { supplierPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("تأمین‌کننده: ${state.suppliers.firstOrNull { it.id == state.supplierId }?.name ?: "انتخاب کنید"}") }
        HyButton("افزودن کالا", { productPicker = true })
        state.cart.forEachIndexed { i, line ->
            HyCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(line.name, modifier = Modifier.weight(1f)); IconButton(onClick = { viewModel.removeLine(i) }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HyField(line.quantity.toString(), { v -> v.toLongOrNull()?.let { viewModel.updateLine(i, q = it) } }, "تعداد", Modifier.weight(1f))
                    MoneyField(line.unitPrice.toString(), { v -> parseMoneyInput(v)?.let { viewModel.updateLine(i, p = it) } }, "قیمت خرید", Modifier.weight(1f))
                }
                MoneyField(line.discount.toString(), { v -> parseMoneyInput(v)?.let { viewModel.updateLine(i, d = it) } }, "تخفیف")
            }
        }
        state.preview?.let { p -> HyCard { SummaryRow("جمع کالا", formatMoney(p.goodsNet, settings)); SummaryRow("هزینه مستقیم خرید", formatMoney(p.directCost, settings)); SummaryRow("کل خرید", formatMoney(p.totalAmount, settings)); SummaryRow("مانده بدهی", formatMoney(p.remainingAmount, settings)) } }
        MoneyField(state.paidInput, viewModel::setPaid, "مبلغ پرداختی")
        MoneyField(state.directCostInput, viewModel::setDirectCost, "هزینه مستقیم خرید مثل حمل (به بهای کالا اضافه می‌شود)")
        OutlinedButton(onClick = { viewModel.setMethod(if (state.method == PaymentMethod.CASH) PaymentMethod.BANK_TRANSFER else PaymentMethod.CASH) }, modifier = Modifier.fillMaxWidth()) { Text("روش پرداخت: ${state.method.label}") }
        SearchPickerInline("حساب پرداخت", state.accounts, { it.title }, state.accountId) { viewModel.setAccount(it.id) }
        JalaliDateField(state.dueDate ?: System.currentTimeMillis(), "سررسید بدهی (اختیاری)", { viewModel.setDueDate(it) }, settings)
        HyField(state.description, viewModel::setDescription, "توضیح", singleLine = false)
        HyButton(if (state.loading) "در حال ثبت…" else "ثبت خرید", { viewModel.submit() }, enabled = !state.loading && state.cart.isNotEmpty())
    }
    if (productPicker) SearchPickerDialog("انتخاب کالا", state.products, { "${it.name} | ${formatMoney(it.purchasePrice, settings)}" }, onDismiss = { productPicker = false }, onSelect = { viewModel.addProduct(it); productPicker = false }, footer = { TextButton(onClick = { productPicker = false; navController.navigate(Routes.productForm()) }) { Text("کالای جدید") } })
    if (supplierPicker) SearchPickerDialog("تأمین‌کننده", state.suppliers, { it.name }, onDismiss = { supplierPicker = false }, onSelect = { viewModel.setSupplier(it.id); supplierPicker = false }, footer = { TextButton(onClick = { supplierPicker = false; newSupplier = true }) { Text("تأمین‌کننده جدید") } })
    if (newSupplier) AlertDialog(onDismissRequest = { newSupplier = false }, title = { Text("تأمین‌کننده جدید") }, text = { HyField(supplierName, { supplierName = it }, "نام") }, confirmButton = { TextButton(onClick = { viewModel.addSupplier(supplierName); supplierName = ""; newSupplier = false }) { Text("ذخیره") } }, dismissButton = { TextButton(onClick = { newSupplier = false }) { Text("انصراف") } })
}

@Composable
fun <T> SearchPickerInline(title: String, options: List<T>, label: (T) -> String, selectedId: Long?, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { (it as? Account)?.id == selectedId }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$title: ${selected?.let(label) ?: "انتخاب"}") }
    if (open) SelectorDialog(title, options, label, { open = false }) { onSelect(it); open = false }
}
