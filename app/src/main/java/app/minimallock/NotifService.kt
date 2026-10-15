package app.minimallock

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Calendar

/**
 * Notification filtering WITHOUT reading or storing any notification content.
 * Notifications from apps that are not on your allow list are snoozed using Android's own
 * snooze feature; Android shows them again at the next digest time. We only look at the
 * app's package name and a few system flags.
 */
class NotifService : NotificationListenerService() {

    // key -> time it is due to reappear. Keys only, never content. Lives in memory only.
    private val snoozed = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val store = Store(this)
        if (!store.active || !store.notifFilterOn) return

        val pkg = sbn.packageName
        if (pkg == packageName) return
        if (pkg in store.notifAllow() || pkg in store.essentials()) return

        val n = sbn.notification
        if (sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        val cat = n.category
        if (cat == Notification.CATEGORY_CALL ||
            cat == Notification.CATEGORY_ALARM ||
            cat == Notification.CATEGORY_NAVIGATION ||
            cat == Notification.CATEGORY_TRANSPORT
        ) return

        val now = System.currentTimeMillis()
        val key = sbn.key

        // Forget old entries so the map can't grow forever.
        val it = snoozed.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value < now - 24 * 3_600_000L) it.remove()
        }

        // This is a notification we snoozed earlier coming back at digest time: let it through.
        val due = snoozed[key]
        if (due != null && now >= due - 60_000L) {
            snoozed.remove(key)
            return
        }

        val next = nextDigest(store.digestHours, now)
        snoozed[key] = next
        try {
            snoozeNotification(key, next - now)
        } catch (e: Exception) {
        }
    }

    private fun nextDigest(hoursCsv: String, now: Long): Long {
        val hours = hoursCsv.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..23 }
            .sorted()
            .ifEmpty { listOf(9, 13, 18, 21) }
        val cal = Calendar.getInstance()
        for (dayOffset in 0..1) {
            for (h in hours) {
                cal.timeInMillis = now
                cal.add(Calendar.DAY_OF_YEAR, dayOffset)
                cal.set(Calendar.HOUR_OF_DAY, h)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > now + 120_000L) return cal.timeInMillis
            }
        }
        return now + 3_600_000L
    }
}
