package ir.hesabyar.data.mapper

import ir.hesabyar.core.database.*
import ir.hesabyar.domain.model.*

fun BusinessEntity.toDomain() = Business(id, name, ownerName, phone, BusinessType.valueOf(businessType), city, createdAt)
fun AccountEntity.toDomain() = Account(id, businessId, title, AccountType.valueOf(accountType), initialBalance, currentBalance)
fun ContactEntity.toDomain() = Contact(id, businessId, name, phone, ContactType.valueOf(contactType), initialBalance, description, currentBalance, createdAt)
fun ProductEntity.toDomain() = Product(id, businessId, name, code, categoryId, purchasePrice, sellingPrice, stockQuantity, minimumStock, inventoryValue, description)
fun ProductEntity.toSnapshot() = ProductSnapshot(id, stockQuantity, inventoryValue, purchasePrice)
fun TransactionEntity.toDomain() = TransactionHeader(
    id = id, businessId = businessId, operationId = operationId, transactionCode = transactionCode,
    type = TransactionType.valueOf(transactionType), amount = amount, paidAmount = paidAmount,
    remainingAmount = remainingAmount, contactId = contactId, accountId = accountId, counterAccountId = counterAccountId,
    occurredAt = transactionDate, description = description, status = TransactionStatus.valueOf(status),
)
fun ReminderEntity.toDomain() = Reminder(id, businessId, contactId, amount, dueDate, status, description, reminderType, productId)
fun SubscriptionEntity.toDomain() = Subscription(id, businessId, planType, startDate, endDate, status, paymentReference)
