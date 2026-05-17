package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.tryToSetForeground
import org.nekomanga.usecases.chapters.CachedMangaUnread
import uy.kohesive.injekt.injectLazy

/** Rebuilds filtered unread counters for every title in the library. */
class CachedUnreadRebuildJob(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val cachedMangaUnread: CachedMangaUnread by injectLazy()
    private val notifier = CachedUnreadRebuildNotifier(applicationContext)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = notifier.progressNotificationBuilder.build()
        val id = Notifications.Id.Library.CachedUnreadRebuild
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    override suspend fun doWork(): Result {
        tryToSetForeground()
        return try {
            val updated = cachedMangaUnread.refreshAllLibrary { processed, total ->
                notifier.showProgress(processed, total)
            }
            notifier.showComplete(updated)
            Result.success()
        } catch (e: Exception) {
            notifier.cancel()
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "CachedUnreadRebuild"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<CachedUnreadRebuildJob>().addTag(TAG).build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.KEEP, request)
        }

        fun isRunning(context: Context): Boolean {
            return WorkManager.getInstance(context).getWorkInfosByTag(TAG).get().any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
        }
    }
}
