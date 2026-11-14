package ir.hesabyar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Insert suspend fun insert(entity: BusinessEntity): Long
    @Update suspend fun update(entity: BusinessEntity)
    @Query("SELECT * FROM businesses ORDER BY id LIMIT 1") fun observeFirst(): Flow<BusinessEntity?>
    @Query("SELECT * FROM businesses WHERE id = :id") suspend fun getById(id: Long): BusinessEntity?
}

@Dao
interface AccountDao {
    @Insert suspend fun insert(entity: AccountEntity): Long
    @Update suspend fun update(entity: AccountEntity)
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun getById(id: Long): AccountEntity?
    @Query("SELECT * FROM accounts WHERE business_id = :businessId ORDER BY id") fun observeByBusiness(businessId: Long): Flow<List<AccountEntity>>
    @Query("SELECT * FROM accounts WHERE business_id = :businessId ORDER BY id") suspend fun getByBusiness(businessId: Long): List<AccountEntity>
    @Query("UPDATE accounts SET current_balance = :balance, updated_at = :now WHERE id = :id") suspend fun updateBalance(id: Long, balance: Long, now: Long)
}

@Dao
interface ContactDao {
    @Insert suspend fun insert(entity: ContactEntity): Long
    @Update suspend fun update(entity: ContactEntity)
    @Query("SELECT * FROM contacts WHERE id = :id") suspend fun getById(id: Long): ContactEntity?
    @Query("SELECT * FROM contacts WHERE business_id = :businessId ORDER BY name") fun observeByBusiness(businessId: Long): Flow<List<ContactEntity>>
    @Query("SELECT * FROM contacts WHERE business_id = :businessId AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%') ORDER BY name") fun search(businessId: Long, query: String): Flow<List<ContactEntity>>
    @Query("UPDATE contacts SET current_balance = :balance, updated_at = :now WHERE id = :id") suspend fun updateBalance(id: Long, balance: Long, now: Long)
    @Query("SELECT COUNT(*) FROM contacts WHERE business_id = :businessId") suspend fun count(businessId: Long): Int
}

@Dao
interface ProductDao {
    @Insert suspend fun insert(entity: ProductEntity): Long
    @Update suspend fun update(entity: ProductEntity)
    @Query("SELECT * FROM products WHERE id = :id") suspend fun getById(id: Long): ProductEntity?
    @Query("SELECT * FROM products WHERE id IN (:ids)") suspend fun getByIds(ids: List<Long>): List<ProductEntity>
    @Query("SELECT * FROM products WHERE business_id = :businessId ORDER BY name") fun observeByBusiness(businessId: Long): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE business_id = :businessId AND (name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%') ORDER BY name") fun search(businessId: Long, query: String): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE business_id = :businessId AND stock_quantity <= minimum_stock ORDER BY stock_quantity") fun observeLowStock(businessId: Long): Flow<List<ProductEntity>>
    @Query("UPDATE products SET stock_quantity = :quantity, inventory_value = :value, updated_at = :now WHERE id = :id") suspend fun updateStock(id: Long, quantity: Long, value: Long, now: Long)
    @Query("SELECT COUNT(*) FROM products WHERE business_id = :businessId") suspend fun count(businessId: Long): Int
}

@Dao
interface TransactionDao {
    @Insert suspend fun insert(entity: TransactionEntity): Long
    @Update suspend fun update(entity: TransactionEntity)
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun getById(id: Long): TransactionEntity?
    @Query("SELECT * FROM transactions WHERE operation_id = :operationId LIMIT 1") suspend fun findByOperationId(operationId: String): TransactionEntity?
    @Query("UPDATE transactions SET transaction_code = :code, updated_at = :now WHERE id = :id") suspend fun updateCode(id: Long, code: String, now: Long)
    @Query("SELECT * FROM transactions WHERE business_id = :businessId ORDER BY transaction_date DESC, id DESC") fun observeByBusiness(businessId: Long): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE business_id = :businessId ORDER BY transaction_date DESC, id DESC LIMIT :limit") fun observeRecent(businessId: Long, limit: Int): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE business_id = :businessId AND transaction_date BETWEEN :from AND :to AND status = 'POSTED' ORDER BY transaction_date DESC, id DESC") suspend fun getInRange(businessId: Long, from: Long, to: Long): List<TransactionEntity>
    @Query("SELECT * FROM transactions WHERE contact_id = :contactId ORDER BY transaction_date DESC, id DESC") fun observeByContact(contactId: Long): Flow<List<TransactionEntity>>
    @Query("SELECT COUNT(*) FROM transactions WHERE business_id = :businessId") suspend fun count(businessId: Long): Int
}

