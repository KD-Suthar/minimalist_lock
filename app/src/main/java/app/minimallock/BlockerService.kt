package app.minimallock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.widget.Toast
import java.util.TreeSet

/**
 * The enforcement engine. It only reads: the package name of the window in front, and (inside
 * YouTube / Instagram only) the view ids on screen to spot Shorts / Reels. It never reads or stores
 * on-screen text, and the app has no network permission.
 *
 * Battery: only window-change events are received normally. Content-change events are switched on
 * only while YouTube or Instagram is in front, and everything stops when the screen turns off.
 */
class BlockerService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: BlockerService? = null

        const val YT = "com.google.android.youtube"
        const val IG = "com.instagram.android"

        private val grace = HashMap<String, Long>()

        fun grantGrace(pkg: String, ms: Long) {
            synchronized(grace) { grace[pkg] = System.currentTimeMillis() + ms }
            instance?.resyncSoon()
        }

        private fun hasGrace(pkg: String): Boolean =
            synchronized(grace) { (grace[pkg] ?: 0L) > System.currentTimeMillis() }

        // View ids that appear on Shorts / Reels screens. These can change when the apps update;
        // use Settings > "Capture screen ids" to find the current ones.
        private val ytIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_watch_player",
            "com.google.android.youtube:id/shorts_player"
        )
        private val igIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_swipe_refresh_container",
            "com.instagram.android:id/root_clips_layout",
            "com.instagram.android:id/clips_video_container"
        )
    }

    private class Status(val appLeft: Long?, val phoneLeft: Long?)

    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var chip: TextView? = null
    private var current = ""
    private var tracking = false
    private var contentOn = false
    private var lastBlockAt = 0L
    private var lastFrictionAt = 0L
    private var lastScan = 0L
    private var lastShortsAction = 0L
    private var dumpUntil = 0L
    private var homeSet: Set<String> = emptySet()
    private var homeAt = 0L

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            if (tracking) handler.postDelayed(this, 15_000L)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> stopTracking()
                Intent.ACTION_USER_PRESENT -> resyncSoon()
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        store = Store(this)
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        configure(false)
        val f = IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_OFF)
        f.addAction(Intent.ACTION_USER_PRESENT)
        Ui.register(this, screenReceiver, f)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        handler.removeCallbacksAndMessages(null)
        hideChip()
        tracking = false
        instance = null
    }

    /** Turn content-change events on only while they are needed (YouTube / Instagram in front). */
    private fun configure(content: Boolean) {
        if (content == contentOn) return
        val info = serviceInfo ?: return
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            (if (content) AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED else 0)
        serviceInfo = info
        contentOn = content
    }

    // ------------------------------------------------------------------ events

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == packageName) {
                    current = pkg
                    configure(false)
                    stopTracking()
                    return
                }
                val cls = e.className?.toString() ?: return
                if (!isActivity(pkg, cls)) return
                onForeground(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == current && (pkg == YT || pkg == IG)) onContent(pkg)
            }
        }
    }

    private fun isActivity(pkg: String, cls: String): Boolean {
        return try {
            packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isHome(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (homeSet.isEmpty() || now - homeAt > 300_000L) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            homeSet = packageManager.queryIntentActivities(i, 0)
                .map { it.activityInfo.packageName }
                .toSet()
            homeAt = now
        }
        return pkg in homeSet
    }

    private fun onForeground(pkg: String) {
        current = pkg
        configure(pkg == YT || pkg == IG)
        if (isHome(pkg) || store.isEssential(pkg)) {
            stopTracking()
            return
        }
        evaluate(pkg, true)
    }

    fun resyncSoon() {
        handler.postDelayed({ resync() }, 600L)
    }

    private fun resync() {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || isHome(pkg)) return
        onForeground(pkg)
    }

    // ------------------------------------------------------------------ limits

    private fun status(pkg: String): Status {
        val r = Usage.compute(this, store.essentials())
        val lim = store.appLimits()[pkg]
        val appLeft = if (lim != null && lim > 0) lim * 60_000L - (r.perApp[pkg] ?: 0L) else null
        val pl = store.phoneLimitMin
        val phoneLeft = if (pl > 0) pl * 60_000L - r.total else null
        return Status(appLeft, phoneLeft)
    }

    private fun evaluate(pkg: String, fromForeground: Boolean) {
        if (!Perms.usage(this)) {
            stopTracking()
            return
        }
        if (store.emergencyActive()) {
            showChip("Emergency pass")
            startTracking()
            return
        }
        val s = status(pkg)
        val appLeft = s.appLeft
        val phoneLeft = s.phoneLeft
        val appOver = appLeft != null && appLeft <= 0
        val phoneOver = phoneLeft != null && phoneLeft <= 0
        if (appOver || phoneOver) {
            blockNow(pkg, if (appOver) "app" else "phone")
            return
        }
        if (fromForeground && store.frictionOn &&
            store.appLimits().containsKey(pkg) && !hasGrace(pkg)
        ) {
            showFriction(pkg)
            return
        }
        val parts = ArrayList<String>()
        if (appLeft != null) parts.add("App " + Ui.fmt(appLeft))
        if (phoneLeft != null) parts.add("Phone " + Ui.fmt(phoneLeft))
        if (parts.isEmpty()) {
            stopTracking()
            return
        }
        showChip(parts.joinToString("  |  "))
        startTracking()
    }

    private fun refresh() {
        val pkg = current
        if (pkg.isEmpty() || pkg == packageName || isHome(pkg) || store.isEssential(pkg)) {
            stopTracking()
            return
        }
        evaluate(pkg, false)
    }

    private fun startTracking() {
        if (tracking) return
        tracking = true
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 15_000L)
    }

    private fun stopTracking() {
        tracking = false
        handler.removeCallbacks(poll)
        hideChip()
    }

    private fun blockNow(pkg: String, why: String) {
        stopTracking()
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 2000L) return
        lastBlockAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            try {
                val i = Intent(this, BlockActivity::class.java)
                i.putExtra("pkg", pkg)
                i.putExtra("why", why)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(i)
            } catch (e: Exception) {
            }
        }, 250L)
    }

    private fun showFriction(pkg: String) {
        stopTracking()
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrictionAt < 2000L) return
        lastFrictionAt = now
        try {
            val i = Intent(this, FrictionActivity::class.java)
            i.putExtra("pkg", pkg)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(i)
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    // ------------------------------------------------------------------ counter chip

    private fun showChip(text: String) {
        val w = wm ?: return
        var view = chip
        if (view == null) {
            val t = TextView(this)
            t.setTextColor(Color.WHITE)
            t.textSize = 12f
            t.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4))
            val bg = GradientDrawable()
            bg.setColor(Color.argb(150, 0, 0, 0))
            bg.cornerRadius = Ui.dp(this, 12).toFloat()
            t.background = bg
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = Ui.dp(this, 36)
            try {
                w.addView(t, lp)
            } catch (e: Exception) {
                return
            }
            chip = t
            view = t
        }
        view.text = text
    }

    private fun hideChip() {
        val c = chip ?: return
        try {
            wm?.removeView(c)
        } catch (e: Exception) {
        }
        chip = null
    }

    // ------------------------------------------------------------------ Shorts / Reels

    fun requestDump() {
        dumpUntil = System.currentTimeMillis() + 25_000L
    }

    private fun onContent(pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastScan < 350L) return
        lastScan = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        if (System.currentTimeMillis() < dumpUntil) {
            val ids = TreeSet<String>()
            collect(root, ids, 0, intArrayOf(3000))
            store.setDump(pkg, ids.joinToString("\n"))
        }

        if (store.blockShorts && !store.emergencyActive() && detectShorts(root, pkg)) {
            if (now - lastShortsAction > 1200L) {
                lastShortsAction = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(this, "Shorts and Reels are blocked", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int, budget: IntArray) {
        if (n == null || depth > 40 || budget[0] <= 0) return
        budget[0]--
        if (!n.isVisibleToUser) return
        val id = n.viewIdResourceName
        if (id != null) out.add(id)
        for (i in 0 until n.childCount) collect(n.getChild(i), out, depth + 1, budget)
    }

    private fun detectShorts(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val ids = if (pkg == YT) ytIds else igIds
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.any { it.isVisibleToUser }) return true
        }
        if (pkg == IG) {
            val tabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
            if (tabs != null && tabs.any { it.isSelected }) return true
        }
        return false
    }
}
