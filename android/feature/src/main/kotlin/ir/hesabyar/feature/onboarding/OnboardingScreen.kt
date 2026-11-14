package ir.hesabyar.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.BusinessType
import ir.hesabyar.domain.repository.BusinessRepository
import ir.hesabyar.domain.repository.SetupCommand
import ir.hesabyar.feature.common.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val name: String = "", val owner: String = "", val phone: String = "", val city: String = "",
    val type: BusinessType = BusinessType.OTHER,
    val openingCash: String = "", val openingReceivables: String = "", val openingPayables: String = "",
    val openingDate: Long = System.currentTimeMillis(),
    val loading: Boolean = false, val error: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val businessRepository: BusinessRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()
    fun update(block: (OnboardingState) -> OnboardingState) { _state.value = block(_state.value) }
    fun submit() {
        val s = _state.value
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            val command = SetupCommand(
                name = s.name, ownerName = s.owner, phone = s.phone, businessType = s.type, city = s.city,
                openingCash = parseMoneyInput(s.openingCash) ?: 0,
                openingReceivables = parseMoneyInput(s.openingReceivables) ?: 0,
                openingPayables = parseMoneyInput(s.openingPayables) ?: 0,
                openingDate = s.openingDate,
            )
            businessRepository.setup(command).handle(
                onSuccess = { _state.value = _state.value.copy(loading = false) },
                onError = { msg -> _state.value = _state.value.copy(loading = false, error = msg) },
            )
        }
    }
}

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }
    val settings = ir.hesabyar.domain.repository.AppSettings()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (page) {
            0 -> IntroPage("حساب‌یار", "حساب‌وکتاب فروشگاهت، همیشه همراهت", "دفتر کاغذی را کنار بگذار؛ فروش، بدهی و سود فروشگاهت را ساده و دقیق ثبت کن.")
            1 -> IntroPage("فروش و صندوق", "فروش نقدی، نسیه و ترکیبی را در چند ثانیه ثبت کن و موجودی صندوق را همیشه بدان.", null)
            2 -> IntroPage("مشتریان و بدهی‌ها", "طلب‌ها و بدهی‌ها را شفاف نگه دار و وصول هر مبلغ را جدا از فروش ثبت کن.", null)
            3 -> IntroPage("سود و گزارش‌ها", "سود واقعی، موجودی کالا و گزارش روزانه و ماهانه را از اطلاعات واقعی ببین.", null)
            4 -> BusinessForm(state, settings, viewModel)
        }
        Spacer(Modifier.weight(1f))
        HyErrorBanner(state.error) { viewModel.update { it.copy(error = null) } }
        if (page < 4) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HyOutlinedButton("رد کردن", { page = 4 }, Modifier.weight(1f))
                HyButton(if (page == 3) "شروع" else "ادامه", { page += 1 }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IntroPage(title: String, subtitle: String, body: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun BusinessForm(state: OnboardingState, settings: ir.hesabyar.domain.repository.AppSettings, viewModel: OnboardingViewModel) {
    var typePicker by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    HySectionTitle("اطلاعات کسب‌وکار")
    HyField(state.name, { v -> viewModel.update { it.copy(name = v) } }, "نام فروشگاه *")
    HyField(state.owner, { v -> viewModel.update { it.copy(owner = v) } }, "نام صاحب فروشگاه")
    HyField(state.phone, { v -> viewModel.update { it.copy(phone = v) } }, "تلفن همراه")
    HyField(state.city, { v -> viewModel.update { it.copy(city = v) } }, "شهر")
    OutlinedButton(onClick = { typePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("نوع کسب‌وکار: ${state.type.label}") }
    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "بستن مانده‌های اولیه" else "مانده‌های اولیه (اختیاری)") }
    if (advanced) {
        MoneyField(state.openingCash, { v -> viewModel.update { it.copy(openingCash = v) } }, "موجودی نقدی اولیه")
        MoneyField(state.openingReceivables, { v -> viewModel.update { it.copy(openingReceivables = v) } }, "طلب‌های اولیه")
        MoneyField(state.openingPayables, { v -> viewModel.update { it.copy(openingPayables = v) } }, "بدهی‌های اولیه")
        JalaliDateField(state.openingDate, "تاریخ شروع", { d -> viewModel.update { it.copy(openingDate = d) } }, settings)
    }
    HyButton(if (state.loading) "در حال ذخیره…" else "ذخیره و ورود به حساب‌یار", { viewModel.submit() }, enabled = !state.loading)
    if (typePicker) SelectorDialog("نوع کسب‌وکار", BusinessType.entries, { it.label }, { typePicker = false }) { viewModel.update { s -> s.copy(type = it) }; typePicker = false }
}
