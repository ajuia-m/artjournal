package com.ajuia.artjournal.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ajuia.artjournal.ArtJournalApplication
import java.io.IOException
import java.util.concurrent.TimeUnit
import retrofit2.HttpException

fun interface SyncRequestScheduler {
    fun enqueue(schoolId: String)
}

object NoOpSyncRequestScheduler : SyncRequestScheduler {
    override fun enqueue(schoolId: String) = Unit
}

class WorkManagerSyncRequestScheduler(context: Context) : SyncRequestScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue(schoolId: String) {
        val request = OneTimeWorkRequestBuilder<ServerJournalSyncWorker>()
            .setInputData(workDataOf(ServerJournalSyncWorker.SCHOOL_ID_KEY to schoolId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(
            uniqueWorkName(schoolId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    companion object {
        const val TAG = "server-journal-sync"

        fun uniqueWorkName(schoolId: String): String = "$TAG-$schoolId"
    }
}

class ServerJournalSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val schoolId = inputData.getString(SCHOOL_ID_KEY)
            ?: return Result.failure(workDataOf(ERROR_KEY to "missing_school_id"))
        val container = (applicationContext as ArtJournalApplication).container
        return try {
            container.serverJournalSyncEngine.synchronize(schoolId)
            Result.success()
        } catch (throwable: Throwable) {
            if (throwable.isRetryableSyncFailure(
                    hasStoredSession = container.serverSessionRepository.hasStoredSession()
                )
            ) {
                Result.retry()
            } else {
                Result.failure(workDataOf(ERROR_KEY to throwable.javaClass.simpleName))
            }
        }
    }

    companion object {
        const val SCHOOL_ID_KEY = "school_id"
        const val ERROR_KEY = "error"
    }
}

internal fun Throwable.isRetryableSyncFailure(hasStoredSession: Boolean): Boolean = when (this) {
    is IOException -> true
    is HttpException -> code() >= 500 || (code() == 401 && hasStoredSession)
    else -> false
}
