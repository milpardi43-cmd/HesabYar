package ir.hesabyar.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.core.database.ReminderEntity
import ir.hesabyar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: HesabYarDatabase,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("hesabyar-reminders", "یادآورهای حساب‌یار", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val business = db.businessDao().observeFirst().first() ?: return Result.success()
        if (settings.stockAlerts) {
            db.productDao().observeLowStock(business.id).first().forEach { product ->
                val existing = db.reminderDao().findPendingProduct(business.id, "LOW_STOCK", product.id)
                if (existing == null) {
                    db.reminderDao().insert(ReminderEntity(businessId = business.id, productId = product.id, dueDate = now, description = "موجودی «${product.name}» کم است.", reminderType = "LOW_STOCK", createdAt = now))
                }
            }
        }
        val due = db.reminderDao().getDue(now)
        due.forEachIndexed { index, reminder ->
            notify(manager, 2000 + index.toInt(), reminder.description)
            db.reminderDao().update(reminder.copy(status = "NOTIFIED"))
        }
        return Result.success()
    }

    private fun notify(manager: NotificationManager, id: Int, text: String) {
        val launch = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val pending = PendingIntent.getActivity(applicationContext, id, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(applicationContext, "hesabyar-reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("حساب‌یار")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}
