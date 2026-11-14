package ir.hesabyar.feature.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import ir.hesabyar.core.calendar.JalaliCalendar
import ir.hesabyar.core.currency.TomanFormatter
import ir.hesabyar.core.design.HyButton
import ir.hesabyar.core.design.HyField
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HyScreen(title: String, navController: NavController?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (navController != null) {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun MoneyField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    HyField(value = value, onValueChange = { input ->
        val normalized = TomanFormatter.normalizeDigits(input).filter { it.isDigit() || it == ',' || it == '٬' }
        onValueChange(normalized)
    }, label = label, modifier = modifier)
}

fun parseMoneyInput(value: String): Long? = TomanFormatter.parse(value)

@Composable
fun JalaliDateField(millis: Long, label: String, onPick: (Long) -> Unit, settings: ir.hesabyar.domain.repository.AppSettings) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${formatDate(millis, settings)}")
    }
    if (open) {
        JalaliDatePickerDialog(initial = millis, onDismiss = { open = false }, onConfirm = { onPick(it); open = false })
    }
}

@Composable
fun JalaliDatePickerDialog(initial: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val j = JalaliCalendar.toJalali(initial.toLocalDate())
    var year by remember { mutableStateOf(j.year.toString()) }
    var month by remember { mutableStateOf(j.month.toString()) }
    var day by remember { mutableStateOf(j.day.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HyField(year, { year = it.filter(Char::isDigit) }, "سال")
                HyField(month, { month = it.filter(Char::isDigit) }, "ماه")
                HyField(day, { day = it.filter(Char::isDigit) }, "روز")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val y = year.toIntOrNull(); val m = month.toIntOrNull(); val d = day.toIntOrNull()
                if (y == null || m == null || d == null) { error = "تاریخ معتبر نیست."; return@TextButton }
                runCatching { JalaliCalendar.toGregorian(y, m, d).startMillis() }
                    .onSuccess { onConfirm(it) }
                    .onFailure { error = "تاریخ معتبر نیست." }
            }) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
fun <T> SelectorDialog(title: String, options: List<T>, label: (T) -> String, onDismiss: () -> Unit, footer: (@Composable () -> Unit)? = null, onSelect: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) { Text(label(option)) }
                }
                footer?.invoke()
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
fun <T> SearchPickerDialog(
    title: String,
    options: List<T>,
    label: (T) -> String,
    searchHint: String = "جستجو…",
    filter: (T, String) -> Boolean = { item, q -> label(item).contains(q, ignoreCase = true) },
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    val filtered = options.filter { filter(it, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HyField(query, { query = it }, searchHint)
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    filtered.forEach { option ->
                        TextButton(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth()) { Text(label(option)) }
                    }
                    if (filtered.isEmpty()) Text("موردی پیدا نشد.", style = MaterialTheme.typography.bodySmall)
                }
                footer?.invoke()
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
