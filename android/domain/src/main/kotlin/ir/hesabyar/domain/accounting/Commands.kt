package ir.hesabyar.domain.accounting

import ir.hesabyar.core.common.Quantity
import ir.hesabyar.core.common.Toman
import ir.hesabyar.domain.model.ExpenseCategory
import ir.hesabyar.domain.model.PaymentMethod

data class SaleLineCommand(
    val productId: Long,
    val quantity: Quantity,
    val unitPrice: Toman,
    val discount: Toman = 0,
)

data class SaleCommand(
    val operationId: String,
    val occurredAt: Long,
    val lines: List<SaleLineCommand>,
    val paidAmount: Toman,
    val contactId: Long? = null,
    val accountId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val description: String = "",
    val dueDate: Long? = null,
)

data class PurchaseLineCommand(
    val productId: Long,
    val quantity: Quantity,
    val unitPrice: Toman,
    val discount: Toman = 0,
)

data class PurchaseCommand(
    val operationId: String,
    val occurredAt: Long,
    val supplierId: Long,
    val lines: List<PurchaseLineCommand>,
    val paidAmount: Toman,
    val accountId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val directCost: Toman = 0,
    val description: String = "",
    val dueDate: Long? = null,
)

data class ExpenseCommand(
    val operationId: String,
    val occurredAt: Long,
    val title: String,
    val category: ExpenseCategory,
    val amount: Toman,
    val paidAmount: Toman,
    val accountId: Long? = null,
    val contactId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val description: String = "",
    val invoicePath: String = "",
    val dueDate: Long? = null,
)

enum class PaymentDirection { RECEIVE, PAY }

data class PaymentCommand(
    val operationId: String,
    val occurredAt: Long,
    val contactId: Long,
    val accountId: Long,
    val amount: Toman,
    val direction: PaymentDirection,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val description: String = "",
)

data class TransferCommand(
    val operationId: String,
    val occurredAt: Long,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Toman,
    val description: String = "",
)

data class AccountAdjustmentCommand(
    val operationId: String,
    val occurredAt: Long,
    val accountId: Long,
    val delta: Toman,
    val reason: String,
)

data class StockAdjustmentCommand(
    val operationId: String,
    val occurredAt: Long,
    val productId: Long,
    val delta: Quantity,
    val unitCost: Toman? = null,
    val reason: String,
)

data class SaleReturnLineCommand(
    val saleItemId: Long,
    val quantity: Quantity,
    val sellable: Boolean,
)

data class SaleReturnCommand(
    val operationId: String,
    val occurredAt: Long,
    val originalTransactionId: Long,
    val lines: List<SaleReturnLineCommand>,
    val refundAmount: Toman,
    val accountId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val reason: String,
)

data class ReverseCommand(
    val operationId: String,
    val occurredAt: Long,
    val originalTransactionId: Long,
    val reason: String,
)

data class ContactAdjustmentCommand(
    val operationId: String,
    val occurredAt: Long,
    val contactId: Long,
    val delta: Toman,
    val reason: String,
)
