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
 * The enforcement engine. It does nothing until you press Activate.
 *
 * It only reads: the package name of the window in front, and (inside YouTube / Instagram only) the
 * view ids on screen to spot Shorts / Reels. It never stores on-screen text, and the app has no
 * network permission.
 *
 * Battery: only window-change events are received normally. Content-change events are switched on
 * only while YouTube or Instagram is in front, the next check is scheduled for the exact moment a
 * limit would run out (max every 15 s), and everything stops when the screen turns off.
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

        // Pieces of view ids that appear on Shorts / Reels screens. Matched as "contains", so small
        // renames still work. If they stop working after an app update, use Settings > "Capture
        // screen ids" to see the current ones.
        private val ytTokens = listOf(
            "reel_recycler", "reel_player_page", "reel_watch", "shorts_player", "shorts_container"
        )
        private val igTokens = listOf(
            "clips_viewer", "clips_swipe", "clips_video_container", "root_clips"
        )
    }

    private class Scan {
        val ids = TreeSet<String>()
        var tabSelected = false
    }

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
    private var lastQuick = 0L
    private var lastShortsAction = 0L
    private var shortsHits = 0
    private var dumpUntil = 0L
    private var homeSet: Set<String> = emptySet()
    private var homeAt = 0L

    // Continuous-session bookkeeping (memory only; a restart simply starts fresh sessions).
    private val sessAccum = HashMap<String, Long>()
    private val sessLast = HashMap<String, Long>()

    private val poll = Runnable { refresh() }

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
        val wantContent = (pkg == YT || pkg == IG) &&
            (store.active || System.currentTimeMillis() < dumpUntil)
        configure(wantContent)
        if (!store.active || isHome(pkg) || store.isEssential(pkg)) {
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

    // ------------------------------------------------------------------ rules

    /**
     * Applies every rule to the app in front. Returns true if it stepped in (blocked it or showed the pause).
     * Order: night block, emergency pass, running session lock, daily app limit, daily phone limit,
     * session length, pause screen, then the small counter.
     */
    private fun evaluate(pkg: String, fromForeground: Boolean): Boolean {
        if (!store.active || !Perms.usage(this)) {
            stopTracking()
            return false
        }
        val now = System.currentTimeMillis()

        val nightApp = pkg in store.nightApps()
        if (nightApp && store.isNight(now)) {
            blockNow(pkg, "night")
            return true
        }

        if (store.emergencyActive()) {
            showChip("Emergency pass")
            schedule(15_000L)
            return false
        }

        if (now < store.lockUntil(pkg)) {
            blockNow(pkg, "session")
            return true
        }

        val r = Usage.compute(this, store.essentials())
        val lim = store.appLimits()[pkg]
        val appLeft: Long? =
            if (lim != null && lim > 0) lim * 60_000L - (r.perApp[pkg] ?: 0L) else null
        val pl = store.phoneLimitMin
        val phoneLeft: Long? = if (pl > 0) pl * 60_000L - r.total else null

        if (appLeft != null && appLeft <= 0) {
            blockNow(pkg, "app")
            return true
        }
        if (phoneLeft != null && phoneLeft <= 0) {
            blockNow(pkg, "phone")
            return true
        }

        val rule = store.sessionRules()[pkg]
        var sessLeft: Long? = null
        if (rule != null) {
            val used = sessionStep(pkg, now, fromForeground)
            val left = rule.sessionMin * 60_000L - used
            if (left <= 0) {
                store.setLock(pkg, now + rule.lockMin * 60_000L)
                sessAccum.remove(pkg)
                sessLast.remove(pkg)
                blockNow(pkg, "session")
                return true
            }
            sessLeft = left
        }

        val watched = lim != null || rule != null
        if (fromForeground && store.frictionOn && watched && !hasGrace(pkg)) {
            showFriction(pkg)
            return true
        }

        val parts = ArrayList<String>()
        var next = 15_000L
        if (appLeft != null) {
            parts.add("App " + Ui.fmt(appLeft))
            next = minOf(next, appLeft)
        }
        if (phoneLeft != null) {
            parts.add("Phone " + Ui.fmt(phoneLeft))
            next = minOf(next, phoneLeft)
        }
        if (sessLeft != null) {
            parts.add("Session " + Ui.fmt(sessLeft))
            next = minOf(next, sessLeft)
        }
        var needTrack = parts.isNotEmpty()
        if (nightApp && store.nightOn) {
            next = minOf(next, store.msUntilNight(now))
            needTrack = true
        }
        if (!needTrack) {
            stopTracking()
            return false
        }
        if (parts.isEmpty()) hideChip() else showChip(parts.joinToString("  |  "))
        schedule(maxOf(next + 400L, 1000L))
        return false
    }

    /** Active time in the current continuous session. A break longer than the "session break" starts a new one. */
    private fun sessionStep(pkg: String, now: Long, fromForeground: Boolean): Long {
        val gap = store.sessionGapMin * 60_000L
        val last = sessLast[pkg]
        var acc = sessAccum[pkg] ?: 0L
        if (last == null || now - last > gap) {
            acc = 0L
        } else if (!fromForeground && now - last <= 30_000L) {
            acc += now - last
        }
        sessAccum[pkg] = acc
        sessLast[pkg] = now
        return acc
    }

    private fun refresh() {
        val pkg = current
        if (!store.active || pkg.isEmpty() || pkg == packageName || isHome(pkg) || store.isEssential(pkg)) {
            stopTracking()
            return
        }
        evaluate(pkg, false)
    }

    private fun schedule(ms: Long) {
        tracking = true
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, ms)
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
        dumpUntil = System.currentTimeMillis() + 40_000L
    }

    private fun onContent(pkg: String) {
        val dumping = System.currentTimeMillis() < dumpUntil
        val active = store.active
        if (!active && !dumping) return
        val now = SystemClock.uptimeMillis()

        // While scrolling, re-check the limits every few seconds so an app that was already open
        // cannot keep going past its time.
        if (active && now - lastQuick > 4000L) {
            lastQuick = now
            if (evaluate(pkg, false)) return
        }

        if (now - lastScan < 400L) return
        lastScan = now
        val watch = active && store.blockShorts && !store.emergencyActive()
        if (!dumping && !watch) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val scan = Scan()
        walk(root, scan, 0, intArrayOf(900))
        if (dumping) store.setDump(pkg, scan.ids.joinToString("\n"))

        if (watch && isShorts(pkg, scan)) {
            shortsHits++
            if (shortsHits >= 2) {
                val since = now - lastShortsAction
                if (since > 900L) {
                    // First time: Back. If it comes straight back, go Home instead of looping.
                    performGlobalAction(if (since < 4000L) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
                    lastShortsAction = now
                    shortsHits = 0
                    Toast.makeText(this, "Reels and Shorts are blocked", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            shortsHits = 0
        }
    }

    private fun isTabName(s: String?): Boolean {
        if (s == null) return false
        val x = s.trim().lowercase()
        return x == "reels" || x == "shorts" || x == "reels tab" || x == "shorts tab"
    }

    private fun walk(n: AccessibilityNodeInfo?, s: Scan, depth: Int, budget: IntArray) {
        if (n == null || depth > 40 || budget[0] <= 0) return
        budget[0]--
        if (!n.isVisibleToUser) return
        val id = n.viewIdResourceName
        if (id != null) s.ids.add(id)
        if (n.isSelected) {
            if (isTabName(n.contentDescription?.toString()) || isTabName(n.text?.toString())) {
                s.tabSelected = true
            }
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), s, depth + 1, budget)
    }

    private fun isShorts(pkg: String, s: Scan): Boolean {
        if (s.tabSelected) return true
        val tokens = if (pkg == YT) ytTokens else igTokens
        for (id in s.ids) {
            for (t in tokens) {
                if (id.contains(t, ignoreCase = true)) return true
            }
        }
        return false
    }
}
