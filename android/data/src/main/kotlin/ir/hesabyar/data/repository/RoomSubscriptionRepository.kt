package ir.hesabyar.data.repository

import ir.hesabyar.core.database.HesabYarDatabase
import ir.hesabyar.core.database.SubscriptionEntity
import ir.hesabyar.data.mapper.toDomain
import ir.hesabyar.domain.model.Subscription
import ir.hesabyar.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomSubscriptionRepository(private val db: HesabYarDatabase) : SubscriptionRepository {
    override fun observeSubscription(businessId: Long): Flow<Subscription?> = db.subscriptionDao().observeCurrent(businessId).map { it?.toDomain() }

    override suspend fun ensureFreePlan(businessId: Long) {
        val current = db.subscriptionDao().observeCurrent(businessId).first()
        if (current == null) {
            db.subscriptionDao().insert(SubscriptionEntity(businessId = businessId, planType = "FREE", status = "ACTIVE", startDate = System.currentTimeMillis()))
        }
    }
}