@Dao
interface SaleDao {
    @Insert suspend fun insertSale(entity: SaleEntity): Long
    @Insert suspend fun insertItems(items: List<SaleItemEntity>)
    @Query("SELECT * FROM sales WHERE transaction_id = :transactionId") suspend fun getByTransaction(transactionId: Long): SaleEntity?
    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId") suspend fun getItems(saleId: Long): List<SaleItemEntity>
    @Query("UPDATE sale_items SET returned_quantity = :returned, returned_discount_amount = :returnedDiscount WHERE id = :id") suspend fun updateReturned(id: Long, returned: Long, returnedDiscount: Long)
    @Query("SELECT i.* FROM sale_items i INNER JOIN sales s ON s.id = i.sale_id WHERE s.transaction_id = :transactionId") suspend fun getItemsByTransaction(transactionId: Long): List<SaleItemEntity>
    @Query("SELECT * FROM sale_items WHERE id = :id") suspend fun getItemById(id: Long): SaleItemEntity?
    @Query("SELECT s.* FROM sales s INNER JOIN transactions t ON t.id = s.transaction_id WHERE t.business_id = :businessId AND t.transaction_date BETWEEN :from AND :to AND t.status = 'POSTED' AND t.transaction_type IN ('CASH_SALE','CREDIT_SALE','MIXED_SALE')") suspend fun getSalesInRange(businessId: Long, from: Long, to: Long): List<SaleEntity>
}

@Dao
interface PurchaseDao {
    @Insert suspend fun insertPurchase(entity: PurchaseEntity): Long
    @Insert suspend fun insertItems(items: List<PurchaseItemEntity>)
    @Query("SELECT * FROM purchases WHERE transaction_id = :transactionId") suspend fun getByTransaction(transactionId: Long): PurchaseEntity?
    @Query("SELECT * FROM purchase_items WHERE purchase_id = :purchaseId") suspend fun getItems(purchaseId: Long): List<PurchaseItemEntity>
    @Query("SELECT i.* FROM purchase_items i INNER JOIN purchases p ON p.id = i.purchase_id WHERE p.transaction_id = :transactionId") suspend fun getItemsByTransaction(transactionId: Long): List<PurchaseItemEntity>
}

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(entity: ExpenseEntity): Long
    @Query("SELECT * FROM expenses WHERE transaction_id = :transactionId") suspend fun getByTransaction(transactionId: Long): ExpenseEntity?
    @Query("SELECT e.* FROM expenses e INNER JOIN transactions t ON t.id = e.transaction_id WHERE t.business_id = :businessId AND t.transaction_date BETWEEN :from AND :to AND t.status = 'POSTED'") suspend fun getInRange(businessId: Long, from: Long, to: Long): List<ExpenseEntity>
}

@Dao
interface ContactLedgerDao {
    @Insert suspend fun insert(entity: ContactLedgerEntity): Long
    @Query("SELECT * FROM contact_ledger WHERE contact_id = :contactId ORDER BY created_at DESC, id DESC") fun observeByContact(contactId: Long): Flow<List<ContactLedgerEntity>>
    @Query("SELECT * FROM contact_ledger WHERE contact_id = :contactId ORDER BY created_at, id") suspend fun getByContact(contactId: Long): List<ContactLedgerEntity>
    @Query("SELECT * FROM contact_ledger WHERE transaction_id = :transactionId") suspend fun getByTransaction(transactionId: Long): List<ContactLedgerEntity>
}

@Dao
interface StockMovementDao {
    @Insert suspend fun insert(entity: StockMovementEntity): Long
    @Query("SELECT * FROM stock_movements WHERE product_id = :productId ORDER BY created_at DESC, id DESC") fun observeByProduct(productId: Long): Flow<List<StockMovementEntity>>
    @Query("SELECT * FROM stock_movements WHERE reference_transaction_id = :transactionId") suspend fun getByTransaction(transactionId: Long): List<StockMovementEntity>
}

