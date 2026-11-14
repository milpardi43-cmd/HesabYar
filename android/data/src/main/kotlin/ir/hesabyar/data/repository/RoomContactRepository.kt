package ir.hesabyar.data.repository

import androidx.room.withTransaction
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.common.runOperation
import ir.hesabyar.core.database.*
import ir.hesabyar.core.validation.Validators
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.domain.model.Contact
import ir.hesabyar.domain.model.BillingPolicy
import ir.hesabyar.domain.model.PlanType
import ir.hesabyar.domain.model.TransactionType
import ir.hesabyar.domain.repository.ContactCommand
import ir.hesabyar.domain.repository.ContactLedgerEntry
import ir.hesabyar.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomContactRepository(private val db: HesabYarDatabase) : ContactRepository {
    override fun observeContacts(businessId: Long): Flow<List<Contact>> = db.contactDao().observeByBusiness(businessId).map { list -> list.map { it.toDomain() } }
    override fun searchContacts(businessId: Long, query: String): Flow<List<Contact>> = db.contactDao().search(businessId, query.trim()).map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: Long): Contact? = db.contactDao().getById(id)?.toDomain()

    override fun observeLedger(contactId: Long): Flow<List<ContactLedgerEntry>> =
        db.contactLedgerDao().observeByContact(contactId).map { list ->
            list.map { entry ->
                val tx = db.transactionDao().getById(entry.transactionId)
                ContactLedgerEntry(entry.id, entry.contactId, entry.transactionId, entry.debit, entry.credit, entry.balanceAfter, entry.createdAt, tx?.transactionCode ?: "", tx?.transactionType?.let { TransactionType.valueOf(it) } ?: TransactionType.OPENING)
            }
        }

    override suspend fun add(command: ContactCommand): OperationResult<Contact> = runOperation {
        val name = Validators.requiredName(command.name)
        val phone = Validators.optionalPhone(command.phone)
        Validators.money(kotlin.math.abs(command.initialBalance), allowZero = true, label = "مانده اولیه")
        val businessId = db.businessDao().observeFirst().first()?.id ?: error("کسب‌وکار پیدا نشد.")
        val now = System.currentTimeMillis()
        db.withTransaction {
            val plan = currentPlan(businessId)
            val count = db.contactDao().count(businessId)
            ir.hesabyar.core.common.requireValid(BillingPolicy.canAddContact(plan, count), "در پلن رایگان حداکثر ${BillingPolicy.FREE_CONTACT_LIMIT} شخص قابل ثبت است.")
            val id = db.contactDao().insert(ContactEntity(businessId = businessId, name = name, phone = phone, contactType = command.contactType.name, initialBalance = command.initialBalance, currentBalance = command.initialBalance, description = command.description.trim(), createdAt = now, updatedAt = now))
            if (command.initialBalance != 0L) {
                val txId = db.transactionDao().insert(TransactionEntity(
                    businessId = businessId, operationId = "contact-opening-${UUID.randomUUID()}", transactionCode = "TMP-${UUID.randomUUID()}",
                    transactionType = TransactionType.OPENING.name, amount = kotlin.math.abs(command.initialBalance), paidAmount = 0, remainingAmount = kotlin.math.abs(command.initialBalance),
                    contactId = id, transactionDate = now, description = "مانده اولیهٔ شخص", createdAt = now, updatedAt = now,
                ))
                db.transactionDao().updateCode(txId, "HY-$txId", now)
                db.contactLedgerDao().insert(ContactLedgerEntity(contactId = id, transactionId = txId, debit = if (command.initialBalance > 0) command.initialBalance else 0, credit = if (command.initialBalance < 0) -command.initialBalance else 0, balanceAfter = command.initialBalance, createdAt = now))
            }
            Contact(id, businessId, name, phone, command.contactType, command.initialBalance, command.description.trim(), command.initialBalance, now)
        }
    }

    override suspend fun update(command: ContactCommand): OperationResult<Contact> = runOperation {
        val name = Validators.requiredName(command.name)
        val phone = Validators.optionalPhone(command.phone)
        val existing = db.contactDao().getById(command.id) ?: error("شخص پیدا نشد.")
        db.withTransaction {
            val entity = existing.copy(name = name, phone = phone, contactType = command.contactType.name, description = command.description.trim(), updatedAt = System.currentTimeMillis())
            db.contactDao().update(entity)
            entity.toDomain()
        }
    }

    private suspend fun currentPlan(businessId: Long): PlanType {
        val sub = db.subscriptionDao().observeCurrent(businessId).first()
        return runCatching { PlanType.valueOf(sub?.planType ?: "FREE") }.getOrDefault(PlanType.FREE)
    }
}
