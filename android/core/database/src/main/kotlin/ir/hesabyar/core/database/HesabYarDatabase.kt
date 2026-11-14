package ir.hesabyar.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BusinessEntity::class,
        AccountEntity::class,
        ContactEntity::class,
        ProductCategoryEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        ContactLedgerEntity::class,
        StockMovementEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        ExpenseEntity::class,
        ReminderEntity::class,
        BackupEntity::class,
        SubscriptionEntity::class,
        AccountLedgerEntity::class,
        SalesReturnEntity::class,
        SalesReturnItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HesabYarDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun accountDao(): AccountDao
    abstract fun contactDao(): ContactDao
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun saleDao(): SaleDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun contactLedgerDao(): ContactLedgerDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun reminderDao(): ReminderDao
    abstract fun backupDao(): BackupDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun accountLedgerDao(): AccountLedgerDao
    abstract fun salesReturnDao(): SalesReturnDao
    abstract fun productCategoryDao(): ProductCategoryDao
}
