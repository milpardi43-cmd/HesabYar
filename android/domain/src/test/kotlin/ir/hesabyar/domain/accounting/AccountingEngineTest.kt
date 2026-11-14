package ir.hesabyar.domain.accounting

import com.google.common.truth.Truth.assertThat
import ir.hesabyar.core.common.ValidationException
import ir.hesabyar.domain.model.ExpenseCategory
import ir.hesabyar.domain.model.ProductSnapshot
import ir.hesabyar.domain.model.SaleItemSnapshot
import ir.hesabyar.domain.model.TransactionType
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingEngineTest {
    private fun product(id: Long, stock: Long, value: Long, purchase: Long = 0) = ProductSnapshot(id, stock, value, purchase)

    @Test fun cashSaleIncreasesSalesAndHasNoDebt() {
        val result = AccountingEngine.computeSale(
            SaleCommand("op-1", 1L, listOf(SaleLineCommand(1, 1, 200_000)), paidAmount = 200_000, accountId = 1),
            mapOf(1L to product(1, 5, 500_000)),
        )
        assertThat(result.type).isEqualTo(TransactionType.CASH_SALE)
        assertThat(result.netAmount).isEqualTo(200_000)
        assertThat(result.paidAmount).isEqualTo(200_000)
        assertThat(result.remainingAmount).isEqualTo(0)
    }

    @Test fun creditSaleRequiresCustomerAndDoesNotReceiveCash() {
        assertThrows(ValidationException::class.java) {
            AccountingEngine.computeSale(SaleCommand("op-2", 1L, listOf(SaleLineCommand(1, 1, 300_000)), paidAmount = 0), mapOf(1L to product(1, 5, 0)))
        }
        val result = AccountingEngine.computeSale(
            SaleCommand("op-2", 1L, listOf(SaleLineCommand(1, 1, 300_000)), paidAmount = 0, contactId = 9),
            mapOf(1L to product(1, 5, 0)),
        )
        assertThat(result.type).isEqualTo(TransactionType.CREDIT_SALE)
        assertThat(result.paidAmount).isEqualTo(0)
        assertThat(result.remainingAmount).isEqualTo(300_000)
    }

    @Test fun mixedSaleSplitsCashAndDebt() {
        val result = AccountingEngine.computeSale(
            SaleCommand("op-7", 1L, listOf(SaleLineCommand(1, 1, 500_000)), paidAmount = 200_000, contactId = 9, accountId = 1),
            mapOf(1L to product(1, 5, 0)),
        )
        assertThat(result.type).isEqualTo(TransactionType.MIXED_SALE)
        assertThat(result.netAmount).isEqualTo(500_000)
        assertThat(result.paidAmount).isEqualTo(200_000)
        assertThat(result.remainingAmount).isEqualTo(300_000)
    }

    @Test fun cogsAndGrossProfitUseMovingAverageSnapshot() {
        val result = AccountingEngine.computeSale(
            SaleCommand("op-5", 1L, listOf(SaleLineCommand(1, 1, 150_000)), paidAmount = 150_000, accountId = 1),
            mapOf(1L to product(1, 1, 100_000)),
        )
        assertThat(result.netAmount).isEqualTo(150_000)
        assertThat(result.cogs).isEqualTo(100_000)
        assertThat(result.grossProfit).isEqualTo(50_000)
    }

    @Test fun purchaseIsNotOperatingExpense() {
        val result = AccountingEngine.computePurchase(PurchaseCommand("op-4", 1L, supplierId = 2, lines = listOf(PurchaseLineCommand(1, 1, 500_000)), paidAmount = 500_000, accountId = 1))
        assertThat(result.totalAmount).isEqualTo(500_000)
        assertThat(result.remainingAmount).isEqualTo(0)
    }

    @Test fun unpaidPurchaseRequiresSupplier() {
        assertThrows(ValidationException::class.java) {
            AccountingEngine.computePurchase(PurchaseCommand("op", 1L, supplierId = 0, lines = listOf(PurchaseLineCommand(1, 1, 500_000)), paidAmount = 0))
        }
    }

    @Test fun directPurchaseCostIsCapitalized() {
        val result = AccountingEngine.computePurchase(PurchaseCommand("op", 1L, supplierId = 2, lines = listOf(PurchaseLineCommand(1, 2, 100_000)), paidAmount = 0, directCost = 20_000))
        assertThat(result.goodsNet).isEqualTo(200_000)
        assertThat(result.totalAmount).isEqualTo(220_000)
        assertThat(result.lines.single().allocatedCost).isEqualTo(220_000)
    }

    @Test fun expenseSeparatesPaidAndPayable() {
        val result = AccountingEngine.computeExpense(ExpenseCommand("op", 1L, "اجاره", ExpenseCategory.RENT, amount = 400_000, paidAmount = 100_000, accountId = 1, contactId = 2))
        assertThat(result.amount).isEqualTo(400_000)
        assertThat(result.paidAmount).isEqualTo(100_000)
        assertThat(result.remainingAmount).isEqualTo(300_000)
    }

    @Test fun discountCannotExceedLineTotal() {
        assertThrows(ValidationException::class.java) {
            AccountingEngine.computeSale(SaleCommand("op", 1L, listOf(SaleLineCommand(1, 1, 100_000, discount = 100_001)), paidAmount = 0, contactId = 1), mapOf(1L to product(1, 5, 0)))
        }
    }

    @Test fun saleRejectsInsufficientStock() {
        assertThrows(ValidationException::class.java) {
            AccountingEngine.computeSale(SaleCommand("op", 1L, listOf(SaleLineCommand(1, 6, 100_000)), paidAmount = 0, contactId = 1), mapOf(1L to product(1, 5, 0)))
        }
    }

    @Test fun returnQuantityCannotExceedRemainingSold() {
        val items = listOf(SaleItemSnapshot(10, 1, 3, 100_000, 60_000, 0, 300_000, returnedQuantity = 2))
        assertThrows(ValidationException::class.java) {
            AccountingEngine.computeSaleReturn(SaleReturnCommand("op", 1L, 5, listOf(SaleReturnLineCommand(10, 2, true)), refundAmount = 0, reason = "خرابی"), items)
        }
    }

    @Test fun sellableReturnReversesCogsButDamagedDoesNotRestock() {
        val items = listOf(SaleItemSnapshot(10, 1, 2, 100_000, 60_000, 10_000, 190_000, 0))
        val result = AccountingEngine.computeSaleReturn(
            SaleReturnCommand("op", 1L, 5, listOf(SaleReturnLineCommand(10, 1, false)), refundAmount = 95_000, accountId = 1, reason = "خرابی"),
            items,
        )
        assertThat(result.netAmount).isEqualTo(95_000)
        assertThat(result.cogsReversed).isEqualTo(0)
        assertThat(result.refundAmount).isEqualTo(95_000)
    }

    @Test fun transferRequiresDifferentAccounts() {
        assertThrows(ValidationException::class.java) {
            AccountingEngine.validateTransfer(TransferCommand("op", 1L, 1, 1, 100_000))
        }
    }
}
