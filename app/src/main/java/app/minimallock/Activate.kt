package app.minimallock

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent

/** Turning MinimalLock on and leaving it. Until Activate is pressed, nothing is enforced. */
object Activate {
    private const val DLG = android.R.style.Theme_DeviceDefault_Dialog_Alert

    fun request(activity: Activity, onDone: () -> Unit) {
        if (!Perms.usage(activity) || !Perms.a11y(activity)) {
            AlertDialog.Builder(activity, DLG)
                .setTitle("Finish setup first")
                .setMessage("MinimalLock needs Usage access and the Accessibility service before it can limit anything.")
                .setPositiveButton("Open setup") { _, _ ->
                    activity.startActivity(Intent(activity, PermissionsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        AlertDialog.Builder(activity, DLG)
            .setTitle("Activate MinimalLock?")
            .setMessage(
                "Your limits start now. From here on, changing settings or exiting takes effort " +
                    "and waiting."
            )
            .setPositiveButton("Activate") { _, _ ->
                Store(activity).active = true
                BlockerService.instance?.resyncSoon()
                onDone()
            }
            .setNegativeButton("Not yet", null)
            .show()
    }

    /** Turns MinimalLock off and sends you to pick another home app. */
    fun exit(activity: Activity) {
        AlertDialog.Builder(activity, DLG)
            .setTitle("Exit MinimalLock?")
            .setMessage("This turns MinimalLock off and lets you pick another home app.")
            .setPositiveButton("Exit") { _, _ ->
                Store(activity).active = false
                Apps.exitLauncher(activity)
            }
            .setNegativeButton("Stay", null)
            .show()
    }
}
