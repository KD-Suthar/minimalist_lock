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

/**
 * Black home screen: round clock with digital time and day inside, status line, favorites,
 * and a bottom bar with All apps, Settings and Exit. Swipe left also opens all apps.
 */
class LauncherActivity : Activity() {

    private lateinit var store: Store
    private lateinit var clock: ClockView
    private lateinit var statusTv: TextView
    private lateinit var setupTv: TextView
    private lateinit var activateBtn: TextView
    private lateinit var favBox: LinearLayout
    private var downX = 0f
    private var downY = 0f
    private var swiped = false

    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            refreshTime()
        }
    }

    private fun dp(v: Int): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        Apps.ensureDefaults(this, store)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(28), dp(48), dp(28), dp(8))

        statusTv = Ui.text(this, "", 14f, Ui.GRAY)
        col.addView(statusTv)

        setupTv = Ui.text(this, "Finish setup  >", 14f, Color.WHITE, false)
        setupTv.setPadding(0, dp(8), 0, 0)
        setupTv.setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        col.addView(setupTv)

        clock = ClockView(this)
        val clockLp = LinearLayout.LayoutParams(dp(280), dp(280))
        clockLp.topMargin = dp(22)
        col.addView(clock, clockLp)

        activateBtn = Ui.button(this, "Activate") { Activate.request(this) { refreshAll() } }
        val aLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        aLp.topMargin = dp(20)
        col.addView(activateBtn, aLp)

        col.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        favBox = LinearLayout(this)
        favBox.orientation = LinearLayout.VERTICAL
        col.addView(
            favBox,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val line = View(this)
        line.setBackgroundColor(Color.rgb(40, 40, 40))
        val lineLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        lineLp.topMargin = dp(14)
        col.addView(line, lineLp)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        val itemLp = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        bar.addView(barItem("‹  All apps") { openApps() }, itemLp())
        bar.addView(barItem("Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, itemLp())
        bar.addView(barItem("Exit") {
            if (store.active) {
                val i = Intent(this, SettingsActivity::class.java)
                i.putExtra("exit", true)
                startActivity(i)
            } else {
                Activate.exit(this)
            }
        }, itemLp())
        col.addView(
            bar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
    }

    private fun barItem(label: String, onClick: () -> Unit): TextView {
        val t = Ui.text(this, label, 14f, Color.WHITE, false)
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(14), 0, dp(14))
        t.setOnClickListener { onClick() }
        return t
    }

    private fun openApps() {
        startActivity(Intent(this, AllAppsActivity::class.java))
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
        refreshAll()
        if (!store.onboarded) {
            store.onboarded = true
            startActivity(Intent(this, PermissionsActivity::class.java))
        }
    }

    private fun refreshAll() {
        buildFavorites()
        refreshTime()
        activateBtn.visibility = if (store.active) View.GONE else View.VISIBLE
        val needSetup = !Perms.usage(this) || !Perms.a11y(this) || !Perms.home(this)
        setupTv.visibility = if (needSetup) View.VISIBLE else View.GONE
    }

    private fun buildFavorites() {
        favBox.removeAllViews()
        val favs = store.favorites()
        if (favs.isEmpty()) {
            favBox.addView(Ui.text(this, "Add favorites in Settings", 16f, Ui.GRAY))
            return
        }
        for (pkg in favs) {
            val t = Ui.text(this, Apps.label(this, pkg), 18f)
            t.letterSpacing = 0.02f
            t.setPadding(0, dp(7), 0, dp(7))
            t.setOnClickListener { Apps.launch(this, pkg) }
            favBox.addView(t)
        }
    }

    private fun refreshTime() {
        clock.invalidate()
        if (!store.active) {
            statusTv.text = "Not active  -  apps open normally"
            return
        }
        var text = "Active"
        try {
            val lim = store.phoneLimitMin
            if (lim > 0 && Perms.usage(this)) {
                val r = Usage.compute(this, store.essentials())
                val left = lim * 60_000L - r.total
                text = if (left > 0) "Phone time left  ${Ui.fmt(left)}" else "Phone time used up for today"
            }
        } catch (e: Exception) {
        }
        statusTv.text = text
    }

    /** Swipe left anywhere opens all apps. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                swiped = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = downX - ev.x
                val dy = Math.abs(downY - ev.y)
                if (!swiped && dx > dp(90) && dy < dx) {
                    swiped = true
                    openApps()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Launcher ignores back")
    override fun onBackPressed() {
        // A launcher has nowhere to go back to.
    }
}
