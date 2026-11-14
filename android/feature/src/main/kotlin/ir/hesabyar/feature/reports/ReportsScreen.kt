package ir.hesabyar.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.sales.SummaryRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ReportsState(
    val from: Long = todayStart(), val to: Long = todayEnd(), val tab: Int = 0,
    val summary: ReportSummary = ReportSummary(), val cash: CashReport = CashReport(0, 0, 0, 0),
    val products: List<ProductReportRow> = emptyList(), val contacts: List<Contact> = emptyList(),
    val plan: PlanType = PlanType.FREE, val trend: List<Pair<String, Long>> = emptyList(),
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository, private val contactRepository: ContactRepository,
    private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state = _state.asStateFlow()
    private var businessId = 0L
    fun load(businessId: Long) {
        if (this.businessId == businessId) return
        this.businessId = businessId
        viewModelScope.launch { contactRepository.observeContacts(businessId).collect { c -> _state.value = _state.value.copy(contacts = c) } }
        viewModelScope.launch { subscriptionRepository.observeSubscription(businessId).collect { s -> _state.value = _state.value.copy(plan = runCatching { PlanType.valueOf(s?.planType ?: "FREE") }.getOrDefault(PlanType.FREE)) } }
        refresh()
    }
    fun update(block: (ReportsState) -> ReportsState) { _state.value = block(_state.value); refresh() }
    fun refresh() {
        val s = _state.value
        viewModelScope.launch {
            val summary = reportRepository.dailyReport(businessId, s.from, s.to)
            val cash = reportRepository.cashReport(businessId, s.from, s.to)
            val products = reportRepository.productReport(businessId, s.from, s.to)
            val trend = (0..6).map { offset ->
                val day = LocalDate.now().minusDays((6 - offset).toLong())
                val r = reportRepository.dailyReport(businessId, day.startMillis(), day.endMillis())
                formatDate(day.startMillis(), AppSettings()) to r.netSales
            }
            _state.value = s.copy(summary = summary, cash = cash, products = products, trend = trend)
        }
    }
}

@Composable
fun ReportsScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val tabs = listOf("روزانه", "ماهانه", "مشتریان", "تأمین‌کنندگان", "کالاها", "صندوق")
    Column(Modifier.fillMaxSize()) {
        Text("گزارش‌ها", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        ScrollableTabRow(selectedTabIndex = state.tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, title -> Tab(selected = state.tab == i, onClick = { viewModel.update { it.copy(tab = i) } }, text = { Text(title) }) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickFrom = true }, modifier = Modifier.weight(1f)) { Text("از ${formatDate(state.from, settings)}") }
                OutlinedButton(onClick = { pickTo = true }, modifier = Modifier.weight(1f)) { Text("تا ${formatDate(state.to, settings)}") }
            }
            when (state.tab) {
                0 -> SummaryContent(state.summary, settings)
                1 -> if (BillingPolicy.hasFeature(state.plan, Feature.MONTHLY_REPORT)) {
                    SummaryContent(state.summary, settings)
                    TrendChart(state.trend, settings)
                } else UpgradeCard(navController)
                2 -> ContactReport(state.contacts.filter { it.currentBalance > 0 || it.contactType != ContactType.SUPPLIER }, settings, true)
                3 -> ContactReport(state.contacts.filter { it.currentBalance < 0 || it.contactType != ContactType.CUSTOMER }, settings, false)
                4 -> ProductReport(state.products, settings)
                5 -> CashContent(state.cash, settings)
            }
            HyOutlinedButton("اشتراک گزارش", {
                shareText(context, buildString {
                    appendLine("گزارش حساب‌یار ${formatDate(state.from, settings)} تا ${formatDate(state.to, settings)}")
                    appendLine("فروش خالص: ${formatMoney(state.summary.netSales, settings)}")
                    appendLine("بهای تمام‌شده: ${formatMoney(state.summary.cogs, settings)}")
                    appendLine("سود ناخالص: ${formatMoney(state.summary.grossProfit, settings)}")
                    appendLine("هزینه عملیاتی: ${formatMoney(state.summary.operatingExpenses, settings)}")
                    appendLine("سود خالص: ${formatMoney(state.summary.netProfit, settings)}")
                })
            })
        }
    }
    if (pickFrom) JalaliDatePickerDialog(state.from, { pickFrom = false }) { viewModel.update { s -> s.copy(from = it) }; pickFrom = false }
    if (pickTo) JalaliDatePickerDialog(state.to, { pickTo = false }) { viewModel.update { s -> s.copy(to = it.endOfDay()) }; pickTo = false }
}

