package dev.minimal.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Packages that currently have a (non-ongoing) notification. Read from Compose. */
object NotifState {
    var active by mutableStateOf(emptySet<String>())
    /** Newest notification title and text per app. Only filled while "Notification peek" is on. */
    var latest by mutableStateOf(emptyMap<String, Pair<String, String>>())
}

/**
 * Records WHICH apps have notifications so Home can show a dot. Titles and text are read only while
 * "Notification peek" is switched on, kept in memory, and never saved or sent anywhere.
 */
class NotificationDotService : NotificationListenerService() {
    companion object {
        private var instance: NotificationDotService? = null

        /** Re-reads the current notifications, e.g. right after Notification peek is switched on. */
        fun refreshNow() { instance?.refresh() }
    }

    override fun onListenerConnected() { instance = this; refresh() }

    override fun onListenerDisconnected() { instance = null; NotifState.active = emptySet(); NotifState.latest = emptyMap() }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    internal fun refresh() {
        val list = try { activeNotifications } catch (e: Exception) { null } ?: return
        val shown = list.filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
        NotifState.active = shown.map { it.packageName }.toSet()
        NotifState.latest = if (appPrefs(this).getBoolean("notifPeek", false)) {
            shown.sortedBy { it.postTime }.associate { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
                sbn.packageName to (title to text)
            }.filterValues { it.first.isNotBlank() || it.second.isNotBlank() }
        } else emptyMap()
    }
}
