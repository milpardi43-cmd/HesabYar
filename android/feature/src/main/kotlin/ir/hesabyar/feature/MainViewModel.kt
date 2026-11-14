package ir.hesabyar.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.hesabyar.domain.model.Business
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.BusinessRepository
import ir.hesabyar.domain.repository.ReminderRepository
import ir.hesabyar.domain.repository.SettingsRepository
import ir.hesabyar.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainState(val loading: Boolean = true, val business: Business? = null, val settings: AppSettings = AppSettings())

@HiltViewModel
class MainViewModel @Inject constructor(
    businessRepository: BusinessRepository,
    settingsRepository: SettingsRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val reminderRepository: ReminderRepository,
) : ViewModel() {
    val state: StateFlow<MainState> = combine(businessRepository.observeBusiness(), settingsRepository.settings) { business, settings ->
        MainState(loading = false, business = business, settings = settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())

    init {
        viewModelScope.launch {
            val business = businessRepository.getBusiness()
            if (business != null) {
                subscriptionRepository.ensureFreePlan(business.id)
                reminderRepository.scheduleDueCheck()
            }
        }
    }
}
