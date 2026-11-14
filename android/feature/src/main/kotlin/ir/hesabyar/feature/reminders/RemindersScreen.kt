package ir.hesabyar.feature.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import ir.hesabyar.domain.model.Reminder
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ReminderCommand
import ir.hesabyar.domain.repository.ReminderRepository
import ir.hesabyar.feature.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersState(val reminders: List<Reminder> = emptyList(), val error: String? = null)

@HiltViewModel
class RemindersViewModel @Inject constructor(private val repository: ReminderRepository) : ViewModel() {
    private val _state = MutableStateFlow(RemindersState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long) { if (loaded) return; loaded = true; viewModelScope.launch { repository.observeReminders(businessId).collect { r -> _state.value = _state.value.copy(reminders = r) } }; viewModelScope.launch { repository.scheduleDueCheck() } }
    fun markDone(id: Long) { viewModelScope.launch { repository.markDone(id).handle({}, { m -> _state.value = _state.value.copy(error = m) }) } }
    fun add(description: String, dueDate: Long) { viewModelScope.launch { repository.add(ReminderCommand(description = description, dueDate = dueDate)).handle({}, { m -> _state.value = _state.value.copy(error = m) }) } }
}

@Composable
fun RemindersScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: RemindersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    var addDialog by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(System.currentTimeMillis()) }
    HyScreen("یادآورها", navController) {
        HyErrorBanner(state.error) { }
        HyButton("افزودن یادآور", { addDialog = true })
        if (state.reminders.isEmpty()) HyEmptyState("یادآوری نیست", "سررسید بدهی، پرداخت تأمین‌کننده یا موجودی کم اینجا نمایش داده می‌شود.")
        state.reminders.forEach { r ->
            HyCard {
                Text(r.description, style = MaterialTheme.typography.bodyMedium)
                Text("${formatDate(r.dueDate, settings)} | ${if (r.status == "DONE") "انجام‌شده" else if (r.status == "NOTIFIED") "اعلان‌شده" else "در انتظار"}", style = MaterialTheme.typography.bodySmall)
                if (r.amount > 0) Text(formatMoney(r.amount, settings), style = MaterialTheme.typography.bodySmall)
                if (r.status != "DONE") HyOutlinedButton("انجام شد", { viewModel.markDone(r.id) })
            }
        }
    }
    if (addDialog) AlertDialog(onDismissRequest = { addDialog = false }, title = { Text("یادآور جدید") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { HyField(description, { description = it }, "توضیح"); JalaliDateField(dueDate, "سررسید", { dueDate = it }, settings) } },
        confirmButton = { TextButton(onClick = { viewModel.add(description, dueDate); addDialog = false }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text("انصراف") } })
}
