package ir.hesabyar.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "owner_name") val ownerName: String = "",
    val phone: String = "",
    @ColumnInfo(name = "business_type") val businessType: String,
    val city: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["business_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("business_id")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val title: String,
    @ColumnInfo(name = "account_type") val accountType: String,
    @ColumnInfo(name = "initial_balance") val initialBalance: Long = 0,
    @ColumnInfo(name = "current_balance") val currentBalance: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

@Entity(
    tableName = "contacts",
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["business_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("business_id"), Index("name")],
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val name: String,
    val phone: String = "",
    @ColumnInfo(name = "contact_type") val contactType: String,
    @ColumnInfo(name = "initial_balance") val initialBalance: Long = 0,
    @ColumnInfo(name = "current_balance") val currentBalance: Long = 0,
    val description: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

@Entity(
    tableName = "product_categories",
    foreignKeys = [ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["business_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("business_id")],
)
data class ProductCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val name: String,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["business_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductCategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("business_id"), Index("name"), Index("code"), Index("category_id")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    val name: String,
    val code: String = "",
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "purchase_price") val purchasePrice: Long,
    @ColumnInfo(name = "selling_price") val sellingPrice: Long,
    @ColumnInfo(name = "stock_quantity") val stockQuantity: Long = 0,
    @ColumnInfo(name = "minimum_stock") val minimumStock: Long = 0,
    @ColumnInfo(name = "inventory_value") val inventoryValue: Long = 0,
    val description: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["business_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contact_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["counter_account_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("counter_account_id"), Index(value = ["operation_id"], unique = true), Index(value = ["transaction_code"], unique = true), Index("business_id"), Index("contact_id"), Index("account_id"), Index("transaction_date"), Index("transaction_type"), Index("status")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "operation_id") val operationId: String,
    @ColumnInfo(name = "transaction_code") val transactionCode: String,
    @ColumnInfo(name = "transaction_type") val transactionType: String,
    val amount: Long,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long,
    @ColumnInfo(name = "remaining_amount") val remainingAmount: Long,
    @ColumnInfo(name = "contact_id") val contactId: Long? = null,
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    @ColumnInfo(name = "counter_account_id") val counterAccountId: Long? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String? = null,
    @ColumnInfo(name = "transaction_date") val transactionDate: Long,
    val description: String = "",
    val status: String = "POSTED",
    @ColumnInfo(name = "related_transaction_id") val relatedTransactionId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)

@Entity(
    tableName = "contact_ledger",
    foreignKeys = [
        ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contact_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("contact_id"), Index("transaction_id")],
)
data class ContactLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_id") val contactId: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    val debit: Long = 0,
    val credit: Long = 0,
    @ColumnInfo(name = "balance_after") val balanceAfter: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["reference_transaction_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("product_id"), Index("reference_transaction_id"), Index("created_at")],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "movement_type") val movementType: String,
    val quantity: Long,
    @ColumnInfo(name = "unit_cost") val unitCost: Long = 0,
    @ColumnInfo(name = "value_delta") val valueDelta: Long = 0,
    @ColumnInfo(name = "reference_transaction_id") val referenceTransactionId: Long? = null,
    val reason: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contact_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["transaction_id"], unique = true), Index("contact_id")],
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "contact_id") val contactId: Long? = null,
    @ColumnInfo(name = "gross_amount") val grossAmount: Long,
    @ColumnInfo(name = "discount_amount") val discountAmount: Long,
    @ColumnInfo(name = "net_amount") val netAmount: Long,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long,
    @ColumnInfo(name = "remaining_amount") val remainingAmount: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["sale_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("sale_id"), Index("product_id")],
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sale_id") val saleId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val quantity: Long,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    @ColumnInfo(name = "unit_cost_snapshot") val unitCostSnapshot: Long,
    @ColumnInfo(name = "discount_amount") val discountAmount: Long = 0,
    @ColumnInfo(name = "total_amount") val totalAmount: Long,
    val cogs: Long = 0,
    @ColumnInfo(name = "returned_quantity") val returnedQuantity: Long = 0,
    @ColumnInfo(name = "returned_discount_amount") val returnedDiscountAmount: Long = 0,
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["supplier_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["transaction_id"], unique = true), Index("supplier_id")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "supplier_id") val supplierId: Long,
    @ColumnInfo(name = "gross_amount") val grossAmount: Long,
    @ColumnInfo(name = "discount_amount") val discountAmount: Long,
    @ColumnInfo(name = "net_amount") val netAmount: Long,
    @ColumnInfo(name = "direct_cost") val directCost: Long = 0,
    @ColumnInfo(name = "total_amount") val totalAmount: Long,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long,
    @ColumnInfo(name = "remaining_amount") val remainingAmount: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(entity = PurchaseEntity::class, parentColumns = ["id"], childColumns = ["purchase_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("purchase_id"), Index("product_id")],
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "purchase_id") val purchaseId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val quantity: Long,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    @ColumnInfo(name = "discount_amount") val discountAmount: Long = 0,
    @ColumnInfo(name = "total_amount") val totalAmount: Long,
    @ColumnInfo(name = "allocated_cost") val allocatedCost: Long = 0,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["transaction_id"], unique = true), Index("category")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    val title: String,
    val category: String,
    val amount: Long,
    @ColumnInfo(name = "paid_amount") val paidAmount: Long,
    @ColumnInfo(name = "remaining_amount") val remainingAmount: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "invoice_path") val invoicePath: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = BusinessEntity::class, parentColumns = ["id"], childColumns = ["business_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ContactEntity::class, parentColumns = ["id"], childColumns = ["contact_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["product_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("business_id"), Index("contact_id"), Index("due_date"), Index("product_id")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "contact_id") val contactId: Long? = null,
    val amount: Long = 0,
    @ColumnInfo(name = "due_date") val dueDate: Long,
    val status: String = "PENDING",
    val description: String = "",
    @ColumnInfo(name = "reminder_type") val reminderType: String = "DEBT",
    @ColumnInfo(name = "product_id") val productId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "backups", indices = [Index("business_id")])
data class BackupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "backup_date") val backupDate: Long,
    @ColumnInfo(name = "file_location") val fileLocation: String,
    val status: String,
    val checksum: String = "",
)

@Entity(tableName = "subscriptions", indices = [Index("business_id")])
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "business_id") val businessId: Long,
    @ColumnInfo(name = "plan_type") val planType: String,
    @ColumnInfo(name = "start_date") val startDate: Long? = null,
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
    val status: String,
    @ColumnInfo(name = "payment_reference") val paymentReference: String? = null,
)

