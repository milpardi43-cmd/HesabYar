package ir.hesabyar.feature.settings

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
import ir.hesabyar.billing.BillingGateway
import ir.hesabyar.billing.BillingPurchaseResult
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.BillingPolicy
import ir.hesabyar.domain.model.Feature
import ir.hesabyar.domain.model.PlanType
import ir.hesabyar.domain.repository.SubscriptionRepository
import ir.hesabyar.feature.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionState(val current: PlanType = PlanType.FREE, val configured: Boolean = false, val message: String? = null)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository, private val gateway: BillingGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(SubscriptionState(configured = gateway.isConfigured))
    val state = _state.asStateFlow()
    fun load(businessId: Long) { viewModelScope.launch { subscriptionRepository.observeSubscription(businessId).collect { s -> _state.value = _state.value.copy(current = runCatching { PlanType.valueOf(s?.planType ?: "FREE") }.getOrDefault(PlanType.FREE)) } } }
    fun purchase(plan: PlanType) {
        viewModelScope.launch {
            when (val result = gateway.purchase(plan)) {
                BillingPurchaseResult.NotConfigured -> _state.value = _state.value.copy(message = "پرداخت بازار هنوز پیکربندی نشده است. هیچ اشتراکی فعال نمی‌شود.")
                is BillingPurchaseResult.Failed -> _state.value = _state.value.copy(message = result.message)
                is BillingPurchaseResult.Verified -> _state.value = _state.value.copy(message = "خرید تأیید شد.")
            }
        }
    }
}

@Composable
fun SubscriptionScreen(businessId: Long, navController: NavController, viewModel: SubscriptionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    HyScreen("اشتراک و پلن‌ها", navController) {
        HyCard { Text("پلن فعلی: ${state.current.label}", style = MaterialTheme.typography.titleMedium) }
        state.message?.let { HyCard { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        PlanType.entries.forEach { plan ->
            HyCard {
                Text(plan.label, style = MaterialTheme.typography.titleMedium)
                val features = when (plan) {
                    PlanType.FREE -> listOf("ثبت درآمد و هزینه", "فروش و خرید", "حداکثر ${BillingPolicy.FREE_CONTACT_LIMIT} شخص و ${BillingPolicy.FREE_PRODUCT_LIMIT} کالا", "گزارش روزانه")
                    PlanType.PROFESSIONAL -> listOf("اشخاص و کالای نامحدود", "گزارش سود و زیان", "خروجی Excel", "یادآور سررسید", "پشتیبان‌گیری پیشرفته محلی")
                    PlanType.BUSINESS -> listOf("همه امکانات حرفه‌ای", "پشتیبان‌گیری ابری (نیازمند اتصال)", "همگام‌سازی و چندکاربره در آینده", "گزارش‌های پیشرفته")
                }
                features.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                if (plan != state.current) HyButton(if (state.configured) "خرید" else "اتصال پرداخت بازار لازم است", { viewModel.purchase(plan) }, enabled = state.configured)
            }
        }
        Text("فعال‌سازی اشتراک فقط پس از تأیید سمت سرور پرداخت بازار انجام می‌شود؛ این نسخه پرداخت ساختگی ندارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
