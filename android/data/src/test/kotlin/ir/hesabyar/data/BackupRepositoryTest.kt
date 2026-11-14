package ir.hesabyar.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.data.repository.*
import ir.hesabyar.domain.accounting.SaleCommand
import ir.hesabyar.domain.accounting.SaleLineCommand
import ir.hesabyar.domain.model.AccountType
import ir.hesabyar.domain.repository.ProductCommand
import ir.hesabyar.domain.repository.SetupCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: HesabYarDatabase
    private lateinit var backup: LocalBackupRepository
    private var businessId = 0L
    private var productId = 0L
    private var cashId = 0L

    private fun open() {
        db = Room.databaseBuilder(context, HesabYarDatabase::class.java, "hesabyar.db").allowMainThreadQueries().build()
        backup = LocalBackupRepository(context, db)
    }

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("hesabyar.db")
        open()
        businessId = RoomBusinessRepository(db).setup(SetupCommand(name = "تست", openingCash = 1_000_000, openingDate = System.currentTimeMillis())).let { (it as OperationResult.Success).value.id }
        productId = RoomProductRepository(db).add(ProductCommand(name = "کالا", purchasePrice = 100_000, sellingPrice = 200_000, openingStock = 5)).let { (it as OperationResult.Success).value.id }
        cashId = db.accountDao().getByBusiness(businessId).first { it.accountType == AccountType.CASH.name }.id
    }

    @After fun tearDown() { if (db.isOpen) db.close() }

    @Test fun backupAndRestoreRoundTrip() = runBlocking {
        val path = backup.createBackup().let { (it as OperationResult.Success).value }
        assertThat(File(path).exists()).isTrue()
        RoomAccountingRepository(db).recordSale(SaleCommand("after-backup", System.currentTimeMillis(), listOf(SaleLineCommand(productId, 1, 200_000)), paidAmount = 200_000, accountId = cashId))
        assertThat(db.accountDao().getById(cashId)!!.currentBalance).isEqualTo(1_200_000)
        val result = backup.restoreBackup(Uri.fromFile(File(path)).toString())
        assertThat(result).isInstanceOf(OperationResult.Success::class.java)
        open()
        assertThat(db.accountDao().getById(cashId)!!.currentBalance).isEqualTo(1_000_000)
        assertThat(db.productDao().getById(productId)!!.stockQuantity).isEqualTo(5)
    }

    @Test fun restoreRejectsInvalidFile() = runBlocking {
        val bad = File(context.cacheDir, "bad.db")
        bad.writeText("not a database")
        val result = backup.restoreBackup(Uri.fromFile(bad).toString())
        assertThat(result).isInstanceOf(OperationResult.Failure::class.java)
    }
}
