package ir.hesabyar.domain.repository

import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.common.Toman
import ir.hesabyar.domain.accounting.*
import ir.hesabyar.domain.model.*
import kotlinx.coroutines.flow.Flow

interface BusinessRepository {
    fun observeBusiness(): Flow<Business?>
    suspend fun getBusiness(): Business?
    suspend fun setup(command: SetupCommand): OperationResult<Business>
    suspend fun update(command: SetupCommand): OperationResult<Business>
}

interface AccountingRepository {
    suspend fun recordSale(command: SaleCommand): OperationResult<TransactionHeader>
    suspend fun recordPurchase(command: PurchaseCommand): OperationResult<TransactionHeader>
    suspend fun recordExpense(command: ExpenseCommand): OperationResult<TransactionHeader>
    suspend fun recordPayment(command: PaymentCommand): OperationResult<TransactionHeader>
    suspend fun recordTransfer(command: TransferCommand): OperationResult<TransactionHeader>
    suspend fun adjustAccount(command: AccountAdjustmentCommand): OperationResult<TransactionHeader>
    suspend fun adjustStock(command: StockAdjustmentCommand): OperationResult<TransactionHeader>
    suspend fun recordOpeningStock(command: StockAdjustmentCommand): OperationResult<TransactionHeader>
    suspend fun recordSaleReturn(command: SaleReturnCommand): OperationResult<TransactionHeader>
    suspend fun reverseTransaction(command: ReverseCommand): OperationResult<TransactionHeader>
    suspend fun adjustContact(command: ContactAdjustmentCommand): OperationResult<TransactionHeader>
    suspend fun getTransaction(id: Long): TransactionHeader?
    suspend fun getSaleItems(transactionId: Long): List<SaleItemSnapshot>
    suspend fun getExpense(transactionId: Long): ExpenseDetail?
    fun observeContactTransactions(contactId: Long): Flow<List<TransactionHeader>>
    fun observeTransactions(businessId: Long): Flow<List<TransactionHeader>>
    fun observeRecent(businessId: Long, limit: Int = 10): Flow<List<TransactionHeader>>
    suspend fun getTransactionsInRange(businessId: Long, from: Long, to: Long): List<TransactionHeader>
}

data class ExpenseDetail(val title: String, val category: ExpenseCategory, val invoicePath: String)

interface ContactRepository {
    fun observeLedger(contactId: Long): Flow<List<ContactLedgerEntry>>
    fun observeContacts(businessId: Long): Flow<List<Contact>>
    fun searchContacts(businessId: Long, query: String): Flow<List<Contact>>
    suspend fun getById(id: Long): Contact?
    suspend fun add(command: ContactCommand): OperationResult<Contact>
    suspend fun update(command: ContactCommand): OperationResult<Contact>
}

data class ContactLedgerEntry(
    val id: Long,
    val contactId: Long,
    val transactionId: Long,
    val debit: Toman,
    val credit: Toman,
    val balanceAfter: Toman,
    val createdAt: Long,
    val transactionCode: String,
    val transactionType: TransactionType,
)

interface ProductRepository {
    fun observeStockMovements(productId: Long): Flow<List<StockMovementEntry>>
    fun observeCategories(businessId: Long): Flow<List<ProductCategory>>
    suspend fun addCategory(businessId: Long, name: String): OperationResult<ProductCategory>
    fun observeProducts(businessId: Long): Flow<List<Product>>
    fun searchProducts(businessId: Long, query: String): Flow<List<Product>>
    fun observeLowStock(businessId: Long): Flow<List<Product>>
    suspend fun getById(id: Long): Product?
    suspend fun add(command: ProductCommand): OperationResult<Product>
    suspend fun update(command: ProductCommand): OperationResult<Product>
}

interface AccountRepository {
    fun observeAccounts(businessId: Long): Flow<List<Account>>
    suspend fun getById(id: Long): Account?
    fun observeStatement(accountId: Long): Flow<List<AccountLedgerEntry>>
}

data class AccountLedgerEntry(
    val id: Long,
    val accountId: Long,
    val transactionId: Long,
    val delta: Toman,
    val balanceAfter: Toman,
    val createdAt: Long,
    val transactionCode: String,
    val description: String,
)

interface ReportRepository {
    suspend fun dailyReport(businessId: Long, from: Long, to: Long): ReportSummary
    suspend fun monthlyReport(businessId: Long, from: Long, to: Long): ReportSummary
    suspend fun profitReport(businessId: Long, from: Long, to: Long): ReportSummary
    suspend fun cashReport(businessId: Long, from: Long, to: Long): CashReport
    suspend fun productReport(businessId: Long, from: Long, to: Long): List<ProductReportRow>
}

interface BackupRepository {
    suspend fun createBackup(): OperationResult<String>
    suspend fun restoreBackup(uri: String): OperationResult<Unit>
    suspend fun exportCsv(): OperationResult<String>
    suspend fun exportExcel(): OperationResult<String>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setPersianDigits(enabled: Boolean)
    suspend fun setStockAlerts(enabled: Boolean)
    suspend fun setAutomaticBackup(enabled: Boolean)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setFontScalePercent(percent: Int)
}

interface ReminderRepository {
    fun observeReminders(businessId: Long): Flow<List<Reminder>>
    suspend fun add(command: ReminderCommand): OperationResult<Reminder>
    suspend fun markDone(id: Long): OperationResult<Unit>
    suspend fun scheduleDueCheck()
}

data class ReminderCommand(
    val contactId: Long? = null,
    val productId: Long? = null,
    val amount: Toman = 0,
    val dueDate: Long,
    val description: String,
    val reminderType: String = "DEBT",
)

interface SubscriptionRepository {
    fun observeSubscription(businessId: Long): Flow<Subscription?>
    suspend fun ensureFreePlan(businessId: Long)
}

data class SetupCommand(
    val name: String,
    val ownerName: String = "",
    val phone: String = "",
    val businessType: BusinessType = BusinessType.OTHER,
    val city: String = "",
    val openingCash: Long = 0,
    val openingDate: Long,
    val openingReceivables: Long = 0,
    val openingPayables: Long = 0,
)

data class ContactCommand(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val contactType: ContactType = ContactType.CUSTOMER,
    val initialBalance: Long = 0,
    val description: String = "",
)

data class ProductCommand(
    val id: Long = 0,
    val name: String,
    val code: String = "",
    val categoryId: Long? = null,
    val purchasePrice: Long,
    val sellingPrice: Long,
    val openingStock: Long = 0,
    val minimumStock: Long = 0,
    val description: String = "",
)

data class StockMovementEntry(
    val id: Long,
    val productId: Long,
    val movementType: StockMovementType,
    val quantity: Long,
    val unitCost: Toman,
    val valueDelta: Toman,
    val referenceTransactionId: Long?,
    val reason: String,
    val createdAt: Long,
)

data class ProductCategory(val id: Long, val businessId: Long, val name: String)

enum class AppTheme { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val persianDigits: Boolean = true,
    val stockAlerts: Boolean = true,
    val automaticBackup: Boolean = false,
    val fontScalePercent: Int = 100,
    val notificationsEnabled: Boolean = true,
)
