package ir.hesabyar.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.domain.model.Business
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.AppTheme
import ir.hesabyar.domain.repository.SettingsRepository
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    fun setTheme(theme: AppTheme) { viewModelScope.launch { repository.setTheme(theme) } }
    fun setDigits(enabled: Boolean) { viewModelScope.launch { repository.setPersianDigits(enabled) } }
    fun setStockAlerts(enabled: Boolean) { viewModelScope.launch { repository.setStockAlerts(enabled) } }
    fun setNotifications(enabled: Boolean) { viewModelScope.launch { repository.setNotificationsEnabled(enabled) } }
    fun setFont(percent: Int) { viewModelScope.launch { repository.setFontScalePercent(percent) } }
}

@Composable
fun SettingsScreen(business: Business, settings: AppSettings, navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    var themePicker by remember { mutableStateOf(false) }
    HyScreen("تنظیمات", navController) {
        HyCard {
            Text(business.name, style = MaterialTheme.typography.titleMedium)
            Text("${business.businessType.label}${if (business.city.isNotEmpty()) " | ${business.city}" else ""}", style = MaterialTheme.typography.bodySmall)
            if (business.ownerName.isNotEmpty()) Text(business.ownerName, style = MaterialTheme.typography.bodySmall)
        }
        HySectionTitle("نمایش")
        OutlinedButton(onClick = { themePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("تم: ${when (settings.theme) { AppTheme.SYSTEM -> "سیستم"; AppTheme.LIGHT -> "روشن"; AppTheme.DARK -> "تاریک" }}") }
        SettingSwitch("ارقام فارسی", settings.persianDigits, viewModel::setDigits)
        Text("اندازه متن: ${formatNumber(settings.fontScalePercent.toLong(), settings)}٪", style = MaterialTheme.typography.bodyMedium)
        Slider(value = settings.fontScalePercent.toFloat(), onValueChange = { viewModel.setFont(it.toInt()) }, valueRange = 85f..130f, steps = 8)
        HySectionTitle("مالی و هشدارها")
        SettingSwitch("هشدار موجودی کم", settings.stockAlerts, viewModel::setStockAlerts)
        SettingSwitch("اعلان یادآورها", settings.notificationsEnabled, viewModel::setNotifications)
        HySectionTitle("داده‌ها و اشتراک")
        HyOutlinedButton("پشتیبان‌گیری و خروجی", { navController.navigate(Routes.BACKUP) })
        HyOutlinedButton("اشتراک و پلن‌ها", { navController.navigate(Routes.SUBSCRIPTION) })
        HyOutlinedButton("درباره و راهنما", { navController.navigate(Routes.ABOUT) })
    }
    if (themePicker) SelectorDialog("تم برنامه", AppTheme.entries, { when (it) { AppTheme.SYSTEM -> "سیستم"; AppTheme.LIGHT -> "روشن"; AppTheme.DARK -> "تاریک" } }, { themePicker = false }) { viewModel.setTheme(it); themePicker = false }
}

@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
