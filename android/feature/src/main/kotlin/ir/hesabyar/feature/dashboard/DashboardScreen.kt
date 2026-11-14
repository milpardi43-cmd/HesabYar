package ir.hesabyar.feature.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.Business
import ir.hesabyar.domain.model.TransactionType
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes

@Composable
fun DashboardScreen(business: Business, settings: AppSettings, navController: NavController, viewModel: DashboardViewModel = hiltViewModel()) {
    val flow = remember(business.id) { viewModel.state(business.id) }
    val state by flow.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(business.name, style = MaterialTheme.typography.titleLarge)
                Text(formatDate(System.currentTimeMillis(), settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { navController.navigate(Routes.REMINDERS) }) { Icon(Icons.Filled.Notifications, contentDescription = "یادآورها") }
            IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "تنظیمات") }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                HyCard(Modifier.clickable { navController.navigate(Routes.ACCOUNTS) }) {
                    Text("موجودی صندوق و حساب‌ها", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMoney(state.cashBalance, settings), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("دریافت امروز: ${formatMoney(state.cashToday.receipts, settings)}", style = MaterialTheme.typography.bodySmall)
                        Text("پرداخت امروز: ${formatMoney(state.cashToday.payments, settings)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DailyCard("فروش امروز", state.today.netSales, settings, Modifier.weight(1f)) { navController.navigate(Routes.TRANSACTIONS) }
                    DailyCard("دریافتی امروز", state.today.cashReceived, settings, Modifier.weight(1f)) { navController.navigate(Routes.TRANSACTIONS) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DailyCard("هزینه امروز", state.today.operatingExpenses, settings, Modifier.weight(1f)) { navController.navigate(Routes.TRANSACTIONS) }
                    DailyCard("سود امروز", state.today.netProfit, settings, Modifier.weight(1f)) { navController.navigate(Routes.REPORTS) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HyCard(Modifier.weight(1f).clickable { navController.navigate(Routes.CONTACTS) }) {
                        Text("طلب از مشتریان", style = MaterialTheme.typography.bodySmall)
                        Text(formatMoney(state.receivables, settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    HyCard(Modifier.weight(1f).clickable { navController.navigate(Routes.CONTACTS) }) {
                        Text("بدهی به دیگران", style = MaterialTheme.typography.bodySmall)
                        Text(formatMoney(state.payables, settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state.lowStock.isNotEmpty()) item {
                HyCard(Modifier.clickable { navController.navigate(Routes.PRODUCTS) }) {
                    Text("هشدار موجودی کم: ${formatNumber(state.lowStock.size.toLong(), settings)} کالا", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { HySectionTitle("دسترسی سریع") }
            item {
                val actions = listOf(
                    Triple("ثبت فروش", Icons.Filled.PointOfSale) { navController.navigate(Routes.SALE) },
                    Triple("ثبت هزینه", Icons.Filled.MoneyOff) { navController.navigate(Routes.EXPENSE) },
                    Triple("دریافت وجه", Icons.Filled.CallReceived) { navController.navigate(Routes.CONTACTS) },
                    Triple("پرداخت وجه", Icons.Filled.CallMade) { navController.navigate(Routes.CONTACTS) },
                    Triple("افزودن مشتری", Icons.Filled.PersonAdd) { navController.navigate(Routes.contactForm()) },
                    Triple("افزودن کالا", Icons.Filled.AddBox) { navController.navigate(Routes.productForm()) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (label, icon, action) ->
                                OutlinedCard(onClick = action, modifier = Modifier.weight(1f)) {
                                    Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                                        Text(label, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { HySectionTitle("آخرین تراکنش‌ها") }
            if (state.recent.isEmpty()) {
                item { HyEmptyState("هنوز فعالیتی ثبت نکرده‌ای", "اولین فروش یا هزینه را ثبت کن تا گزارش‌ها شکل بگیرند.", "ثبت اولین تراکنش") { navController.navigate(Routes.SALE) } }
            } else {
                items(state.recent, key = { it.id }) { tx ->
                    HyCard(Modifier.clickable { navController.navigate(Routes.transaction(tx.id)) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(transactionTypeLabel(tx.type), style = MaterialTheme.typography.bodyMedium)
                            Text(formatMoney(tx.amount, settings), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(formatDateTime(tx.occurredAt, settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyCard(title: String, amount: Long, settings: AppSettings, modifier: Modifier = Modifier, onClick: () -> Unit) {
    HyCard(modifier.clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMoney(amount, settings), style = MaterialTheme.typography.titleMedium)
    }
}

fun transactionTypeLabel(type: TransactionType): String = when (type) {
    TransactionType.OPENING -> "مانده اولیه"
    TransactionType.CASH_SALE -> "فروش نقدی"
    TransactionType.CREDIT_SALE -> "فروش نسیه"
    TransactionType.MIXED_SALE -> "فروش ترکیبی"
    TransactionType.RECEIVE_RECEIVABLE -> "دریافت طلب"
    TransactionType.EXPENSE -> "هزینه"
    TransactionType.PURCHASE -> "خرید کالا"
    TransactionType.PAY_PAYABLE -> "پرداخت بدهی"
    TransactionType.SALES_RETURN -> "برگشت فروش"
    TransactionType.ACCOUNT_ADJUSTMENT -> "اصلاح صندوق"
    TransactionType.STOCK_ADJUSTMENT -> "اصلاح موجودی"
    TransactionType.TRANSFER -> "انتقال بین حساب‌ها"
    TransactionType.CONTACT_ADJUSTMENT -> "اصلاح مانده شخص"
    TransactionType.REVERSAL -> "اصلاح تراکنش"
}
