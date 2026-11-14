package ir.hesabyar.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import ir.hesabyar.domain.repository.AppSettings
import ir.hesabyar.domain.repository.AppTheme
import ir.hesabyar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreSettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PERSIAN_DIGITS = booleanPreferencesKey("persian_digits")
        val STOCK_ALERTS = booleanPreferencesKey("stock_alerts")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val FONT_SCALE = intPreferencesKey("font_scale_percent")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            AppSettings(
                theme = runCatching { AppTheme.valueOf(prefs[Keys.THEME] ?: "SYSTEM") }.getOrDefault(AppTheme.SYSTEM),
                persianDigits = prefs[Keys.PERSIAN_DIGITS] ?: true,
                stockAlerts = prefs[Keys.STOCK_ALERTS] ?: true,
                automaticBackup = prefs[Keys.AUTO_BACKUP] ?: false,
                fontScalePercent = prefs[Keys.FONT_SCALE] ?: 100,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            )
        }

    override suspend fun setTheme(theme: AppTheme) { dataStore.edit { it[Keys.THEME] = theme.name } }
    override suspend fun setPersianDigits(enabled: Boolean) { dataStore.edit { it[Keys.PERSIAN_DIGITS] = enabled } }
    override suspend fun setStockAlerts(enabled: Boolean) { dataStore.edit { it[Keys.STOCK_ALERTS] = enabled } }
    override suspend fun setAutomaticBackup(enabled: Boolean) { dataStore.edit { it[Keys.AUTO_BACKUP] = enabled } }
    override suspend fun setNotificationsEnabled(enabled: Boolean) { dataStore.edit { it[Keys.NOTIFICATIONS] = enabled } }
    override suspend fun setFontScalePercent(percent: Int) { dataStore.edit { it[Keys.FONT_SCALE] = percent.coerceIn(85, 130) } }
}
