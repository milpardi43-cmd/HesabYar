package ir.hesabyar.domain.model

import ir.hesabyar.core.common.Quantity
import ir.hesabyar.core.common.Toman

enum class BusinessType(val label: String) {
    SUPERMARKET("سوپرمارکت"), CLOTHING("پوشاک"), HOME_APPLIANCE("لوازم خانگی"),
    DIGITAL("موبایل و دیجیتال"), TOOLS("ابزار و قطعات"), FOOD("مواد غذایی"),
    SERVICE("خدماتی"), OTHER("سایر")
}

enum class AccountType(val label: String) { CASH("صندوق نقدی"), BANK("حساب بانکی"), CARD_READER("کارت‌خوان"), OTHER("سایر") }
enum class ContactType(val label: String) { CUSTOMER("مشتری"), SUPPLIER("تأمین‌کننده"), BOTH("مشتری و تأمین‌کننده") }
enum class PaymentMethod(val label: String) { CASH("نقد"), CARD_READER("کارت‌خوان"), BANK_TRANSFER("انتقال بانکی"), OTHER("سایر") }
enum class TransactionStatus { POSTED, REVERSED }
enum class ExpenseCategory(val label: String) {
    RENT("اجاره"), SALARY("حقوق"), UTILITIES("آب و برق و گاز"), TRANSPORT("حمل‌ونقل"),
    REPAIR("تعمیرات"), ADVERTISING("تبلیغات"), SUPPLIES("ملزومات"), SERVICES("خدمات"), OTHER("سایر")
}

enum class TransactionType {
    OPENING, CASH_SALE, CREDIT_SALE, MIXED_SALE, RECEIVE_RECEIVABLE, EXPENSE,
    PURCHASE, PAY_PAYABLE, SALES_RETURN, ACCOUNT_ADJUSTMENT, STOCK_ADJUSTMENT, TRANSFER, CONTACT_ADJUSTMENT, REVERSAL
}

enum class StockMovementType { OPENING, PURCHASE, SALE, SALES_RETURN, ADJUSTMENT }

data class Business(
    val id: Long = 0,
    val name: String,
    val ownerName: String = "",
    val phone: String = "",
    val businessType: BusinessType = BusinessType.OTHER,
    val city: String = "",
    val createdAt: Long = 0,
)

data class Account(
    val id: Long = 0,
    val businessId: Long,
    val title: String,
    val accountType: AccountType,
    val initialBalance: Toman = 0,
    val currentBalance: Toman = 0,
)

data class Contact(
    val id: Long = 0,
    val businessId: Long,
    val name: String,
    val phone: String = "",
    val contactType: ContactType = ContactType.CUSTOMER,
    val initialBalance: Toman = 0,
    val description: String = "",
    val currentBalance: Toman = 0,
    val createdAt: Long = 0,
)

data class Product(
    val id: Long = 0,
    val businessId: Long,
    val name: String,
    val code: String = "",
    val categoryId: Long? = null,
    val purchasePrice: Toman,
    val sellingPrice: Toman,
    val stockQuantity: Quantity = 0,
    val minimumStock: Quantity = 0,
    val inventoryValue: Toman = 0,
    val description: String = "",
) {
    val averageCost: Toman get() = if (stockQuantity > 0) inventoryValue / stockQuantity else purchasePrice
    val isLowStock: Boolean get() = stockQuantity <= minimumStock
}

data class ProductSnapshot(
    val id: Long,
    val stockQuantity: Quantity,
    val inventoryValue: Toman,
    val purchasePrice: Toman,
)

data class TransactionHeader(
    val id: Long = 0,
    val businessId: Long,
    val operationId: String,
    val transactionCode: String,
    val type: TransactionType,
    val amount: Toman,
    val paidAmount: Toman,
    val remainingAmount: Toman,
    val contactId: Long? = null,
    val accountId: Long? = null,
    val counterAccountId: Long? = null,
    val occurredAt: Long,
    val description: String = "",
    val status: TransactionStatus = TransactionStatus.POSTED,
)

data class SaleItemSnapshot(
    val id: Long,
    val productId: Long,
    val quantity: Quantity,
    val unitPrice: Toman,
    val unitCostSnapshot: Toman,
    val discountAmount: Toman,
    val totalAmount: Toman,
    val returnedQuantity: Quantity,
    val returnedDiscountAmount: Toman = 0,
)
