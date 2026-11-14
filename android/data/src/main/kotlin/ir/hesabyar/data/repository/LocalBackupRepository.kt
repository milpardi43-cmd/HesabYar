package ir.hesabyar.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import ir.hesabyar.core.common.OperationResult
import ir.hesabyar.core.common.runOperation
import ir.hesabyar.core.database.BackupEntity
import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.data.backup.XlsxWriter
import ir.hesabyar.domain.model.TransactionType
import ir.hesabyar.domain.repository.BackupRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalBackupRepository(private val context: Context, private val db: HesabYarDatabase) : BackupRepository {

    override suspend fun createBackup(): OperationResult<String> = runOperation {
        val businessId = db.businessDao().observeFirst().first()?.id ?: error("کسب‌وکار پیدا نشد.")
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val source = context.getDatabasePath("hesabyar.db")
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(dir, "hesabyar-$stamp.db")
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        val checksum = sha256(target)
        db.backupDao().insert(BackupEntity(businessId = businessId, backupDate = System.currentTimeMillis(), fileLocation = target.absolutePath, status = "OK", checksum = checksum))
        target.absolutePath
    }

    override suspend fun restoreBackup(uri: String): OperationResult<Unit> = runOperation {
        val temp = File(context.cacheDir, "restore-validate.db")
        val parsed = Uri.parse(uri)
        val stream = if (parsed.scheme == "file") File(parsed.path ?: "").inputStream() else context.contentResolver.openInputStream(parsed)
        stream?.use { input -> temp.outputStream().use { input.copyTo(it) } }
            ?: error("فایل پشتیبان قابل خواندن نیست.")
        validateDatabase(temp)
        db.close()
        val target = context.getDatabasePath("hesabyar.db")
        temp.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        File(target.absolutePath + "-wal").delete()
        File(target.absolutePath + "-shm").delete()
        temp.delete()
    }

    private fun validateDatabase(file: File) {
        require(file.length() > 100) { "فایل پشتیبان معتبر نیست." }
        val header = ByteArray(16)
        file.inputStream().use { it.read(header) }
        require(String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) { "فایل انتخاب‌شده پایگاه‌دادهٔ حساب‌یار نیست." }
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
            sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "فایل پشتیبان آسیب دیده است." }
            }
            sqlite.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                require(!cursor.moveToFirst()) { "فایل پشتیبان دارای خطای ارجاع است." }
            }
            val required = listOf("businesses", "accounts", "contacts", "products", "transactions", "sales", "sale_items", "purchases", "purchase_items", "expenses", "contact_ledger", "stock_movements", "account_ledger", "reminders", "backups", "subscriptions")
            required.forEach { table ->
                sqlite.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { cursor ->
                    require(cursor.moveToFirst()) { "جدول $table در فایل پشتیبان وجود ندارد." }
                }
            }
        }
    }

    override suspend fun exportCsv(): OperationResult<String> = runOperation {
        val businessId = db.businessDao().observeFirst().first()?.id ?: error("کسب‌وکار پیدا نشد.")
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "hesabyar-transactions.csv")
        val transactions = db.transactionDao().observeByBusiness(businessId).first()
        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val writer = out.bufferedWriter(Charsets.UTF_8)
            writer.appendLine("id,code,type,amount,paid,remaining,date,status,description")
            transactions.forEach { tx ->
                writer.appendLine(listOf(tx.id, tx.transactionCode, tx.transactionType, tx.amount, tx.paidAmount, tx.remainingAmount, tx.transactionDate, tx.status, csv(tx.description)).joinToString(","))
            }
            writer.flush()
        }
        file.absolutePath
    }

    override suspend fun exportExcel(): OperationResult<String> = runOperation {
        val businessId = db.businessDao().observeFirst().first()?.id ?: error("کسب‌وکار پیدا نشد.")
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "hesabyar.xlsx")
        val transactions = db.transactionDao().observeByBusiness(businessId).first()
        val contacts = db.contactDao().observeByBusiness(businessId).first()
        val products = db.productDao().observeByBusiness(businessId).first()
        XlsxWriter.write(file, listOf(
            XlsxWriter.Sheet("Transactions", listOf(listOf("کد", "نوع", "مبلغ", "پرداخت‌شده", "مانده", "تاریخ", "وضعیت", "توضیح")) + transactions.map { listOf(it.transactionCode, typeLabel(it.transactionType), it.amount, it.paidAmount, it.remainingAmount, it.transactionDate, it.status, it.description) }),
            XlsxWriter.Sheet("Contacts", listOf(listOf("نام", "تلفن", "نوع", "مانده")) + contacts.map { listOf(it.name, it.phone, it.contactType, it.currentBalance) }),
            XlsxWriter.Sheet("Products", listOf(listOf("نام", "کد", "قیمت خرید", "قیمت فروش", "موجودی", "ارزش موجودی")) + products.map { listOf(it.name, it.code, it.purchasePrice, it.sellingPrice, it.stockQuantity, it.inventoryValue) }),
        ))
        file.absolutePath
    }

    private fun typeLabel(type: String): String = runCatching { TransactionType.valueOf(type).name }.getOrDefault(type)
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
