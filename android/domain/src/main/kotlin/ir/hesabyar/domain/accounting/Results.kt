package ir.hesabyar.domain.accounting

import ir.hesabyar.core.common.Quantity
import ir.hesabyar.core.common.Toman
import ir.hesabyar.domain.model.TransactionType

data class SaleLineResult(
    val productId: Long,
    val quantity: Quantity,
    val unitPrice: Toman,
    val unitCostSnapshot: Toman,
    val discount: Toman,
    val totalAmount: Toman,
    val cogs: Toman,
)

data class SaleResult(
    val type: TransactionType,
    val grossAmount: Toman,
    val discountAmount: Toman,
    val netAmount: Toman,
    val paidAmount: Toman,
    val remainingAmount: Toman,
    val cogs: Toman,
    val grossProfit: Toman,
    val lines: List<SaleLineResult>,
)

data class PurchaseLineResult(
    val productId: Long,
    val quantity: Quantity,
    val unitPrice: Toman,
    val discount: Toman,
    val goodsNet: Toman,
    val allocatedCost: Toman,
)

data class PurchaseResult(
    val grossAmount: Toman,
    val discountAmount: Toman,
    val goodsNet: Toman,
    val directCost: Toman,
    val totalAmount: Toman,
    val paidAmount: Toman,
    val remainingAmount: Toman,
    val lines: List<PurchaseLineResult>,
)

data class ExpenseResult(
    val amount: Toman,
    val paidAmount: Toman,
    val remainingAmount: Toman,
)

data class SaleReturnLineResult(
    val saleItemId: Long,
    val productId: Long,
    val quantity: Quantity,
    val unitPrice: Toman,
    val unitCostSnapshot: Toman,
    val discountShare: Toman,
    val netAmount: Toman,
    val cogsReversed: Toman,
    val sellable: Boolean,
)

data class SaleReturnResult(
    val grossAmount: Toman,
    val discountAmount: Toman,
    val netAmount: Toman,
    val refundAmount: Toman,
    val receivableReduction: Toman,
    val cogsReversed: Toman,
    val lines: List<SaleReturnLineResult>,
)
