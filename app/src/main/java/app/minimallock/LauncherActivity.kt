package app.minimallock

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Black home screen: round clock, date, phone-time-left counter, a few favorites. */
class LauncherActivity : Activity() {

    private lateinit var store: Store
    private lateinit var clock: ClockView
    private lateinit var dateTv: TextView
    private lateinit var leftTv: TextView
    private lateinit var setupTv: TextView
    private lateinit var favBox: LinearLayout
    private var downY = 0f

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            refreshTime()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Apps.ensureDefaults(this, store)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(Ui.dp(this, 28), Ui.dp(this, 56), Ui.dp(this, 28), Ui.dp(this, 36))

        leftTv = Ui.text(this, "", 14f, Ui.GRAY)
        col.addView(leftTv)

        setupTv = Ui.text(this, "Finish setup  >", 14f, Color.WHITE, false)
        setupTv.setPadding(0, Ui.dp(this, 8), 0, 0)
        setupTv.setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        col.addView(setupTv)

        clock = ClockView(this)
        val clockLp = LinearLayout.LayoutParams(Ui.dp(this, 220), Ui.dp(this, 220))
        clockLp.topMargin = Ui.dp(this, 36)
        col.addView(clock, clockLp)

        dateTv = Ui.text(this, "", 16f, Ui.GRAY)
        dateTv.setPadding(0, Ui.dp(this, 16), 0, 0)
        col.addView(dateTv)

        val spacer = View(this)
        col.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        favBox = LinearLayout(this)
        favBox.orientation = LinearLayout.VERTICAL
        col.addView(
            favBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val hint = Ui.text(this, "swipe up: all apps    hold: settings", 12f, Color.rgb(90, 90, 90))
        hint.setPadding(0, Ui.dp(this, 20), 0, 0)
        col.addView(hint)

        root.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.setOnLongClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        Ui.register(this, tick, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onStop() {
        try {
            unregisterReceiver(tick)
        } catch (e: Exception) {
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        buildFavorites()
        refreshTime()
        val needSetup = !Perms.usage(this) || !Perms.a11y(this) || !Perms.home(this)
        setupTv.visibility = if (needSetup) View.VISIBLE else View.GONE
        if (!store.onboarded) {
            store.onboarded = true
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    private fun buildFavorites() {
        favBox.removeAllViews()
        val favs = store.favorites()
        if (favs.isEmpty()) {
            favBox.addView(Ui.text(this, "Add favorites in settings", 16f, Ui.GRAY))
            return
        }
        for (pkg in favs) {
            val t = Ui.text(this, Apps.label(this, pkg), 26f)
            t.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8))
            t.setOnClickListener { Apps.launch(this, pkg) }
            favBox.addView(t)
        }
    }

    private fun refreshTime() {
        clock.invalidate()
        dateTv.text = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date())
        var text = ""
        try {
            val lim = store.phoneLimitMin
            if (lim > 0 && Perms.usage(this)) {
                val r = Usage.compute(this, store.essentials())
                val left = lim * 60_000L - r.total
                text = if (left > 0) "Phone time left  ${Ui.fmt(left)}" else "Phone time used up for today"
            }
        } catch (e: Exception) {
        }
        leftTv.text = text
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) downY = ev.y
        if (ev.action == MotionEvent.ACTION_UP && downY - ev.y > Ui.dp(this, 120)) {
            startActivity(Intent(this, AllAppsActivity::class.java))
        }
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Launcher ignores back")
    override fun onBackPressed() {
        // A launcher has nowhere to go back to.
    }
}
