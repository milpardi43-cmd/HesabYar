package ir.hesabyar.feature.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import ir.hesabyar.domain.model.Contact
import ir.hesabyar.domain.model.ContactType
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.ContactRepository
import ir.hesabyar.feature.common.*
import ir.hesabyar.feature.navigation.Routes
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsState(val contacts: List<Contact> = emptyList(), val query: String = "", val filter: ContactType? = null, val sortByBalance: Boolean = false)

@HiltViewModel
class ContactsViewModel @Inject constructor(private val repository: ContactRepository) : ViewModel() {
    private val _state = MutableStateFlow(ContactsState())
    val state = _state.asStateFlow()
    private var loaded = false
    fun load(businessId: Long) { if (loaded) return; loaded = true; viewModelScope.launch { repository.observeContacts(businessId).collect { c -> _state.value = _state.value.copy(contacts = c) } } }
    fun update(block: (ContactsState) -> ContactsState) { _state.value = block(_state.value) }
}

@Composable
fun ContactsScreen(businessId: Long, settings: AppSettings, navController: NavController, viewModel: ContactsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(businessId) { viewModel.load(businessId) }
    var filterPicker by remember { mutableStateOf(false) }
    val filtered = state.contacts
        .filter { it.name.contains(state.query, true) || it.phone.contains(state.query) }
        .filter { state.filter == null || it.contactType == state.filter || it.contactType == ContactType.BOTH }
        .let { if (state.sortByBalance) it.sortedByDescending { kotlin.math.abs(it.currentBalance) } else it.sortedBy { it.name } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("اشخاص", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { navController.navigate(Routes.contactForm()) }) { Icon(Icons.Filled.Add, contentDescription = "افزودن شخص") }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HyField(state.query, { v -> viewModel.update { it.copy(query = v) } }, "جستجوی نام یا تلفن")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filterPicker = true }, modifier = Modifier.weight(1f)) { Text(state.filter?.label ?: "همه") }
                OutlinedButton(onClick = { viewModel.update { it.copy(sortByBalance = !it.sortByBalance) } }, modifier = Modifier.weight(1f)) { Text(if (state.sortByBalance) "مرتب بر اساس مانده" else "مرتب بر اساس نام") }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item { HyEmptyState("شخصی ثبت نشده", "مشتری یا تأمین‌کننده اضافه کن.", "افزودن شخص") { navController.navigate(Routes.contactForm()) } }
            items(filtered, key = { it.id }) { contact ->
                HyCard(Modifier.clickable { navController.navigate(Routes.contact(contact.id)) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(contact.name, style = MaterialTheme.typography.titleSmall)
                        Text(balanceLabel(contact, settings), style = MaterialTheme.typography.bodySmall, color = if (contact.currentBalance > 0) MaterialTheme.colorScheme.primary else if (contact.currentBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${contact.contactType.label}${if (contact.phone.isNotEmpty()) " | ${contact.phone}" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (filterPicker) SelectorDialog("نوع شخص", listOf<ContactType?>(null) + ContactType.entries, { it?.label ?: "همه" }, { filterPicker = false }) { viewModel.update { s -> s.copy(filter = it) }; filterPicker = false }
}

fun balanceLabel(contact: Contact, settings: AppSettings): String = when {
    contact.currentBalance > 0 -> "بدهکار ${formatMoney(contact.currentBalance, settings)}"
    contact.currentBalance < 0 -> "بستانکار ${formatMoney(-contact.currentBalance, settings)}"
    else -> "تسویه"
}
