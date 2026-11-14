package ir.hesabyar.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.hesabyar.core.database.*
import ir.hesabyar.data.repository.*
import ir.hesabyar.domain.repository.*
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hesabyar_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): HesabYarDatabase =
        Room.databaseBuilder(context, HesabYarDatabase::class.java, "hesabyar.db").build()

    @Provides fun businessDao(db: HesabYarDatabase) = db.businessDao()
    @Provides fun accountDao(db: HesabYarDatabase) = db.accountDao()
    @Provides fun contactDao(db: HesabYarDatabase) = db.contactDao()
    @Provides fun productDao(db: HesabYarDatabase) = db.productDao()
    @Provides fun transactionDao(db: HesabYarDatabase) = db.transactionDao()
    @Provides fun saleDao(db: HesabYarDatabase) = db.saleDao()
    @Provides fun purchaseDao(db: HesabYarDatabase) = db.purchaseDao()
    @Provides fun expenseDao(db: HesabYarDatabase) = db.expenseDao()
    @Provides fun contactLedgerDao(db: HesabYarDatabase) = db.contactLedgerDao()
    @Provides fun stockMovementDao(db: HesabYarDatabase) = db.stockMovementDao()
    @Provides fun reminderDao(db: HesabYarDatabase) = db.reminderDao()
    @Provides fun backupDao(db: HesabYarDatabase) = db.backupDao()
    @Provides fun subscriptionDao(db: HesabYarDatabase) = db.subscriptionDao()

    @Provides @Singleton fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore
    @Provides @Singleton fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

    @Provides @Singleton
    fun businessRepository(db: HesabYarDatabase): BusinessRepository =
        RoomBusinessRepository(db)

    @Provides @Singleton
    fun accountingRepository(db: HesabYarDatabase): AccountingRepository = RoomAccountingRepository(db)

    @Provides @Singleton
    fun contactRepository(db: HesabYarDatabase): ContactRepository = RoomContactRepository(db)

    @Provides @Singleton
    fun productRepository(db: HesabYarDatabase): ProductRepository = RoomProductRepository(db)

    @Provides @Singleton
    fun accountRepository(db: HesabYarDatabase): AccountRepository = RoomAccountRepository(db)

    @Provides @Singleton
    fun reportRepository(db: HesabYarDatabase): ReportRepository = RoomReportRepository(db)

    @Provides @Singleton
    fun settingsRepository(dataStore: DataStore<Preferences>): SettingsRepository = DataStoreSettingsRepository(dataStore)

    @Provides @Singleton
    fun reminderRepository(db: HesabYarDatabase, workManager: WorkManager): ReminderRepository = RoomReminderRepository(db, workManager)

    @Provides @Singleton
    fun backupRepository(@ApplicationContext context: Context, db: HesabYarDatabase): BackupRepository = LocalBackupRepository(context, db)

    @Provides @Singleton
    fun subscriptionRepository(db: HesabYarDatabase): SubscriptionRepository = RoomSubscriptionRepository(db)
}
