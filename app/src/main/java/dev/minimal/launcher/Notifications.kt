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
}

/**
 * Only records WHICH apps have notifications so Home can show a dot.
 * It never reads notification titles or text.
 */
class NotificationDotService : NotificationListenerService() {
    override fun onListenerConnected() { refresh() }

    override fun onListenerDisconnected() { NotifState.active = emptySet() }

    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        val list = try { activeNotifications } catch (e: Exception) { null } ?: return
        NotifState.active = list
            .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .map { it.packageName }
            .toSet()
    }
}
