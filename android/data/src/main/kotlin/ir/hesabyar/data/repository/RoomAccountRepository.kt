package ir.hesabyar.data.repository

import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.domain.model.Account
import ir.hesabyar.domain.repository.AccountLedgerEntry
import ir.hesabyar.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAccountRepository(private val db: HesabYarDatabase) : AccountRepository {
    override fun observeAccounts(businessId: Long): Flow<List<Account>> = db.accountDao().observeByBusiness(businessId).map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: Long): Account? = db.accountDao().getById(id)?.toDomain()
    override fun observeStatement(accountId: Long): Flow<List<AccountLedgerEntry>> =
        db.accountLedgerDao().observeByAccount(accountId).map { list ->
            list.map { entry ->
                val tx = db.transactionDao().getById(entry.transactionId)
                AccountLedgerEntry(entry.id, entry.accountId, entry.transactionId, entry.delta, entry.balanceAfter, entry.createdAt, tx?.transactionCode ?: "", tx?.description ?: "")
            }
        }
}
