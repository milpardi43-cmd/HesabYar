package ir.hesabyar.domain.model

enum class PlanType(val label: String) { FREE("رایگان"), PROFESSIONAL("حرفه‌ای"), BUSINESS("کسب‌وکار") }

enum class Feature {
    UNLIMITED_CONTACTS, UNLIMITED_PRODUCTS, PROFIT_REPORT, MONTHLY_REPORT, EXCEL_EXPORT,
    DUE_REMINDERS, ADVANCED_BACKUP, CLOUD_BACKUP, MULTI_USER, ADVANCED_REPORTS
}

data class PlanLimits(val maxContacts: Int?, val maxProducts: Int?)

object BillingPolicy {
    const val FREE_CONTACT_LIMIT = 30
    const val FREE_PRODUCT_LIMIT = 50

    fun limits(plan: PlanType): PlanLimits = when (plan) {
        PlanType.FREE -> PlanLimits(FREE_CONTACT_LIMIT, FREE_PRODUCT_LIMIT)
        PlanType.PROFESSIONAL, PlanType.BUSINESS -> PlanLimits(null, null)
    }

    fun features(plan: PlanType): Set<Feature> = when (plan) {
        PlanType.FREE -> emptySet()
        PlanType.PROFESSIONAL -> setOf(Feature.UNLIMITED_CONTACTS, Feature.UNLIMITED_PRODUCTS, Feature.PROFIT_REPORT, Feature.MONTHLY_REPORT, Feature.EXCEL_EXPORT, Feature.DUE_REMINDERS, Feature.ADVANCED_BACKUP)
        PlanType.BUSINESS -> Feature.entries.toSet()
    }

    fun canAddContact(plan: PlanType, currentCount: Int): Boolean = limits(plan).maxContacts?.let { currentCount < it } ?: true
    fun canAddProduct(plan: PlanType, currentCount: Int): Boolean = limits(plan).maxProducts?.let { currentCount < it } ?: true
    fun hasFeature(plan: PlanType, feature: Feature): Boolean = feature in features(plan)
}
