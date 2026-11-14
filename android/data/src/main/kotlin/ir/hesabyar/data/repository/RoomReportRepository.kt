package ir.hesabyar.data.repository

import ir.hesabyar.core.common.safeAdd
import ir.hesabyar.core.common.safeSub
import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.ReportRepository
import kotlinx.coroutines.flow.first

class RoomReportRepository(private val db: HesabYarDatabase) : ReportRepository {

    override suspend fun dailyReport(businessId: Long, from: Long, to: Long): ReportSummary = summary(businessId, from, to)
    override suspend fun monthlyReport(businessId: Long, from: Long, to: Long): ReportSummary = summary(businessId, from, to)
    override suspend fun profitReport(businessId: Long, from: Long, to: Long): ReportSummary = summary(businessId, from, to)

    private suspend fun summary(businessId: Long, from: Long, to: Long): ReportSummary {
        val sales = db.saleDao().getSalesInRange(businessId, from, to)
        val returns = db.salesReturnDao().getInRange(businessId, from, to)
        val expenses = db.expenseDao().getInRange(businessId, from, to)
        var gross = 0L
        var discounts = 0L
        var credit = 0L
        var saleCogs = 0L
        sales.forEach { sale ->
            gross = gross.safeAdd(sale.grossAmount)
            discounts = discounts.safeAdd(sale.discountAmount)
            credit = credit.safeAdd(sale.remainingAmount)
            db.saleDao().getItemsByTransaction(sale.transactionId).forEach { item -> saleCogs = saleCogs.safeAdd(item.cogs) }
        }
        var returnsNet = 0L
        var returnsCogs = 0L
        returns.forEach { r ->
            returnsNet = returnsNet.safeAdd(r.netAmount)
            returnsCogs = returnsCogs.safeAdd(r.cogsReversed)
        }
        val netSales = gross.safeSub(discounts).safeSub(returnsNet)
        val cogs = saleCogs.safeSub(returnsCogs)
        val opex = expenses.sumOf { it.amount }
        val grossProfit = netSales.safeSub(cogs)
        val ledger = db.accountLedgerDao().getInRange(businessId, from, to)
        var cashReceived = 0L
        ledger.forEach { entry ->
            val tx = db.transactionDao().getById(entry.transactionId) ?: return@forEach
            if (tx.status != TransactionStatus.POSTED.name) return@forEach
            val type = TransactionType.valueOf(tx.transactionType)
            if (entry.delta > 0 && type in setOf(TransactionType.CASH_SALE, TransactionType.CREDIT_SALE, TransactionType.MIXED_SALE, TransactionType.RECEIVE_RECEIVABLE)) {
                cashReceived = cashReceived.safeAdd(entry.delta)
            }
        }
        return ReportSummary(
            saleCount = sales.size,
            grossSales = gross,
            discounts = discounts,
            returns = returnsNet,
            netSales = netSales,
            cashReceived = cashReceived,
            creditSales = credit,
            operatingExpenses = opex,
            cogs = cogs,
            grossProfit = grossProfit,
            netProfit = grossProfit.safeSub(opex),
        )
    }

    override suspend fun cashReport(businessId: Long, from: Long, to: Long): CashReport {
        val accounts = db.accountDao().getByBusiness(businessId)
        val initial = accounts.sumOf { it.initialBalance }
        val before = db.accountLedgerDao().getBefore(businessId, from).sumOf { it.delta }
        val inRange = db.accountLedgerDao().getInRange(businessId, from, to)
        val opening = initial.safeAdd(before)
        val closing = opening.safeAdd(inRange.sumOf { it.delta })
        var receipts = 0L
        var payments = 0L
        inRange.forEach { entry ->
            val tx = db.transactionDao().getById(entry.transactionId) ?: return@forEach
            if (tx.status != TransactionStatus.POSTED.name) return@forEach
            val type = TransactionType.valueOf(tx.transactionType)
            if (type in setOf(TransactionType.TRANSFER, TransactionType.OPENING, TransactionType.ACCOUNT_ADJUSTMENT, TransactionType.REVERSAL)) return@forEach
            if (entry.delta > 0) receipts = receipts.safeAdd(entry.delta) else payments = payments.safeAdd(-entry.delta)
        }
        return CashReport(opening, closing, receipts, payments)
    }

    override suspend fun productReport(businessId: Long, from: Long, to: Long): List<ProductReportRow> {
        val products = db.productDao().observeByBusiness(businessId).first()
        val rows = products.associate { it.id to ProductReportRow(it.id, it.name, 0, 0, 0, 0) }.toMutableMap()
        val sales = db.saleDao().getSalesInRange(businessId, from, to)
        sales.forEach { sale ->
            db.saleDao().getItemsByTransaction(sale.transactionId).forEach { item ->
                val row = rows[item.productId] ?: return@forEach
                rows[item.productId] = row.copy(
                    quantitySold = row.quantitySold + item.quantity,
                    netSales = row.netSales.safeAdd(item.totalAmount),
                    cogs = row.cogs.safeAdd(item.cogs),
                    profit = row.profit.safeAdd(item.totalAmount.safeSub(item.cogs)),
                )
            }
        }
        db.salesReturnDao().getInRange(businessId, from, to).forEach { r ->
            db.salesReturnDao().getItems(r.id).forEach { item ->
                val saleItem = db.saleDao().getItemById(item.saleItemId) ?: return@forEach
                val row = rows[saleItem.productId] ?: return@forEach
                rows[saleItem.productId] = row.copy(
                    quantitySold = row.quantitySold - item.quantity,
                    netSales = row.netSales.safeSub(item.netAmount),
                    cogs = row.cogs.safeSub(item.cogsReversed),
                    profit = row.profit.safeSub(item.netAmount.safeSub(item.cogsReversed)),
                )
            }
        }
        return rows.values.sortedByDescending { it.quantitySold }
    }
}
