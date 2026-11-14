package ir.hesabyar.billing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides @Singleton
    fun billingGateway(): BillingGateway = NotConfiguredBillingGateway()
}