@Dao
interface ReminderDao {
    @Insert suspend fun insert(entity: ReminderEntity): Long
    @Update suspend fun update(entity: ReminderEntity)
    @Query("SELECT * FROM reminders WHERE business_id = :businessId ORDER BY due_date") fun observeByBusiness(businessId: Long): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE status = 'PENDING' AND due_date <= :until") suspend fun getDue(until: Long): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun getById(id: Long): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE business_id = :businessId AND reminder_type = :type AND product_id = :productId AND status != 'DONE' LIMIT 1") suspend fun findPendingProduct(businessId: Long, type: String, productId: Long): ReminderEntity?
}

@Dao
interface BackupDao {
    @Insert suspend fun insert(entity: BackupEntity): Long
    @Query("SELECT * FROM backups WHERE business_id = :businessId ORDER BY backup_date DESC") fun observeByBusiness(businessId: Long): Flow<List<BackupEntity>>
}

@Dao
interface SubscriptionDao {
    @Insert suspend fun insert(entity: SubscriptionEntity): Long
    @Update suspend fun update(entity: SubscriptionEntity)
    @Query("SELECT * FROM subscriptions WHERE business_id = :businessId ORDER BY id DESC LIMIT 1") fun observeCurrent(businessId: Long): Flow<SubscriptionEntity?>
}

@Dao
interface AccountLedgerDao {
    @Insert suspend fun insert(entity: AccountLedgerEntity): Long
    @Query("SELECT * FROM account_ledger WHERE account_id = :accountId ORDER BY created_at DESC, id DESC") fun observeByAccount(accountId: Long): Flow<List<AccountLedgerEntity>>
    @Query("SELECT l.* FROM account_ledger l INNER JOIN transactions t ON t.id = l.transaction_id WHERE t.business_id = :businessId AND t.transaction_date <= :to ORDER BY t.transaction_date, l.id") suspend fun getUpTo(businessId: Long, to: Long): List<AccountLedgerEntity>
    @Query("SELECT l.* FROM account_ledger l INNER JOIN transactions t ON t.id = l.transaction_id WHERE t.business_id = :businessId AND t.transaction_date BETWEEN :from AND :to ORDER BY t.transaction_date, l.id") suspend fun getInRange(businessId: Long, from: Long, to: Long): List<AccountLedgerEntity>
    @Query("SELECT l.* FROM account_ledger l INNER JOIN transactions t ON t.id = l.transaction_id WHERE t.business_id = :businessId AND t.transaction_date < :from ORDER BY t.transaction_date, l.id") suspend fun getBefore(businessId: Long, from: Long): List<AccountLedgerEntity>
    @Query("SELECT * FROM account_ledger WHERE transaction_id = :transactionId") suspend fun getByTransaction(transactionId: Long): List<AccountLedgerEntity>
}

@Dao
interface SalesReturnDao {
    @Insert suspend fun insertReturn(entity: SalesReturnEntity): Long
    @Insert suspend fun insertItems(items: List<SalesReturnItemEntity>)
    @Query("SELECT * FROM sales_returns WHERE original_transaction_id = :originalTransactionId") suspend fun getByOriginal(originalTransactionId: Long): List<SalesReturnEntity>
    @Query("SELECT r.* FROM sales_returns r INNER JOIN transactions t ON t.id = r.transaction_id WHERE t.business_id = :businessId AND t.transaction_date BETWEEN :from AND :to AND t.status = 'POSTED'") suspend fun getInRange(businessId: Long, from: Long, to: Long): List<SalesReturnEntity>
    @Query("SELECT * FROM sales_return_items WHERE return_id = :returnId") suspend fun getItems(returnId: Long): List<SalesReturnItemEntity>
}

@Dao
interface ProductCategoryDao {
    @Insert suspend fun insert(entity: ProductCategoryEntity): Long
    @Query("SELECT * FROM product_categories WHERE business_id = :businessId ORDER BY name") fun observeByBusiness(businessId: Long): Flow<List<ProductCategoryEntity>>
}
