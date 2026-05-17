package eu.kanade.tachiyomi.data.library

import android.content.Context
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notificationManager
import org.nekomanga.R

class CachedUnreadRebuildNotifier(private val context: Context) {

    val progressNotificationBuilder =
        context.notificationBuilder(Notifications.Channel.Library.Progress).apply {
            setSmallIcon(R.drawable.ic_neko_notification)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setAutoCancel(false)
        }

    fun showProgress(processed: Int, total: Int) {
        val notification =
            context
                .notificationBuilder(Notifications.Channel.Library.Progress)
                .setSmallIcon(R.drawable.ic_neko_notification)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setAutoCancel(false)
                .setContentTitle(
                    context.getString(R.string.rebuild_library_unread_counters_progress)
                )
                .setContentText(
                    context.getString(
                        R.string.rebuild_library_unread_counters_progress_detail,
                        processed,
                        total,
                    )
                )
                .setProgress(total.coerceAtLeast(1), processed.coerceAtMost(total), total == 0)
                .build()
        context.notificationManager.notify(
            Notifications.Id.Library.CachedUnreadRebuild,
            notification,
        )
    }

    fun showComplete(updatedCount: Int) {
        context.notificationManager.cancel(Notifications.Id.Library.CachedUnreadRebuild)
        val notification =
            context
                .notificationBuilder(Notifications.Channel.Library.Progress)
                .setSmallIcon(R.drawable.ic_neko_notification)
                .setAutoCancel(true)
                .setContentTitle(
                    context.getString(R.string.rebuild_library_unread_counters_complete)
                )
                .setContentText(
                    context.resources.getQuantityString(
                        R.plurals.rebuild_library_unread_counters_complete_message,
                        updatedCount,
                        updatedCount,
                    )
                )
                .build()
        context.notificationManager.notify(
            Notifications.Id.Library.CachedUnreadRebuild,
            notification,
        )
    }

    fun cancel() {
        context.notificationManager.cancel(Notifications.Id.Library.CachedUnreadRebuild)
    }
}
