package ir.hesabyar.domain.model

import ir.hesabyar.core.common.Quantity
import ir.hesabyar.core.common.Toman

data class ReportSummary(
    val saleCount: Int = 0,
    val grossSales: Toman = 0,
    val discounts: Toman = 0,
    val returns: Toman = 0,
    val netSales: Toman = 0,
    val cashReceived: Toman = 0,
    val creditSales: Toman = 0,
    val operatingExpenses: Toman = 0,
    val cogs: Toman = 0,
    val grossProfit: Toman = 0,
    val netProfit: Toman = 0,
)

data class CashReport(
    val openingBalance: Toman,
    val closingBalance: Toman,
    val receipts: Toman,
    val payments: Toman,
)

data class ProductReportRow(
    val productId: Long,
    val name: String,
    val quantitySold: Quantity,
    val netSales: Toman,
    val cogs: Toman,
    val profit: Toman,
)

data class Reminder(
    val id: Long = 0,
    val businessId: Long,
    val contactId: Long? = null,
    val amount: Toman = 0,
    val dueDate: Long,
    val status: String = "PENDING",
    val description: String = "",
    val reminderType: String = "DEBT",
    val productId: Long? = null,
)

data class Subscription(
    val id: Long = 0,
    val businessId: Long,
    val planType: String = "FREE",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val status: String = "INACTIVE",
    val paymentReference: String? = null,
)
