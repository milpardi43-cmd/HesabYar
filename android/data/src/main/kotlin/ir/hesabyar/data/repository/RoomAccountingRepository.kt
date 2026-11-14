package ir.hesabyar.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import ir.hesabyar.core.common.*
import ir.hesabyar.core.database.*
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.data.mapper.toSnapshot
import ir.hesabyar.domain.accounting.*
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.AccountingRepository
import ir.hesabyar.domain.repository.ExpenseDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * All multi-part financial writes happen inside one Room transaction.
 * Contact balances are signed: positive = receivable (customer owes us), negative = payable (we owe).
 * Duplicate operationId returns the original posted transaction instead of writing twice.
 */
class RoomAccountingRepository(private val db: HesabYarDatabase) : AccountingRepository {

    private suspend fun requireBusinessId(): Long =
        db.businessDao().observeFirst().first()?.id ?: throw NotFoundException("کسب‌وکار پیدا نشد.")

    private suspend fun withIdempotency(operationId: String, block: suspend () -> TransactionHeader): TransactionHeader {
        requireValid(operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
        db.transactionDao().findByOperationId(operationId)?.let { return it.toDomain() }
        return try {
            db.withTransaction {
                db.transactionDao().findByOperationId(operationId)?.let { return@withTransaction it.toDomain() }
                block()
            }
        } catch (e: SQLiteConstraintException) {
            db.transactionDao().findByOperationId(operationId)?.toDomain() ?: throw e
        }
    }

    private suspend fun insertTx(
        businessId: Long,
        operationId: String,
        type: TransactionType,
        amount: Toman,
        paidAmount: Toman,
        remainingAmount: Toman,
        occurredAt: Long,
        now: Long,
        contactId: Long? = null,
        accountId: Long? = null,
        counterAccountId: Long? = null,
        paymentMethod: PaymentMethod? = null,
        description: String = "",
        relatedTransactionId: Long? = null,
    ): Long {
        val id = db.transactionDao().insert(TransactionEntity(
            businessId = businessId,
            operationId = operationId,
            transactionCode = "TMP-${UUID.randomUUID()}",
            transactionType = type.name,
            amount = amount,
            paidAmount = paidAmount,
            remainingAmount = remainingAmount,
            contactId = contactId,
            accountId = accountId,
            counterAccountId = counterAccountId,
            paymentMethod = paymentMethod?.name,
            transactionDate = occurredAt,
            description = description,
            relatedTransactionId = relatedTransactionId,
            createdAt = now,
            updatedAt = now,
        ))
        db.transactionDao().updateCode(id, "HY-$id", now)
        return id
    }

    private suspend fun changeAccount(accountId: Long, delta: Toman, txId: Long, now: Long, allowNegative: Boolean = false): Long {
        val account = db.accountDao().getById(accountId) ?: throw NotFoundException("حساب پیدا نشد.")
        val balance = account.currentBalance.safeAdd(delta)
        requireValid(allowNegative || balance >= 0, "موجودی حساب «${account.title}» کافی نیست.")
        db.accountDao().updateBalance(account.id, balance, now)
        db.accountLedgerDao().insert(AccountLedgerEntity(accountId = account.id, transactionId = txId, delta = delta, balanceAfter = balance, createdAt = now))
        return balance
    }

    private suspend fun changeContact(contactId: Long, delta: Toman, txId: Long, now: Long): Long {
        val contact = db.contactDao().getById(contactId) ?: throw NotFoundException("شخص پیدا نشد.")
        val balance = contact.currentBalance.safeAdd(delta)
        db.contactDao().updateBalance(contact.id, balance, now)
        db.contactLedgerDao().insert(ContactLedgerEntity(
            contactId = contact.id, transactionId = txId,
            debit = if (delta > 0) delta else 0,
            credit = if (delta < 0) -delta else 0,
            balanceAfter = balance, createdAt = now,
        ))
        return balance
    }

    private suspend fun requireAccountInBusiness(accountId: Long, businessId: Long): AccountEntity {
        val account = db.accountDao().getById(accountId) ?: throw NotFoundException("حساب پیدا نشد.")
        requireValid(account.businessId == businessId, "حساب متعلق به این کسب‌وکار نیست.")
        return account
    }

    private suspend fun requireContactInBusiness(contactId: Long, businessId: Long): ContactEntity {
        val contact = db.contactDao().getById(contactId) ?: throw NotFoundException("شخص پیدا نشد.")
        requireValid(contact.businessId == businessId, "شخص متعلق به این کسب‌وکار نیست.")
        return contact
    }

    private suspend fun maybeReminder(businessId: Long, contactId: Long?, amount: Toman, dueDate: Long?, type: String, description: String, now: Long) {
        if (dueDate == null || amount <= 0) return
        db.reminderDao().insert(ReminderEntity(businessId = businessId, contactId = contactId, amount = amount, dueDate = dueDate, description = description, reminderType = type, createdAt = now))
    }

    private suspend fun maybeLowStockReminder(product: ProductEntity, now: Long) {
        if (product.stockQuantity > product.minimumStock) return
        val existing = db.reminderDao().findPendingProduct(product.businessId, "LOW_STOCK", product.id)
        if (existing == null) {
            db.reminderDao().insert(ReminderEntity(businessId = product.businessId, productId = product.id, dueDate = now, description = "موجودی «${product.name}» کم است.", reminderType = "LOW_STOCK", createdAt = now))
        }
    }

    override suspend fun recordSale(command: SaleCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            val businessId = requireBusinessId()
            val productIds = command.lines.map { it.productId }.distinct()
            val productEntities = db.productDao().getByIds(productIds)
            requireValid(productEntities.size == productIds.size && productEntities.all { it.businessId == businessId }, "کالای انتخاب‌شده پیدا نشد.")
            val products = productEntities.associate { it.id to it.toSnapshot() }
            val result = AccountingEngine.computeSale(command, products)
            val now = System.currentTimeMillis()
            if (result.remainingAmount > 0) {
                val contact = requireContactInBusiness(command.contactId!!, businessId)
                requireValid(contact.contactType != ContactType.SUPPLIER.name, "شخص انتخاب‌شده مشتری نیست.")
            }
            if (result.paidAmount > 0) requireAccountInBusiness(command.accountId!!, businessId)
            val txId = insertTx(businessId, command.operationId, result.type, result.netAmount, result.paidAmount, result.remainingAmount, command.occurredAt, now,
                contactId = command.contactId, accountId = if (result.paidAmount > 0) command.accountId else null,
                paymentMethod = if (result.paidAmount > 0) command.paymentMethod else null, description = command.description)
            val saleId = db.saleDao().insertSale(SaleEntity(transactionId = txId, contactId = command.contactId, grossAmount = result.grossAmount, discountAmount = result.discountAmount, netAmount = result.netAmount, paidAmount = result.paidAmount, remainingAmount = result.remainingAmount, createdAt = now))
            db.saleDao().insertItems(result.lines.map { line -> SaleItemEntity(saleId = saleId, productId = line.productId, quantity = line.quantity, unitPrice = line.unitPrice, unitCostSnapshot = line.unitCostSnapshot, discountAmount = line.discount, totalAmount = line.totalAmount, cogs = line.cogs) })
            result.lines.forEach { line ->
                val product = db.productDao().getById(line.productId)!!
                val newQty = product.stockQuantity.safeSub(line.quantity)
                val newValue = product.inventoryValue.safeSub(line.cogs)
                requireValid(newQty >= 0 && newValue >= 0, "موجودی کالا کافی نیست.")
                db.productDao().updateStock(product.id, newQty, newValue, now)
                db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.SALE.name, quantity = -line.quantity, unitCost = line.unitCostSnapshot, valueDelta = -line.cogs, referenceTransactionId = txId, reason = "فروش", createdAt = now))
                maybeLowStockReminder(product.copy(stockQuantity = newQty), now)
            }
            if (result.paidAmount > 0) changeAccount(command.accountId!!, result.paidAmount, txId, now)
            if (result.remainingAmount > 0) changeContact(command.contactId!!, result.remainingAmount, txId, now)
            maybeReminder(businessId, command.contactId, result.remainingAmount, command.dueDate, "CUSTOMER_DEBT", "سررسید بدهی مشتری", now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun recordPurchase(command: PurchaseCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            val businessId = requireBusinessId()
            val supplier = requireContactInBusiness(command.supplierId, businessId)
            requireValid(supplier.contactType != ContactType.CUSTOMER.name, "شخص انتخاب‌شده تأمین‌کننده نیست.")
            val productIds = command.lines.map { it.productId }.distinct()
            val productEntities = db.productDao().getByIds(productIds)
            requireValid(productEntities.size == productIds.size && productEntities.all { it.businessId == businessId }, "کالای انتخاب‌شده پیدا نشد.")
            val result = AccountingEngine.computePurchase(command)
            val now = System.currentTimeMillis()
            if (result.paidAmount > 0) requireAccountInBusiness(command.accountId!!, businessId)
            val txId = insertTx(businessId, command.operationId, TransactionType.PURCHASE, result.totalAmount, result.paidAmount, result.remainingAmount, command.occurredAt, now,
                contactId = command.supplierId, accountId = if (result.paidAmount > 0) command.accountId else null,
                paymentMethod = if (result.paidAmount > 0) command.paymentMethod else null, description = command.description)
            val purchaseId = db.purchaseDao().insertPurchase(PurchaseEntity(transactionId = txId, supplierId = command.supplierId, grossAmount = result.grossAmount, discountAmount = result.discountAmount, netAmount = result.goodsNet, directCost = result.directCost, totalAmount = result.totalAmount, paidAmount = result.paidAmount, remainingAmount = result.remainingAmount, createdAt = now))
            db.purchaseDao().insertItems(result.lines.map { line -> PurchaseItemEntity(purchaseId = purchaseId, productId = line.productId, quantity = line.quantity, unitPrice = line.unitPrice, discountAmount = line.discount, totalAmount = line.goodsNet, allocatedCost = line.allocatedCost) })
            result.lines.forEach { line ->
                val product = db.productDao().getById(line.productId)!!
                val newQty = product.stockQuantity.safeAdd(line.quantity)
                val newValue = product.inventoryValue.safeAdd(line.allocatedCost)
                db.productDao().updateStock(product.id, newQty, newValue, now)
                db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.PURCHASE.name, quantity = line.quantity, unitCost = line.allocatedCost / line.quantity, valueDelta = line.allocatedCost, referenceTransactionId = txId, reason = "خرید کالا", createdAt = now))
            }
            if (result.paidAmount > 0) changeAccount(command.accountId!!, -result.paidAmount, txId, now)
            if (result.remainingAmount > 0) changeContact(command.supplierId, -result.remainingAmount, txId, now)
            maybeReminder(businessId, command.supplierId, result.remainingAmount, command.dueDate, "SUPPLIER_PAYMENT", "سررسید پرداخت به تأمین‌کننده", now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun recordExpense(command: ExpenseCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            val businessId = requireBusinessId()
            val result = AccountingEngine.computeExpense(command)
            val now = System.currentTimeMillis()
            if (result.paidAmount > 0) requireAccountInBusiness(command.accountId!!, businessId)
            if (result.remainingAmount > 0) requireContactInBusiness(command.contactId!!, businessId)
            val txId = insertTx(businessId, command.operationId, TransactionType.EXPENSE, result.amount, result.paidAmount, result.remainingAmount, command.occurredAt, now,
                contactId = command.contactId, accountId = if (result.paidAmount > 0) command.accountId else null,
                paymentMethod = if (result.paidAmount > 0) command.paymentMethod else null, description = command.description)
            db.expenseDao().insert(ExpenseEntity(transactionId = txId, title = command.title.trim(), category = command.category.name, amount = result.amount, paidAmount = result.paidAmount, remainingAmount = result.remainingAmount, paymentMethod = command.paymentMethod.name, invoicePath = command.invoicePath, createdAt = now))
            if (result.paidAmount > 0) changeAccount(command.accountId!!, -result.paidAmount, txId, now)
            if (result.remainingAmount > 0) changeContact(command.contactId!!, -result.remainingAmount, txId, now)
            maybeReminder(businessId, command.contactId, result.remainingAmount, command.dueDate, "SUPPLIER_PAYMENT", "سررسید هزینهٔ پرداخت‌نشده", now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun recordPayment(command: PaymentCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            AccountingEngine.validatePayment(command)
            val businessId = requireBusinessId()
            val contact = requireContactInBusiness(command.contactId, businessId)
            requireAccountInBusiness(command.accountId, businessId)
            if (command.direction == PaymentDirection.RECEIVE) {
                requireValid(contact.currentBalance > 0, "این شخص بدهکار نیست.")
                requireValid(command.amount <= contact.currentBalance, "مبلغ دریافتی بیشتر از بدهی شخص است.")
            } else {
                requireValid(contact.currentBalance < 0, "این شخص بستانکار نیست.")
                requireValid(command.amount <= -contact.currentBalance, "مبلغ پرداختی بیشتر از طلب شخص است.")
            }
            val now = System.currentTimeMillis()
            val type = if (command.direction == PaymentDirection.RECEIVE) TransactionType.RECEIVE_RECEIVABLE else TransactionType.PAY_PAYABLE
            val txId = insertTx(businessId, command.operationId, type, command.amount, command.amount, 0, command.occurredAt, now,
                contactId = contact.id, accountId = command.accountId, paymentMethod = command.paymentMethod, description = command.description)
            val accountDelta = if (command.direction == PaymentDirection.RECEIVE) command.amount else -command.amount
            val contactDelta = if (command.direction == PaymentDirection.RECEIVE) -command.amount else command.amount
            changeAccount(command.accountId, accountDelta, txId, now)
            changeContact(contact.id, contactDelta, txId, now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun recordTransfer(command: TransferCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            AccountingEngine.validateTransfer(command)
            val businessId = requireBusinessId()
            val from = requireAccountInBusiness(command.fromAccountId, businessId)
            val to = requireAccountInBusiness(command.toAccountId, businessId)
            val now = System.currentTimeMillis()
            val txId = insertTx(businessId, command.operationId, TransactionType.TRANSFER, command.amount, command.amount, 0, command.occurredAt, now,
                accountId = from.id, counterAccountId = to.id, description = "انتقال به ${to.title}: ${command.description}")
            changeAccount(from.id, -command.amount, txId, now)
            changeAccount(to.id, command.amount, txId, now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun adjustAccount(command: AccountAdjustmentCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            AccountingEngine.validateAccountAdjustment(command)
            val businessId = requireBusinessId()
            requireAccountInBusiness(command.accountId, businessId)
            val now = System.currentTimeMillis()
            val txId = insertTx(businessId, command.operationId, TransactionType.ACCOUNT_ADJUSTMENT, kotlin.math.abs(command.delta), 0, 0, command.occurredAt, now,
                accountId = command.accountId, description = command.reason)
            changeAccount(command.accountId, command.delta, txId, now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun adjustContact(command: ContactAdjustmentCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            requireValid(command.operationId.isNotBlank(), "شناسهٔ عملیات الزامی است.")
            requireValid(command.reason.isNotBlank(), "دلیل اصلاح الزامی است.")
            requireValid(command.delta != 0L, "مبلغ اصلاح باید غیرصفر باشد.")
            val businessId = requireBusinessId()
            requireContactInBusiness(command.contactId, businessId)
            val now = System.currentTimeMillis()
            val txId = insertTx(businessId, command.operationId, TransactionType.CONTACT_ADJUSTMENT, kotlin.math.abs(command.delta), 0, 0, command.occurredAt, now,
                contactId = command.contactId, description = command.reason)
            changeContact(command.contactId, command.delta, txId, now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun adjustStock(command: StockAdjustmentCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            val businessId = requireBusinessId()
            val product = db.productDao().getById(command.productId) ?: throw NotFoundException("کالا پیدا نشد.")
            requireValid(product.businessId == businessId, "کالا متعلق به این کسب‌وکار نیست.")
            AccountingEngine.validateStockAdjustment(command, product.toSnapshot())
            val now = System.currentTimeMillis()
            val unitCost = if (command.delta > 0) {
                command.unitCost ?: product.toSnapshot().let { if (it.stockQuantity > 0) it.inventoryValue / it.stockQuantity else it.purchasePrice }
            } else {
                if (product.stockQuantity > 0) product.inventoryValue / product.stockQuantity else 0L
            }
            val valueDelta = command.delta.safeMul(unitCost)
            val newQty = product.stockQuantity.safeAdd(command.delta)
            val newValue = product.inventoryValue.safeAdd(valueDelta)
            requireValid(newQty >= 0 && newValue >= 0, "اصلاح موجودی معتبر نیست.")
            val txId = insertTx(businessId, command.operationId, TransactionType.STOCK_ADJUSTMENT, kotlin.math.abs(valueDelta), 0, 0, command.occurredAt, now, description = command.reason)
            db.productDao().updateStock(product.id, newQty, newValue, now)
            db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.ADJUSTMENT.name, quantity = command.delta, unitCost = unitCost, valueDelta = valueDelta, referenceTransactionId = txId, reason = command.reason, createdAt = now))
            maybeLowStockReminder(product.copy(stockQuantity = newQty), now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun recordOpeningStock(command: StockAdjustmentCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            requireValid(command.delta > 0, "موجودی اولیه باید مثبت باشد.")
            requireValid(command.unitCost != null, "بهای واحد موجودی اولیه الزامی است.")
            val businessId = requireBusinessId()
            val product = db.productDao().getById(command.productId) ?: throw NotFoundException("کالا پیدا نشد.")
            requireValid(product.businessId == businessId, "کالا متعلق به این کسب‌وکار نیست.")
            AccountingEngine.validateStockAdjustment(command, product.toSnapshot())
            val now = System.currentTimeMillis()
            val valueDelta = command.delta.safeMul(command.unitCost!!)
            val txId = insertTx(businessId, command.operationId, TransactionType.OPENING, valueDelta, 0, 0, command.occurredAt, now, description = command.reason)
            db.productDao().updateStock(product.id, product.stockQuantity.safeAdd(command.delta), product.inventoryValue.safeAdd(valueDelta), now)
            db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.OPENING.name, quantity = command.delta, unitCost = command.unitCost!!, valueDelta = valueDelta, referenceTransactionId = txId, reason = command.reason, createdAt = now))
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun recordSaleReturn(command: SaleReturnCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            val businessId = requireBusinessId()
            val original = db.transactionDao().getById(command.originalTransactionId) ?: throw NotFoundException("فروش پیدا نشد.")
            requireValid(original.businessId == businessId, "فروش متعلق به این کسب‌وکار نیست.")
            requireValid(original.status == TransactionStatus.POSTED.name, "فروش اصلاح‌شده قابل مرجوعی نیست.")
            requireValid(original.transactionType in setOf(TransactionType.CASH_SALE.name, TransactionType.CREDIT_SALE.name, TransactionType.MIXED_SALE.name), "فقط فروش قابل مرجوعی است.")
            val items = db.saleDao().getItemsByTransaction(original.id).map { SaleItemSnapshot(it.id, it.productId, it.quantity, it.unitPrice, it.unitCostSnapshot, it.discountAmount, it.totalAmount, it.returnedQuantity, it.returnedDiscountAmount) }
            val result = AccountingEngine.computeSaleReturn(command, items)
            val now = System.currentTimeMillis()
            if (result.receivableReduction > 0) {
                requireValid(original.contactId != null, "برای کاهش بدهی، مشتری فروش الزامی است.")
                val contact = requireContactInBusiness(original.contactId!!, businessId)
                requireValid(contact.currentBalance >= result.receivableReduction, "بدهی ثبت‌شدهٔ مشتری کمتر از مبلغ مرجوعی است؛ مبلغ بازپرداخت را اصلاح کنید.")
            }
            if (result.refundAmount > 0) requireAccountInBusiness(command.accountId!!, businessId)
            val txId = insertTx(businessId, command.operationId, TransactionType.SALES_RETURN, result.netAmount, result.refundAmount, result.receivableReduction, command.occurredAt, now,
                contactId = original.contactId, accountId = if (result.refundAmount > 0) command.accountId else null,
                paymentMethod = if (result.refundAmount > 0) command.paymentMethod else null,
                description = command.reason, relatedTransactionId = original.id)
            val returnId = db.salesReturnDao().insertReturn(SalesReturnEntity(transactionId = txId, originalTransactionId = original.id, grossAmount = result.grossAmount, discountAmount = result.discountAmount, netAmount = result.netAmount, refundAmount = result.refundAmount, receivableReduction = result.receivableReduction, cogsReversed = result.cogsReversed, reason = command.reason, createdAt = now))
            db.salesReturnDao().insertItems(result.lines.map { line -> SalesReturnItemEntity(returnId = returnId, saleItemId = line.saleItemId, quantity = line.quantity, unitPrice = line.unitPrice, discountShare = line.discountShare, netAmount = line.netAmount, cogsReversed = line.cogsReversed, sellable = line.sellable) })
            result.lines.forEach { line ->
                val item = db.saleDao().getItemsByTransaction(original.id).first { it.id == line.saleItemId }
                db.saleDao().updateReturned(item.id, item.returnedQuantity + line.quantity, item.returnedDiscountAmount + line.discountShare)
                if (line.sellable) {
                    val product = db.productDao().getById(line.productId)!!
                    db.productDao().updateStock(product.id, product.stockQuantity.safeAdd(line.quantity), product.inventoryValue.safeAdd(line.cogsReversed), now)
                    db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.SALES_RETURN.name, quantity = line.quantity, unitCost = line.unitCostSnapshot, valueDelta = line.cogsReversed, referenceTransactionId = txId, reason = "برگشت فروش سالم", createdAt = now))
                }
            }
            if (result.refundAmount > 0) changeAccount(command.accountId!!, -result.refundAmount, txId, now)
            if (result.receivableReduction > 0) changeContact(original.contactId!!, -result.receivableReduction, txId, now)
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun reverseTransaction(command: ReverseCommand): OperationResult<TransactionHeader> = runOperation {
        withIdempotency(command.operationId) {
            requireValid(command.reason.isNotBlank(), "دلیل اصلاح الزامی است.")
            val businessId = requireBusinessId()
            val original = db.transactionDao().getById(command.originalTransactionId) ?: throw NotFoundException("تراکنش پیدا نشد.")
            requireValid(original.businessId == businessId, "تراکنش متعلق به این کسب‌وکار نیست.")
            requireValid(original.status == TransactionStatus.POSTED.name, "این تراکنش قبلاً اصلاح شده است.")
            requireValid(original.transactionType != TransactionType.REVERSAL.name && original.transactionType != TransactionType.SALES_RETURN.name, "برای این نوع تراکنش از مسیر اصلاح مخصوص استفاده کنید.")
            if (original.transactionType in setOf(TransactionType.CASH_SALE.name, TransactionType.CREDIT_SALE.name, TransactionType.MIXED_SALE.name)) {
                requireValid(db.salesReturnDao().getByOriginal(original.id).isEmpty(), "فروش دارای مرجوعی است؛ ابتدا مرجوعی‌ها را اصلاح کنید.")
            }
            val now = System.currentTimeMillis()
            val txId = insertTx(businessId, command.operationId, TransactionType.REVERSAL, original.amount, 0, 0, command.occurredAt, now,
                contactId = original.contactId, accountId = original.accountId, counterAccountId = original.counterAccountId,
                description = "اصلاح ${original.transactionCode}: ${command.reason}", relatedTransactionId = original.id)
            when (TransactionType.valueOf(original.transactionType)) {
                TransactionType.CASH_SALE, TransactionType.CREDIT_SALE, TransactionType.MIXED_SALE -> {
                    db.saleDao().getItemsByTransaction(original.id).forEach { item ->
                        val product = db.productDao().getById(item.productId)!!
                        db.productDao().updateStock(product.id, product.stockQuantity.safeAdd(item.quantity), product.inventoryValue.safeAdd(item.cogs), now)
                        db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.ADJUSTMENT.name, quantity = item.quantity, unitCost = item.unitCostSnapshot, valueDelta = item.cogs, referenceTransactionId = txId, reason = "برگشت اصلاح فروش", createdAt = now))
                    }
                    if (original.paidAmount > 0) changeAccount(original.accountId!!, -original.paidAmount, txId, now)
                    if (original.remainingAmount > 0) changeContact(original.contactId!!, -original.remainingAmount, txId, now)
                }
                TransactionType.PURCHASE -> {
                    db.purchaseDao().getItemsByTransaction(original.id).forEach { item ->
                        val product = db.productDao().getById(item.productId)!!
                        requireValid(product.stockQuantity >= item.quantity && product.inventoryValue >= item.allocatedCost, "موجودی کالا برای اصلاح خرید کافی نیست؛ ابتدا موجودی را اصلاح کنید.")
                        db.productDao().updateStock(product.id, product.stockQuantity.safeSub(item.quantity), product.inventoryValue.safeSub(item.allocatedCost), now)
                        db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.ADJUSTMENT.name, quantity = -item.quantity, unitCost = item.allocatedCost / item.quantity, valueDelta = -item.allocatedCost, referenceTransactionId = txId, reason = "اصلاح خرید", createdAt = now))
                    }
                    if (original.paidAmount > 0) changeAccount(original.accountId!!, original.paidAmount, txId, now)
                    if (original.remainingAmount > 0) changeContact(original.contactId!!, original.remainingAmount, txId, now)
                }
                TransactionType.EXPENSE -> {
                    if (original.paidAmount > 0) changeAccount(original.accountId!!, original.paidAmount, txId, now)
                    if (original.remainingAmount > 0) changeContact(original.contactId!!, original.remainingAmount, txId, now)
                }
                TransactionType.RECEIVE_RECEIVABLE -> {
                    changeAccount(original.accountId!!, -original.amount, txId, now)
                    changeContact(original.contactId!!, original.amount, txId, now)
                }
                TransactionType.PAY_PAYABLE -> {
                    changeAccount(original.accountId!!, original.amount, txId, now)
                    changeContact(original.contactId!!, -original.amount, txId, now)
                }
                TransactionType.TRANSFER -> {
                    changeAccount(original.accountId!!, original.amount, txId, now)
                    changeAccount(original.counterAccountId!!, -original.amount, txId, now)
                }
                TransactionType.ACCOUNT_ADJUSTMENT -> {
                    val delta = db.accountLedgerDao().getByTransaction(original.id).sumOf { it.delta }
                    changeAccount(original.accountId!!, -delta, txId, now)
                }
                TransactionType.CONTACT_ADJUSTMENT -> {
                    val delta = db.contactLedgerDao().getByTransaction(original.id).sumOf { it.debit - it.credit }
                    changeContact(original.contactId!!, -delta, txId, now)
                }
                TransactionType.STOCK_ADJUSTMENT, TransactionType.OPENING -> {
                    db.stockMovementDao().getByTransaction(original.id).forEach { movement ->
                        val product = db.productDao().getById(movement.productId)!!
                        requireValid(product.stockQuantity >= movement.quantity && product.inventoryValue >= movement.valueDelta, "موجودی برای اصلاح کافی نیست.")
                        db.productDao().updateStock(product.id, product.stockQuantity.safeSub(movement.quantity), product.inventoryValue.safeSub(movement.valueDelta), now)
                        db.stockMovementDao().insert(StockMovementEntity(productId = product.id, movementType = StockMovementType.ADJUSTMENT.name, quantity = -movement.quantity, unitCost = movement.unitCost, valueDelta = -movement.valueDelta, referenceTransactionId = txId, reason = "اصلاح موجودی", createdAt = now))
                    }
                }
                else -> throw ValidationException("این تراکنش قابل اصلاح نیست.")
            }
            db.transactionDao().update(original.copy(status = TransactionStatus.REVERSED.name, updatedAt = now))
            db.transactionDao().getById(txId)!!.toDomain()
        }
    }

    override suspend fun getTransaction(id: Long): TransactionHeader? = db.transactionDao().getById(id)?.toDomain()
    override suspend fun getSaleItems(transactionId: Long): List<SaleItemSnapshot> = db.saleDao().getItemsByTransaction(transactionId).map { SaleItemSnapshot(it.id, it.productId, it.quantity, it.unitPrice, it.unitCostSnapshot, it.discountAmount, it.totalAmount, it.returnedQuantity, it.returnedDiscountAmount) }
    override suspend fun getExpense(transactionId: Long): ExpenseDetail? = db.expenseDao().getByTransaction(transactionId)?.let { ExpenseDetail(it.title, ExpenseCategory.valueOf(it.category), it.invoicePath) }
    override fun observeTransactions(businessId: Long): Flow<List<TransactionHeader>> = db.transactionDao().observeByBusiness(businessId).map { list -> list.map { it.toDomain() } }
    override fun observeRecent(businessId: Long, limit: Int): Flow<List<TransactionHeader>> = db.transactionDao().observeRecent(businessId, limit).map { list -> list.map { it.toDomain() } }
    override fun observeContactTransactions(contactId: Long): Flow<List<TransactionHeader>> = db.transactionDao().observeByContact(contactId).map { list -> list.map { it.toDomain() } }
    override suspend fun getTransactionsInRange(businessId: Long, from: Long, to: Long): List<TransactionHeader> = db.transactionDao().getInRange(businessId, from, to).map { it.toDomain() }
}
