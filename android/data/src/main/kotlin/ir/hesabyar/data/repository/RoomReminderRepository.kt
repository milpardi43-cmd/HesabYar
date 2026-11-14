package ir.hesabyar.data.repository

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.common.runOperation
import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.core.database.ReminderEntity
import ir.hesabyar.data.worker.ReminderWorker
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.domain.model.Reminder
import ir.hesabyar.domain.repository.ReminderCommand
import ir.hesabyar.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

class RoomReminderRepository(private val db: HesabYarDatabase, private val workManager: WorkManager) : ReminderRepository {
    override fun observeReminders(businessId: Long): Flow<List<Reminder>> = db.reminderDao().observeByBusiness(businessId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(command: ReminderCommand): OperationResult<Reminder> = runOperation {
        val businessId = db.businessDao().observeFirst().first()?.id ?: error("کسب‌وکار پیدا نشد.")
        val now = System.currentTimeMillis()
        val id = db.reminderDao().insert(ReminderEntity(businessId = businessId, contactId = command.contactId, productId = command.productId, amount = command.amount, dueDate = command.dueDate, description = command.description, reminderType = command.reminderType, createdAt = now))
        Reminder(id, businessId, command.contactId, command.amount, command.dueDate, "PENDING", command.description)
    }

    override suspend fun markDone(id: Long): OperationResult<Unit> = runOperation {
        val entity = db.reminderDao().getById(id) ?: error("یادآور پیدا نشد.")
        db.reminderDao().update(entity.copy(status = "DONE"))
    }

    override suspend fun scheduleDueCheck() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
        workManager.enqueueUniquePeriodicWork("hesabyar-reminders", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
