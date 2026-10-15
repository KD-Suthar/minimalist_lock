package app.minimallock

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import java.util.Calendar

/** Reads today's foreground time per app from Android's own usage stats. Nothing is saved by us. */
object Usage {
    class Result(val total: Long, val perApp: Map<String, Long>)

    private val launchableCache = HashMap<String, Boolean>()

    fun startOfDay(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun counts(ctx: Context, pkg: String): Boolean {
        if (pkg == ctx.packageName || pkg == "android" || pkg == "com.android.systemui") return false
        val cached = launchableCache[pkg]
        if (cached != null) return cached
        val ok = ctx.packageManager.getLaunchIntentForPackage(pkg) != null
        launchableCache[pkg] = ok
        return ok
    }

    /** total = time in all normal apps except the ones in [exclude] (your necessities). */
    fun compute(ctx: Context, exclude: Set<String>): Result {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfDay(), now)
        val ev = UsageEvents.Event()
        val open = HashMap<String, Long>()
        val per = HashMap<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!open.containsKey(pkg)) open[pkg] = ev.timeStamp
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val s = open.remove(pkg)
                    if (s != null) per[pkg] = (per[pkg] ?: 0L) + (ev.timeStamp - s)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    for ((k, s) in open) per[k] = (per[k] ?: 0L) + (ev.timeStamp - s)
                    open.clear()
                }
            }
        }

        // The app that is open right now has no "paused" event yet.
        val power = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (power.isInteractive && open.isNotEmpty()) {
            var bestPkg: String? = null
            var bestT = 0L
            for ((k, s) in open) {
                if (s >= bestT) {
                    bestT = s
                    bestPkg = k
                }
            }
            if (bestPkg != null) per[bestPkg] = (per[bestPkg] ?: 0L) + (now - bestT)
        }

        var total = 0L
        for ((k, v) in per) {
            if (k !in exclude && counts(ctx, k)) total += v
        }
        return Result(total, per)
    }
}
