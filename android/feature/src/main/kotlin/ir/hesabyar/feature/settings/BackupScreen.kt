package ir.hesabyar.feature.settings

import android.content.Intent
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.core.design.*
import ir.hesabyar.data.worker.BackupWorker
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.BackupRepository
import ir.hesabyar.domain.repository.SettingsRepository
import ir.hesabyar.feature.common.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BackupState(val loading: Boolean = false, val message: String? = null, val error: String? = null, val pendingRestore: String? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository, private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupState())
    val state = _state.asStateFlow()
    fun update(block: (BackupState) -> BackupState) { _state.value = block(_state.value) }
    fun create() { update { it.copy(loading = true, error = null) }; viewModelScope.launch { backupRepository.createBackup().handle({ p -> update { it.copy(loading = false, message = "پشتیبان ساخته شد: $p") } }, { m -> update { it.copy(loading = false, error = m) } }) } }
    fun requestRestore(uri: String) { update { it.copy(pendingRestore = uri) } }
    fun confirmRestore() {
        val uri = _state.value.pendingRestore ?: return
        update { it.copy(loading = true, pendingRestore = null) }
        viewModelScope.launch { backupRepository.restoreBackup(uri).handle({ update { it.copy(loading = false, message = "بازیابی انجام شد. برنامه را دوباره باز کنید.") } }, { m -> update { it.copy(loading = false, error = m) } }) }
    }
    fun exportCsv(onPath: (String) -> Unit) { viewModelScope.launch { backupRepository.exportCsv().handle(onPath, { m -> update { it.copy(error = m) } }) } }
    fun exportExcel(onPath: (String) -> Unit) { viewModelScope.launch { backupRepository.exportExcel().handle(onPath, { m -> update { it.copy(error = m) } }) } }
    fun setAuto(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutomaticBackup(enabled)
            if (enabled) workManager.enqueueUniquePeriodicWork("hesabyar-backup", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).build())
            else workManager.cancelUniqueWork("hesabyar-backup")
        }
    }
}

@Composable
fun BackupScreen(settings: AppSettings, navController: NavController, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.requestRestore(it.toString()) } }
    HyScreen("پشتیبان‌گیری و خروجی", navController) {
        HyErrorBanner(state.error) { viewModel.update { it.copy(error = null) } }
        state.message?.let { msg ->
            HyCard { Text(msg, style = MaterialTheme.typography.bodyMedium) }
            if (msg.startsWith("بازیابی")) HyButton("بازکردن دوباره برنامه", {
                val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(launch)
                Runtime.getRuntime().exit(0)
            })
        }
        HyButton(if (state.loading) "در حال انجام…" else "ساخت پشتیبان محلی", { viewModel.create() }, enabled = !state.loading)
        HyOutlinedButton("بازیابی از فایل", { restorePicker.launch(arrayOf("*/*")) })
        Text("پشتیبان شامل کسب‌وکار، حساب‌ها، اشخاص، کالاها، تراکنش‌ها، فروش‌ها، خریدها، هزینه‌ها، دفترها و موجودی است. بازیابی فقط پس از تأیید شما انجام می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("پشتیبان‌گیری خودکار روزانه"); Switch(checked = settings.automaticBackup, onCheckedChange = viewModel::setAuto) }
        HySectionTitle("خروجی گزارش")
        HyOutlinedButton("خروجی CSV تراکنش‌ها", { viewModel.exportCsv { shareFile(context, it, "text/csv") } })
        HyOutlinedButton("خروجی Excel (xlsx)", { viewModel.exportExcel { shareFile(context, it, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") } })
    }
    state.pendingRestore?.let {
        AlertDialog(onDismissRequest = { viewModel.update { it.copy(pendingRestore = null) } }, title = { Text("تأیید بازیابی") },
            text = { Text("اطلاعات فعلی با فایل پشتیبان جایگزین می‌شود. این کار قابل بازگشت نیست. ادامه می‌دهید؟") },
            confirmButton = { TextButton(onClick = { viewModel.confirmRestore() }) { Text("بازیابی") } },
            dismissButton = { TextButton(onClick = { viewModel.update { it.copy(pendingRestore = null) } }) { Text("انصراف") } })
    }
}
