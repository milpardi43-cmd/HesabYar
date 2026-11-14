package ir.hesabyar.feature.sales

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.PaymentMethod
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes

@Composable
fun SaleScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: SaleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    LaunchedEffect(state.successId) { state.successId?.let { navController.navigate(Routes.transaction(it)) { popUpTo(Routes.DASHBOARD) } } }
    var productPicker by remember { mutableStateOf(false) }
    var customerPicker by remember { mutableStateOf(false) }
    var newCustomer by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var methodPicker by remember { mutableStateOf(false) }
    var accountPicker by remember { mutableStateOf(false) }

    HyScreen("ثبت فروش", navController) {
        HyErrorBanner(state.error) { viewModel.clearError() }
        HySectionTitle("مشتری")
        val customer = state.contacts.firstOrNull { it.id == state.contactId }
        OutlinedButton(onClick = { customerPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(customer?.name ?: "مشتری نقدی / ناشناس")
        }
        HySectionTitle("کالاها")
        HyButton("افزودن کالا", { productPicker = true })
        state.cart.forEachIndexed { index, line ->
            HyCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(line.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeLine(index) }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                }
                Text("موجودی: ${formatNumber(line.stock, settings)}", style = MaterialTheme.typography.bodySmall, color = if (line.quantity > line.stock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HyField(line.quantity.toString(), { v -> v.toLongOrNull()?.let { viewModel.updateLine(index, quantity = it) } }, "تعداد", Modifier.weight(1f))
                    MoneyField(line.unitPrice.toString(), { v -> parseMoneyInput(v)?.let { viewModel.updateLine(index, unitPrice = it) } }, "قیمت واحد", Modifier.weight(1f))
                }
                MoneyField(line.discount.toString(), { v -> parseMoneyInput(v)?.let { viewModel.updateLine(index, discount = it) } }, "تخفیف")
            }
        }
        HySectionTitle("پرداخت")
        state.preview?.let { preview ->
            HyCard {
                SummaryRow("جمع کل", formatMoney(preview.grossAmount, settings))
                SummaryRow("تخفیف", formatMoney(preview.discountAmount, settings))
                SummaryRow("قابل پرداخت", formatMoney(preview.netAmount, settings))
                SummaryRow("مانده نسیه", formatMoney(preview.remainingAmount, settings))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.payFull() }, modifier = Modifier.weight(1f)) { Text("نقدی") }
            OutlinedButton(onClick = { viewModel.payNone() }, modifier = Modifier.weight(1f)) { Text("نسیه") }
        }
        MoneyField(state.paidInput, viewModel::setPaid, "مبلغ دریافتی")
        OutlinedButton(onClick = { methodPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("روش پرداخت: ${state.method.label}") }
        OutlinedButton(onClick = { accountPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("حساب دریافت: ${state.accounts.firstOrNull { it.id == state.accountId }?.title ?: "انتخاب حساب"}") }
        JalaliDateField(state.dueDate ?: System.currentTimeMillis(), "سررسید بدهی (اختیاری)", { viewModel.setDueDate(it) }, settings)
        HyField(state.description, viewModel::setDescription, "توضیح", singleLine = false)
        HyButton(if (state.loading) "در حال ثبت…" else "ثبت فروش", { viewModel.submit() }, enabled = !state.loading && state.cart.isNotEmpty())
    }

    if (productPicker) {
        SearchPickerDialog("انتخاب کالا", state.products,
            { "${it.name} | موجودی ${formatNumber(it.stockQuantity, settings)} | ${formatMoney(it.sellingPrice, settings)}" },
            onDismiss = { productPicker = false },
            onSelect = { viewModel.addProduct(it); productPicker = false },
            footer = { TextButton(onClick = { productPicker = false; navController.navigate(Routes.productForm()) }) { Text("افزودن کالای جدید") } })
    }
    if (customerPicker) {
        val options: List<ir.hesabyar.domain.model.Contact?> = listOf(null) + state.contacts.filter { it.contactType != ir.hesabyar.domain.model.ContactType.SUPPLIER }
        SearchPickerDialog("انتخاب مشتری", options, { it?.name ?: "مشتری نقدی / ناشناس" },
            onDismiss = { customerPicker = false },
            onSelect = { viewModel.setContact(it?.id); customerPicker = false },
            footer = { TextButton(onClick = { customerPicker = false; newCustomer = true }) { Text("افزودن مشتری جدید") } })
    }
    if (newCustomer) {
        AlertDialog(onDismissRequest = { newCustomer = false }, title = { Text("مشتری جدید") },
            text = { HyField(customerName, { customerName = it }, "نام مشتری") },
            confirmButton = { TextButton(onClick = { viewModel.addCustomer(customerName); customerName = ""; newCustomer = false }) { Text("ذخیره") } },
            dismissButton = { TextButton(onClick = { newCustomer = false }) { Text("انصراف") } })
    }
    if (methodPicker) SelectorDialog("روش پرداخت", PaymentMethod.entries, { it.label }, { methodPicker = false }) { viewModel.setMethod(it); methodPicker = false }
    if (accountPicker) SelectorDialog("حساب دریافت", state.accounts, { it.title }, { accountPicker = false }) { viewModel.setAccount(it.id); accountPicker = false }
}

@Composable
fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