@Entity(
    tableName = "account_ledger",
    foreignKeys = [
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["account_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("account_id"), Index("transaction_id"), Index("created_at")],
)
data class AccountLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    val delta: Long,
    @ColumnInfo(name = "balance_after") val balanceAfter: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sales_returns",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["original_transaction_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["transaction_id"], unique = true), Index("original_transaction_id")],
)
data class SalesReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "original_transaction_id") val originalTransactionId: Long,
    @ColumnInfo(name = "gross_amount") val grossAmount: Long,
    @ColumnInfo(name = "discount_amount") val discountAmount: Long,
    @ColumnInfo(name = "net_amount") val netAmount: Long,
    @ColumnInfo(name = "refund_amount") val refundAmount: Long,
    @ColumnInfo(name = "receivable_reduction") val receivableReduction: Long,
    @ColumnInfo(name = "cogs_reversed") val cogsReversed: Long,
    val reason: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sales_return_items",
    foreignKeys = [
        ForeignKey(entity = SalesReturnEntity::class, parentColumns = ["id"], childColumns = ["return_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SaleItemEntity::class, parentColumns = ["id"], childColumns = ["sale_item_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("return_id"), Index("sale_item_id")],
)
data class SalesReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "return_id") val returnId: Long,
    @ColumnInfo(name = "sale_item_id") val saleItemId: Long,
    val quantity: Long,
    @ColumnInfo(name = "unit_price") val unitPrice: Long,
    @ColumnInfo(name = "discount_share") val discountShare: Long,
    @ColumnInfo(name = "net_amount") val netAmount: Long,
    @ColumnInfo(name = "cogs_reversed") val cogsReversed: Long,
    val sellable: Boolean,
)