private fun Long.endOfDay(): Long = toLocalDate().endMillis()

@Composable private fun SummaryContent(s: ReportSummary, settings: AppSettings) {
    HyCard {
        SummaryRow("تعداد فروش", formatNumber(s.saleCount.toLong(), settings))
        SummaryRow("فروش ناخالص", formatMoney(s.grossSales, settings))
        SummaryRow("تخفیف", formatMoney(s.discounts, settings))
        SummaryRow("برگشت فروش", formatMoney(s.returns, settings))
        SummaryRow("فروش خالص", formatMoney(s.netSales, settings))
        SummaryRow("دریافت نقدی", formatMoney(s.cashReceived, settings))
        SummaryRow("فروش نسیه", formatMoney(s.creditSales, settings))
        SummaryRow("بهای تمام‌شده", formatMoney(s.cogs, settings))
        SummaryRow("سود ناخالص", formatMoney(s.grossProfit, settings))
        SummaryRow("هزینه عملیاتی", formatMoney(s.operatingExpenses, settings))
        SummaryRow("سود خالص", formatMoney(s.netProfit, settings))
    }
}

@Composable private fun CashContent(c: CashReport, settings: AppSettings) {
    HyCard {
        SummaryRow("مانده اول دوره", formatMoney(c.openingBalance, settings))
        SummaryRow("دریافت‌ها", formatMoney(c.receipts, settings))
        SummaryRow("پرداخت‌ها", formatMoney(c.payments, settings))
        SummaryRow("مانده پایان دوره", formatMoney(c.closingBalance, settings))
    }
}

@Composable private fun ContactReport(contacts: List<Contact>, settings: AppSettings, debtors: Boolean) {
    val sorted = if (debtors) contacts.sortedByDescending { it.currentBalance } else contacts.sortedBy { it.currentBalance }
    if (sorted.isEmpty()) HyEmptyState("موردی نیست", "در این بازه شخصی با مانده ثبت نشده است.")
    sorted.forEach { c -> HyCard { SummaryRow(c.name, balanceLabelShort(c, settings)) } }
}

private fun balanceLabelShort(c: Contact, settings: AppSettings): String = when {
    c.currentBalance > 0 -> "بدهکار ${formatMoney(c.currentBalance, settings)}"
    c.currentBalance < 0 -> "بستانکار ${formatMoney(-c.currentBalance, settings)}"
    else -> "تسویه"
}

@Composable private fun ProductReport(rows: List<ProductReportRow>, settings: AppSettings) {
    if (rows.isEmpty()) HyEmptyState("کالایی نیست", "کالا اضافه کن تا گزارش فروش کالاها ساخته شود.")
    rows.forEach { r ->
        HyCard {
            Text(r.name, style = MaterialTheme.typography.titleSmall)
            SummaryRow("تعداد فروش", formatNumber(r.quantitySold, settings))
            SummaryRow("فروش خالص", formatMoney(r.netSales, settings))
            SummaryRow("سود", formatMoney(r.profit, settings))
        }
    }
}

@Composable private fun UpgradeCard(navController: NavController) {
    HyCard {
        Text("این گزارش در پلن حرفه‌ای فعال است.", style = MaterialTheme.typography.bodyMedium)
        HyButton("مشاهده پلن‌ها", { navController.navigate(ir.hesabyar.feature.navigation.Routes.SUBSCRIPTION) })
    }
}

@Composable private fun TrendChart(trend: List<Pair<String, Long>>, settings: AppSettings) {
    val color = MaterialTheme.colorScheme.primary
    HyCard {
        Text("روند فروش خالص ۷ روز اخیر", style = MaterialTheme.typography.titleSmall)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val max = (trend.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            val barWidth = size.width / (trend.size * 1.6f)
            trend.forEachIndexed { i, (_, value) ->
                val h = (value.toFloat() / max) * size.height
                drawRoundRect(color, Offset(i * barWidth * 1.6f + barWidth * 0.3f, size.height - h), Size(barWidth, h), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            trend.forEach { (label, _) -> Text(label.substringBefore(" "), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
