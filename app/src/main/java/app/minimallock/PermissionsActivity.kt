package app.minimallock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView

/** One screen to grant everything the app needs. All of it stays on this phone. */
class PermissionsActivity : Activity() {

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store(this).onboarded = true
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 24), Ui.dp(this, 40))
        scroll.addView(box)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        box.removeAllViews()
        box.addView(Ui.text(this, "Setup", 28f))
        box.addView(
            Ui.text(
                this,
                "Tap each item to grant it. This app has no internet permission, so nothing can leave your phone.",
                14f,
                Ui.GRAY
            )
        )

        item("Usage access", "Counts your time per app", Perms.usage(this)) {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        item(
            "Accessibility service",
            "Sees which app is open, shows the counter, blocks Shorts and Reels",
            Perms.a11y(this)
        ) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        item("Display over other apps", "Lets pause and block screens appear", Perms.overlay(this)) {
            open(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        item("Notification access", "Delays non-essential notifications to digest times", Perms.notif(this)) {
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        item("Default home app", "Use MinimalLock as your home screen", Perms.home(this)) {
            open(Intent(Settings.ACTION_HOME_SETTINGS))
        }
        item("Battery: unrestricted", "Stops Android from killing the blocker", Perms.battery(this)) {
            open(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }

        val note = Ui.text(
            this,
            "If Accessibility or Notification access is greyed out: open App info, tap the three dots " +
                "at the top right, choose \"Allow restricted settings\", then try again.\n\n" +
                "OnePlus: also set Battery to Unrestricted and allow Auto-launch for this app, " +
                "otherwise the phone may stop the blocker in the background.",
            13f,
            Ui.GRAY
        )
        note.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 12))
        box.addView(note)

        box.addView(Ui.button(this, "Open App info") {
            open(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        })
        val done = Ui.button(this, "Done") { finish() }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = Ui.dp(this, 12)
        box.addView(done, lp)
    }

    private fun item(title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.VERTICAL
        row.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14))
        row.addView(Ui.text(this, (if (ok) "[done]  " else "[todo]  ") + title, 18f))
        row.addView(Ui.text(this, sub, 13f, Ui.GRAY))
        row.setOnClickListener { onClick() }
        box.addView(row)
    }

    private fun open(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (e2: Exception) {
            }
        }
    }
}
