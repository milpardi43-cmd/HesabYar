package ir.hesabyar.data.repository

import androidx.room.withTransaction
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.common.runOperation
import ir.hesabyar.core.database.*
import ir.hesabyar.core.validation.Validators
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.BusinessRepository
import ir.hesabyar.domain.repository.SetupCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

class RoomBusinessRepository(private val db: HesabYarDatabase) : BusinessRepository {
    override fun observeBusiness(): Flow<Business?> = db.businessDao().observeFirst().map { it?.toDomain() }
    override suspend fun getBusiness(): Business? = db.businessDao().observeFirst().first()?.toDomain()

    override suspend fun setup(command: SetupCommand): OperationResult<Business> = runOperation {
        val name = Validators.requiredName(command.name, "نام کسب‌وکار")
        val phone = Validators.optionalPhone(command.phone)
        Validators.money(command.openingCash, allowZero = true)
        Validators.money(command.openingReceivables, allowZero = true)
        Validators.money(command.openingPayables, allowZero = true)
        db.withTransaction {
            val existing = db.businessDao().observeFirst().first()
            if (existing != null) return@withTransaction existing.toDomain()
            val now = System.currentTimeMillis()
            val businessId = db.businessDao().insert(BusinessEntity(name = name, ownerName = command.ownerName.trim(), phone = phone, businessType = command.businessType.name, city = command.city.trim(), createdAt = now))
            db.accountDao().insert(AccountEntity(businessId = businessId, title = "صندوق نقدی", accountType = AccountType.CASH.name, initialBalance = command.openingCash, currentBalance = command.openingCash, createdAt = now, updatedAt = now))
            db.accountDao().insert(AccountEntity(businessId = businessId, title = "کارت‌خوان", accountType = AccountType.CARD_READER.name, createdAt = now, updatedAt = now))
            db.accountDao().insert(AccountEntity(businessId = businessId, title = "حساب بانکی", accountType = AccountType.BANK.name, createdAt = now, updatedAt = now))
            db.subscriptionDao().insert(SubscriptionEntity(businessId = businessId, planType = "FREE", status = "ACTIVE", startDate = now))
            if (command.openingReceivables > 0) {
                val contactId = db.contactDao().insert(ContactEntity(businessId = businessId, name = "بدهکاران اولیه", contactType = ContactType.CUSTOMER.name, initialBalance = command.openingReceivables, currentBalance = command.openingReceivables, createdAt = now, updatedAt = now))
                openingTx(businessId, contactId, command.openingReceivables, command.openingDate, now, "مانده اولیهٔ دریافتنی")
            }
            if (command.openingPayables > 0) {
                val contactId = db.contactDao().insert(ContactEntity(businessId = businessId, name = "بستانکاران اولیه", contactType = ContactType.SUPPLIER.name, initialBalance = -command.openingPayables, currentBalance = -command.openingPayables, createdAt = now, updatedAt = now))
                openingTx(businessId, contactId, -command.openingPayables, command.openingDate, now, "مانده اولیهٔ پرداختنی")
            }
            Business(id = businessId, name = name, ownerName = command.ownerName.trim(), phone = phone, businessType = command.businessType, city = command.city.trim(), createdAt = now)
        }
    }

    private suspend fun openingTx(businessId: Long, contactId: Long, delta: Long, occurredAt: Long, now: Long, reason: String) {
        val txId = db.transactionDao().insert(TransactionEntity(
            businessId = businessId, operationId = "opening-${UUID.randomUUID()}", transactionCode = "TMP-${UUID.randomUUID()}",
            transactionType = TransactionType.OPENING.name, amount = kotlin.math.abs(delta), paidAmount = 0, remainingAmount = kotlin.math.abs(delta),
            contactId = contactId, transactionDate = occurredAt, description = reason, createdAt = now, updatedAt = now,
        ))
        db.transactionDao().updateCode(txId, "HY-$txId", now)
        val contact = db.contactDao().getById(contactId)!!
        db.contactLedgerDao().insert(ContactLedgerEntity(contactId = contactId, transactionId = txId, debit = if (delta > 0) delta else 0, credit = if (delta < 0) -delta else 0, balanceAfter = contact.currentBalance, createdAt = now))
    }

    override suspend fun update(command: SetupCommand): OperationResult<Business> = runOperation {
        val name = Validators.requiredName(command.name)
        val phone = Validators.optionalPhone(command.phone)
        val current = getBusiness() ?: error("کسب‌وکار پیدا نشد.")
        db.withTransaction {
            db.businessDao().update(BusinessEntity(id = current.id, name = name, ownerName = command.ownerName.trim(), phone = phone, businessType = command.businessType.name, city = command.city.trim(), createdAt = current.createdAt))
            current.copy(name = name, ownerName = command.ownerName.trim(), phone = phone, businessType = command.businessType, city = command.city.trim())
        }
    }
}
