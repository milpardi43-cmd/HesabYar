package ir.hesabyar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.data.repository.*
import ir.hesabyar.domain.accounting.*
import ir.hesabyar.domain.model.*
import ir.hesabyar.domain.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountingRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: HesabYarDatabase
    private lateinit var businessRepo: RoomBusinessRepository
    private lateinit var contactRepo: RoomContactRepository
    private lateinit var productRepo: RoomProductRepository
    private lateinit var accounting: RoomAccountingRepository
    private lateinit var reports: RoomReportRepository
    private var businessId = 0L
    private var cashAccountId = 0L
    private var customerId = 0L
    private var supplierId = 0L
    private var productId = 0L

    private fun openDb() {
        db = Room.databaseBuilder(context, HesabYarDatabase::class.java, "test.db").allowMainThreadQueries().build()
        businessRepo = RoomBusinessRepository(db)
        contactRepo = RoomContactRepository(db)
        productRepo = RoomProductRepository(db)
        accounting = RoomAccountingRepository(db)
        reports = RoomReportRepository(db)
    }

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("test.db")
        openDb()
        val business = businessRepo.setup(SetupCommand(name = "فروشگاه تست", openingCash = 1_000_000, openingDate = System.currentTimeMillis())).unwrap()
        businessId = business.id
        cashAccountId = db.accountDao().getByBusiness(businessId).first { it.accountType == AccountType.CASH.name }.id
        customerId = contactRepo.add(ContactCommand(name = "مشتری", contactType = ContactType.CUSTOMER)).unwrap().id
        supplierId = contactRepo.add(ContactCommand(name = "تأمین‌کننده", contactType = ContactType.SUPPLIER)).unwrap().id
        productId = productRepo.add(ProductCommand(name = "کالا", purchasePrice = 100_000, sellingPrice = 200_000, openingStock = 10)).unwrap().id
    }

    @After fun tearDown() { db.close() }

    private fun <T> OperationResult<T>.unwrap(): T {
        assertThat(this).isInstanceOf(OperationResult.Success::class.java)
        return (this as OperationResult.Success).value
    }

    private suspend fun cashBalance(): Long = db.accountDao().getById(cashAccountId)!!.currentBalance
    private suspend fun customerBalance(): Long = db.contactDao().getById(customerId)!!.currentBalance
    private suspend fun supplierBalance(): Long = db.contactDao().getById(supplierId)!!.currentBalance
    private suspend fun todayReport() = reports.dailyReport(businessId, 0, Long.MAX_VALUE)

    @Test fun scenario1_cashSale() = runBlocking {
        accounting.recordSale(SaleCommand("s1", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 200_000, accountId = cashAccountId)).unwrap()
        assertThat(cashBalance()).isEqualTo(1_200_000)
        assertThat(todayReport().netSales).isEqualTo(200_000)
        assertThat(customerBalance()).isEqualTo(0)
    }

    @Test fun scenario2_creditSale() = runBlocking {
        accounting.recordSale(SaleCommand("s2", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 300_000)), paidAmount = 0, contactId = customerId)).unwrap()
        assertThat(cashBalance()).isEqualTo(1_000_000)
        assertThat(customerBalance()).isEqualTo(300_000)
        assertThat(todayReport().netSales).isEqualTo(300_000)
    }

    @Test fun scenario3_collectReceivableIsNotIncome() = runBlocking {
        accounting.recordSale(SaleCommand("s3a", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 300_000)), paidAmount = 0, contactId = customerId)).unwrap()
        accounting.recordPayment(PaymentCommand("s3b", System.currentTimeMillis(), customerId, cashAccountId, 100_000, PaymentDirection.RECEIVE)).unwrap()
        assertThat(cashBalance()).isEqualTo(1_100_000)
        assertThat(customerBalance()).isEqualTo(200_000)
        assertThat(todayReport().netSales).isEqualTo(300_000)
        assertThat(todayReport().cashReceived).isEqualTo(100_000)
    }

    @Test fun scenario4_purchaseIsNotOpex() = runBlocking {
        accounting.recordPurchase(PurchaseCommand("p4", System.currentTimeMillis(), supplierId, listOf(PurchaseLineCommand(productId, 1, 500_000)), paidAmount = 0)).unwrap()
        val product = db.productDao().getById(productId)!!
        assertThat(product.stockQuantity).isEqualTo(11)
        assertThat(product.inventoryValue).isEqualTo(1_500_000)
        assertThat(todayReport().operatingExpenses).isEqualTo(0)
        assertThat(supplierBalance()).isEqualTo(-500_000)
    }

    @Test fun scenario5_cogsAndGrossProfit() = runBlocking {
        val p = productRepo.add(ProductCommand(name = "کالای ویژه", purchasePrice = 100_000, sellingPrice = 150_000, openingStock = 1)).unwrap()
        accounting.recordSale(SaleCommand("s5", System.currentTimeMillis(), listOf(SaleLineCommand(p.id, 1, 150_000)), paidAmount = 150_000, accountId = cashAccountId)).unwrap()
        val report = todayReport()
        assertThat(report.netSales).isEqualTo(150_000)
        assertThat(report.cogs).isEqualTo(100_000)
        assertThat(report.grossProfit).isEqualTo(50_000)
    }

    @Test fun scenario6_payDebtIsNotExpense() = runBlocking {
        accounting.recordPurchase(PurchaseCommand("p6a", System.currentTimeMillis(), supplierId, listOf(PurchaseLineCommand(productId, 1, 500_000)), paidAmount = 0)).unwrap()
        accounting.recordPayment(PaymentCommand("p6b", System.currentTimeMillis(), supplierId, cashAccountId, 100_000, PaymentDirection.PAY)).unwrap()
        assertThat(cashBalance()).isEqualTo(900_000)
        assertThat(supplierBalance()).isEqualTo(-400_000)
        assertThat(todayReport().operatingExpenses).isEqualTo(0)
    }

    @Test fun scenario7_mixedSale() = runBlocking {
        accounting.recordSale(SaleCommand("s7", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 500_000)), paidAmount = 200_000, contactId = customerId, accountId = cashAccountId)).unwrap()
        assertThat(todayReport().netSales).isEqualTo(500_000)
        assertThat(cashBalance()).isEqualTo(1_200_000)
        assertThat(customerBalance()).isEqualTo(300_000)
    }

    @Test fun scenario8_persistenceAcrossRestart() = runBlocking {
        accounting.recordSale(SaleCommand("s8", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 200_000, accountId = cashAccountId)).unwrap()
        db.close()
        openDb()
        assertThat(cashBalance()).isEqualTo(1_200_000)
        assertThat(reports.dailyReport(businessId, 0, Long.MAX_VALUE).netSales).isEqualTo(200_000)
        assertThat(db.productDao().getById(productId)!!.stockQuantity).isEqualTo(9)
    }

    @Test fun scenario9_offlineOperationsRetained() = runBlocking {
        accounting.recordSale(SaleCommand("s9a", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 0, contactId = customerId)).unwrap()
        accounting.recordExpense(ExpenseCommand("s9b", System.currentTimeMillis(), "اجاره", ExpenseCategory.RENT, 50_000, 50_000, accountId = cashAccountId)).unwrap()
        accounting.recordPayment(PaymentCommand("s9c", System.currentTimeMillis(), customerId, cashAccountId, 100_000, PaymentDirection.RECEIVE)).unwrap()
        db.close()
        openDb()
        assertThat(cashBalance()).isEqualTo(1_050_000)
        assertThat(customerBalance()).isEqualTo(100_000)
        val report = reports.dailyReport(businessId, 0, Long.MAX_VALUE)
        assertThat(report.netSales).isEqualTo(200_000)
        assertThat(report.operatingExpenses).isEqualTo(50_000)
    }

    @Test fun scenario10_duplicateSubmitWritesOnce() = runBlocking {
        val command = SaleCommand("dup", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 200_000, accountId = cashAccountId)
        val first = accounting.recordSale(command).unwrap()
        val second = accounting.recordSale(command).unwrap()
        assertThat(second.id).isEqualTo(first.id)
        assertThat(cashBalance()).isEqualTo(1_200_000)
        assertThat(db.transactionDao().count(businessId)).isEqualTo(2) // opening stock + one sale
        assertThat(db.productDao().getById(productId)!!.stockQuantity).isEqualTo(9)
    }

    @Test fun movingWeightedAverageCost() = runBlocking {
        // opening: 10 units @100k = 1,000,000. Buy 10 @150k = 1,500,000 -> 20 units, 2,500,000, avg 125k.
        accounting.recordPurchase(PurchaseCommand("avg", System.currentTimeMillis(), supplierId, listOf(PurchaseLineCommand(productId, 10, 150_000)), paidAmount = 0)).unwrap()
        val product = db.productDao().getById(productId)!!
        assertThat(product.stockQuantity).isEqualTo(20)
        assertThat(product.inventoryValue).isEqualTo(2_500_000)
        accounting.recordSale(SaleCommand("avg-sale", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 200_000, accountId = cashAccountId)).unwrap()
        assertThat(todayReport().cogs).isEqualTo(125_000)
    }

    @Test fun transferIsNotIncomeOrExpense() = runBlocking {
        val bank = db.accountDao().getByBusiness(businessId).first { it.accountType == AccountType.BANK.name }
        accounting.recordTransfer(TransferCommand("t1", System.currentTimeMillis(), cashAccountId, bank.id, 200_000)).unwrap()
        assertThat(cashBalance()).isEqualTo(800_000)
        assertThat(db.accountDao().getById(bank.id)!!.currentBalance).isEqualTo(200_000)
        val report = todayReport()
        assertThat(report.netSales).isEqualTo(0)
        assertThat(report.operatingExpenses).isEqualTo(0)
        assertThat(report.cashReceived).isEqualTo(0)
    }

    @Test fun saleReturnRestocksOnlySellableAndAdjustsDebt() = runBlocking {
        val tx = accounting.recordSale(SaleCommand("ret-sale", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 2, 200_000)), paidAmount = 0, contactId = customerId)).unwrap()
        val item = accounting.getSaleItems(tx.id).single()
        accounting.recordSaleReturn(SaleReturnCommand("ret", System.currentTimeMillis(), tx.id, listOf(SaleReturnLineCommand(item.id, 1, true)), refundAmount = 0, reason = "مرجوعی")).unwrap()
        assertThat(customerBalance()).isEqualTo(200_000)
        assertThat(db.productDao().getById(productId)!!.stockQuantity).isEqualTo(9)
        assertThat(todayReport().returns).isEqualTo(200_000)
        assertThat(todayReport().netSales).isEqualTo(200_000)
    }

    @Test fun reversalUndoesSaleWithoutDeleting() = runBlocking {
        val tx = accounting.recordSale(SaleCommand("rev-sale", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 200_000, accountId = cashAccountId)).unwrap()
        accounting.reverseTransaction(ReverseCommand("rev", System.currentTimeMillis(), tx.id, "اشتباه")).unwrap()
        assertThat(cashBalance()).isEqualTo(1_000_000)
        assertThat(db.productDao().getById(productId)!!.stockQuantity).isEqualTo(10)
        assertThat(db.transactionDao().getById(tx.id)!!.status).isEqualTo(TransactionStatus.REVERSED.name)
        assertThat(todayReport().netSales).isEqualTo(0)
    }
}
