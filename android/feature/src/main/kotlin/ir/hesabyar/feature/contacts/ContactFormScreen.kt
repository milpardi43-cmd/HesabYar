package ir.hesabyar.feature.contacts

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
import ir.hesabyar.domain.model.ContactType
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ContactCommand
import ir.hesabyar.domain.repository.ContactRepository
import ir.hesabyar.feature.common.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactFormState(
    val id: Long = 0, val name: String = "", val phone: String = "", val type: ContactType = ContactType.CUSTOMER,
    val balanceInput: String = "", val debtor: Boolean = true, val description: String = "",
    val loading: Boolean = false, val error: String? = null, val saved: Boolean = false,
)

@HiltViewModel
class ContactFormViewModel @Inject constructor(private val repository: ContactRepository) : ViewModel() {
    private val _state = MutableStateFlow(ContactFormState())
    val state = _state.asStateFlow()
    fun load(id: Long?) { if (id == null) return; viewModelScope.launch { repository.getById(id)?.let { c -> _state.value = ContactFormState(id = c.id, name = c.name, phone = c.phone, type = c.contactType, description = c.description) } } }
    fun update(block: (ContactFormState) -> ContactFormState) { _state.value = block(_state.value) }
    fun submit() {
        val s = _state.value; update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val raw = parseMoneyInput(s.balanceInput) ?: 0
            val balance = if (s.debtor) raw else -raw
            val command = ContactCommand(id = s.id, name = s.name, phone = s.phone, contactType = s.type, initialBalance = if (s.id == 0L) balance else 0, description = s.description)
            val result = if (s.id == 0L) repository.add(command) else repository.update(command)
            result.handle({ update { it.copy(loading = false, saved = true) } }, { m -> update { it.copy(loading = false, error = m) } })
        }
    }
}

@Composable
fun ContactFormScreen(businessId: Long, id: Long?, settings: AppSettings, navController: NavController, viewModel: ContactFormViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.load(id) }
    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }
    var typePicker by remember { mutableStateOf(false) }
    HyScreen(if (id == null) "افزودن شخص" else "ویرایش شخص", navController) {
        HyErrorBanner(state.error) { viewModel.update { it.copy(error = null) } }
        HyField(state.name, { v -> viewModel.update { it.copy(name = v) } }, "نام *")
        HyField(state.phone, { v -> viewModel.update { it.copy(phone = v) } }, "تلفن همراه")
        OutlinedButton(onClick = { typePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("نوع: ${state.type.label}") }
        if (id == null) {
            MoneyField(state.balanceInput, { v -> viewModel.update { it.copy(balanceInput = v) } }, "مانده اولیه")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.debtor, onClick = { viewModel.update { it.copy(debtor = true) } }, label = { Text("بدهکار به ما") })
                FilterChip(selected = !state.debtor, onClick = { viewModel.update { it.copy(debtor = false) } }, label = { Text("بستانکار از ما") })
            }
        }
        HyField(state.description, { v -> viewModel.update { it.copy(description = v) } }, "توضیح", singleLine = false)
        HyButton(if (state.loading) "در حال ذخیره…" else "ذخیره", { viewModel.submit() }, enabled = !state.loading)
    }
    if (typePicker) SelectorDialog("نوع شخص", ContactType.entries, { it.label }, { typePicker = false }) { t -> viewModel.update { it.copy(type = t) }; typePicker = false }
}
