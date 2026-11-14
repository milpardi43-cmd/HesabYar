package ir.hesabyar.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ir.hesabyar.domain.repository.BackupRepository
import ir.hesabyar.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.automaticBackup) return Result.success()
        return when (backupRepository.createBackup()) {
            is ir.hesabyar.core.common.OperationResult.Success -> Result.success()
            is ir.hesabyar.core.common.OperationResult.Failure -> Result.retry()
        }
    }
}
