package ir.hesabyar.feature.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ir.hesabyar.core.calendar.JalaliCalendar
import ir.hesabyar.core.common.AppError
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.currency.TomanFormatter
import ir.hesabyar.domain.repository.AppSettings
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun LocalDate.endMillis(): Long = plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
fun todayStart(): Long = LocalDate.now().startMillis()
fun todayEnd(): Long = LocalDate.now().endMillis()
fun newOperationId(): String = UUID.randomUUID().toString()

fun formatMoney(amount: Long, settings: AppSettings, withUnit: Boolean = true): String =
    TomanFormatter.format(amount, persianDigits = settings.persianDigits, withUnit = withUnit)

fun formatNumber(value: Long, settings: AppSettings): String {
    val grouped = TomanFormatter.group(value)
    return if (settings.persianDigits) TomanFormatter.toPersianDigits(grouped) else grouped
}

fun formatDate(millis: Long, settings: AppSettings): String {
    val text = JalaliCalendar.format(millis.toLocalDate())
    return if (settings.persianDigits) TomanFormatter.toPersianDigits(text) else text
}

fun formatDateTime(millis: Long, settings: AppSettings): String {
    val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(millis))
    val text = "${formatDate(millis, settings)} - $time"
    return if (settings.persianDigits) TomanFormatter.toPersianDigits(text) else text
}

fun AppError.message(): String = when (this) {
    is AppError.Validation -> message
    is AppError.NotFound -> message
    is AppError.Conflict -> message
    is AppError.Storage -> message
}

inline fun <T> OperationResult<T>.handle(onSuccess: (T) -> Unit, onError: (String) -> Unit) {
    when (this) {
        is OperationResult.Success -> onSuccess(value)
        is OperationResult.Failure -> onError(error.message())
    }
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
}

fun shareFile(context: Context, path: String, mime: String) {
    val file = File(path)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
}
