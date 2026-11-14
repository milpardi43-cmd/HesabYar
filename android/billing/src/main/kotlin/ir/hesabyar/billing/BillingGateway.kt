package ir.hesabyar.billing

import ir.hesabyar.domain.model.PlanType

sealed interface BillingPurchaseResult {
    data class Verified(val plan: PlanType, val reference: String) : BillingPurchaseResult
    data object NotConfigured : BillingPurchaseResult
    data class Failed(val message: String) : BillingPurchaseResult
}

/**
 * Real Cafe Bazaar billing must verify purchases server-side before activation.
 * This build ships with no billing credentials, so the gateway refuses to fake success.
 */
interface BillingGateway {
    val isConfigured: Boolean
    suspend fun purchase(plan: PlanType): BillingPurchaseResult
    suspend fun verify(purchaseToken: String): BillingPurchaseResult
}

class NotConfiguredBillingGateway : BillingGateway {
    override val isConfigured: Boolean = false
    override suspend fun purchase(plan: PlanType): BillingPurchaseResult = BillingPurchaseResult.NotConfigured
    override suspend fun verify(purchaseToken: String): BillingPurchaseResult = BillingPurchaseResult.NotConfigured
}
