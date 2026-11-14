package ir.hesabyar.data.repository

import androidx.room.withTransaction
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.common.runOperation
import ir.hesabyar.core.common.safeMul
import ir.hesabyar.core.database.*
import ir.hesabyar.core.validation.Validators
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.domain.model.Product
import ir.hesabyar.domain.model.BillingPolicy
import ir.hesabyar.domain.model.PlanType
import ir.hesabyar.domain.model.StockMovementType
import ir.hesabyar.domain.model.TransactionType
import ir.hesabyar.domain.repository.ProductCategory
import ir.hesabyar.domain.repository.ProductCommand
import ir.hesabyar.domain.repository.ProductRepository
import ir.hesabyar.domain.repository.StockMovementEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomProductRepository(private val db: HesabYarDatabase) : ProductRepository {
    override fun observeProducts(businessId: Long): Flow<List<Product>> = db.productDao().observeByBusiness(businessId).map { list -> list.map { it.toDomain() } }
    override fun searchProducts(businessId: Long, query: String): Flow<List<Product>> = db.productDao().search(businessId, query.trim()).map { list -> list.map { it.toDomain() } }
    override fun observeLowStock(businessId: Long): Flow<List<Product>> = db.productDao().observeLowStock(businessId).map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: Long): Product? = db.productDao().getById(id)?.toDomain()

    override fun observeStockMovements(productId: Long): Flow<List<StockMovementEntry>> =
        db.stockMovementDao().observeByProduct(productId).map { list -> list.map { StockMovementEntry(it.id, it.productId, StockMovementType.valueOf(it.movementType), it.quantity, it.unitCost, it.valueDelta, it.referenceTransactionId, it.reason, it.createdAt) } }

    override fun observeCategories(businessId: Long): Flow<List<ProductCategory>> =
        db.productCategoryDao().observeByBusiness(businessId).map { list -> list.map { ProductCategory(it.id, it.businessId, it.name) } }

    override suspend fun addCategory(businessId: Long, name: String): OperationResult<ProductCategory> = runOperation {
        val clean = Validators.requiredName(name, "نام دسته")
        val id = db.productCategoryDao().insert(ProductCategoryEntity(businessId = businessId, name = clean))
        ProductCategory(id, businessId, clean)
    }

    override suspend fun add(command: ProductCommand): OperationResult<Product> = runOperation {
        val name = Validators.requiredName(command.name, "نام کالا")
        Validators.money(command.purchasePrice, allowZero = true, label = "قیمت خرید")
        Validators.money(command.sellingPrice, allowZero = true, label = "قیمت فروش")
        Validators.money(command.openingStock, allowZero = true, label = "موجودی اولیه")
        Validators.money(command.minimumStock, allowZero = true, label = "حداقل موجودی")
        val businessId = db.businessDao().observeFirst().first()?.id ?: error("کسب‌وکار پیدا نشد.")
        val now = System.currentTimeMillis()
        val value = command.openingStock.safeMul(command.purchasePrice)
        db.withTransaction {
            val sub = db.subscriptionDao().observeCurrent(businessId).first()
            val plan = runCatching { PlanType.valueOf(sub?.planType ?: "FREE") }.getOrDefault(PlanType.FREE)
            val count = db.productDao().count(businessId)
            ir.hesabyar.core.common.requireValid(BillingPolicy.canAddProduct(plan, count), "در پلن رایگان حداکثر ${BillingPolicy.FREE_PRODUCT_LIMIT} کالا قابل ثبت است.")
            val id = db.productDao().insert(ProductEntity(businessId = businessId, name = name, code = command.code.trim(), categoryId = command.categoryId, purchasePrice = command.purchasePrice, sellingPrice = command.sellingPrice, stockQuantity = command.openingStock, minimumStock = command.minimumStock, inventoryValue = value, description = command.description.trim(), createdAt = now, updatedAt = now))
            if (command.openingStock > 0) {
                val txId = db.transactionDao().insert(TransactionEntity(
                    businessId = businessId, operationId = "product-opening-${UUID.randomUUID()}", transactionCode = "TMP-${UUID.randomUUID()}",
                    transactionType = TransactionType.OPENING.name, amount = value, paidAmount = 0, remainingAmount = 0,
                    transactionDate = now, description = "موجودی اولیهٔ ${name}", createdAt = now, updatedAt = now,
                ))
                db.transactionDao().updateCode(txId, "HY-$txId", now)
                db.stockMovementDao().insert(StockMovementEntity(productId = id, movementType = StockMovementType.OPENING.name, quantity = command.openingStock, unitCost = command.purchasePrice, valueDelta = value, referenceTransactionId = txId, reason = "موجودی اولیه", createdAt = now))
            }
            Product(id, businessId, name, command.code.trim(), command.categoryId, command.purchasePrice, command.sellingPrice, command.openingStock, command.minimumStock, value, command.description.trim())
        }
    }

    override suspend fun update(command: ProductCommand): OperationResult<Product> = runOperation {
        val name = Validators.requiredName(command.name, "نام کالا")
        Validators.money(command.purchasePrice, allowZero = true, label = "قیمت خرید")
        Validators.money(command.sellingPrice, allowZero = true, label = "قیمت فروش")
        val existing = db.productDao().getById(command.id) ?: error("کالا پیدا نشد.")
        db.withTransaction {
            val entity = existing.copy(name = name, code = command.code.trim(), categoryId = command.categoryId, purchasePrice = command.purchasePrice, sellingPrice = command.sellingPrice, minimumStock = command.minimumStock, description = command.description.trim(), updatedAt = System.currentTimeMillis())
            db.productDao().update(entity)
            entity.toDomain()
        }
    }
}
