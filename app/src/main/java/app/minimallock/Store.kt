package app.minimallock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class SessionRule(val sessionMin: Int, val lockMin: Int)

/**
 * All settings and counters live in this app's private storage on the phone.
 * Nothing here is ever sent anywhere (the app has no network permission).
 * We store: your limits, your lists of apps, how many times you opened a limited app today,
 * until when a session-locked app stays locked, and whether the daily emergency unlock was used.
 * We do NOT store notification text, screen text, browsing, or any log of what you did.
 */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("minimal_lock", Context.MODE_PRIVATE)

    var defaultsDone: Boolean
        get() = p.getBoolean("defaultsDone", false)
        set(v) { p.edit().putBoolean("defaultsDone", v).apply() }

    var defaults2: Boolean
        get() = p.getBoolean("defaults2", false)
        set(v) { p.edit().putBoolean("defaults2", v).apply() }

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }

    /** Nothing is limited, blocked or filtered until the user presses Activate. */
    var active: Boolean
        get() = p.getBoolean("active", false)
        set(v) { p.edit().putBoolean("active", v).apply() }

    var phoneLimitMin: Int
        get() = p.getInt("phoneLimitMin", 180)
        set(v) { p.edit().putInt("phoneLimitMin", v).apply() }

    var blockShorts: Boolean
        get() = p.getBoolean("blockShorts", true)
        set(v) { p.edit().putBoolean("blockShorts", v).apply() }

    var frictionOn: Boolean
        get() = p.getBoolean("frictionOn", true)
        set(v) { p.edit().putBoolean("frictionOn", v).apply() }

    var notifFilterOn: Boolean
        get() = p.getBoolean("notifFilterOn", true)
        set(v) { p.edit().putBoolean("notifFilterOn", v).apply() }

    var digestHours: String
        get() = p.getString("digestHours", "9,13,18,21") ?: "9,13,18,21"
        set(v) { p.edit().putString("digestHours", v).apply() }

    // ---------- night block ----------

    var nightOn: Boolean
        get() = p.getBoolean("nightOn", true)
        set(v) { p.edit().putBoolean("nightOn", v).apply() }

    /** Minutes after midnight. Default 22:00. */
    var nightStartMin: Int
        get() = p.getInt("nightStartMin", 22 * 60)
        set(v) { p.edit().putInt("nightStartMin", v).apply() }

    /** Minutes after midnight. Default 05:00. */
    var nightEndMin: Int
        get() = p.getInt("nightEndMin", 5 * 60)
        set(v) { p.edit().putInt("nightEndMin", v).apply() }

    fun nightApps(): Set<String> = getSet("nightApps")
    fun setNightApps(s: Set<String>) = putSet("nightApps", s)

    private fun minuteOfDay(nowMs: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun isNight(nowMs: Long): Boolean {
        if (!nightOn) return false
        val m = minuteOfDay(nowMs)
        val s = nightStartMin
        val e = nightEndMin
        if (s == e) return false
        return if (s < e) (m >= s && m < e) else (m >= s || m < e)
    }

    /** Milliseconds until the next night starts (0 if it is night now). */
    fun msUntilNight(nowMs: Long): Long {
        if (!nightOn || isNight(nowMs)) return 0L
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        val m = minuteOfDay(nowMs)
        var d = nightStartMin - m
        if (d <= 0) d += 24 * 60
        return d * 60_000L - (c.get(Calendar.SECOND) * 1000L + c.get(Calendar.MILLISECOND))
    }

    // ---------- session lock ----------

    /** Minutes away from an app that end a "continuous session". */
    var sessionGapMin: Int
        get() = p.getInt("sessionGapMin", 3)
        set(v) { p.edit().putInt("sessionGapMin", v).apply() }

    fun sessionRules(): Map<String, SessionRule> {
        val out = LinkedHashMap<String, SessionRule>()
        try {
            val o = JSONObject(p.getString("sessionRules", "{}") ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val r = o.getJSONObject(k)
                out[k] = SessionRule(r.getInt("s"), r.getInt("l"))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun setSessionRule(pkg: String, sessionMin: Int, lockMin: Int) {
        val m = LinkedHashMap<String, SessionRule>(sessionRules())
        if (sessionMin <= 0) m.remove(pkg) else m[pkg] = SessionRule(sessionMin, maxOf(lockMin, 1))
        val o = JSONObject()
        for ((k, v) in m) {
            val r = JSONObject()
            r.put("s", v.sessionMin)
            r.put("l", v.lockMin)
            o.put(k, r)
        }
        p.edit().putString("sessionRules", o.toString()).apply()
    }

    private fun locks(): JSONObject {
        return try {
            JSONObject(p.getString("locks", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Until when (epoch millis) this app is locked after a long session. 0 if not locked. */
    fun lockUntil(pkg: String): Long = locks().optLong(pkg, 0L)

    fun setLock(pkg: String, until: Long) {
        val o = locks()
        val now = System.currentTimeMillis()
        val dead = ArrayList<String>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (o.optLong(k, 0L) < now) dead.add(k)
        }
        for (k in dead) o.remove(k)
        if (until > now) o.put(pkg, until)
        p.edit().putString("locks", o.toString()).apply()
    }

    // ---------- per-app limits (minutes per day) ----------

    fun appLimits(): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        try {
            val o = JSONObject(p.getString("appLimits", "{}") ?: "{}")
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = o.getInt(k)
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun setAppLimit(pkg: String, minutes: Int) {
        val m = LinkedHashMap(appLimits())
        if (minutes <= 0) m.remove(pkg) else m[pkg] = minutes
        val o = JSONObject()
        for ((k, v) in m) o.put(k, v)
        p.edit().putString("appLimits", o.toString()).apply()
    }

    // ---------- app lists ----------

    private fun getSet(key: String): Set<String> =
        HashSet(p.getStringSet(key, emptySet()) ?: emptySet())

    private fun putSet(key: String, s: Set<String>) {
        p.edit().putStringSet(key, HashSet(s)).apply()
    }

    /** Necessities that are never blocked and do not count toward the phone limit. */
    fun essentials(): Set<String> = getSet("essentials")
    fun setEssentials(s: Set<String>) = putSet("essentials", s)
    fun isEssential(pkg: String): Boolean = essentials().contains(pkg)

    /** Apps whose notifications are shown immediately (everything else is delayed to digest times). */
    fun notifAllow(): Set<String> = getSet("notifAllow")
    fun setNotifAllow(s: Set<String>) = putSet("notifAllow", s)

    fun favorites(): List<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(p.getString("favorites", "[]") ?: "[]")
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Exception) {
        }
        return out
    }

    fun setFavorites(list: List<String>) {
        val a = JSONArray()
        for (s in list) a.put(s)
        p.edit().putString("favorites", a.toString()).apply()
    }

    // ---------- day-scoped counters ----------

    private fun dayKey(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun opensObj(): JSONObject {
        val day = dayKey()
        if (p.getString("opensDay", "") != day) {
            p.edit().putString("opensDay", day).putString("opens", "{}").apply()
        }
        return try {
            JSONObject(p.getString("opens", "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    fun opensToday(pkg: String): Int = opensObj().optInt(pkg, 0)

    fun addOpen(pkg: String): Int {
        val o = opensObj()
        val n = o.optInt(pkg, 0) + 1
        o.put(pkg, n)
        p.edit().putString("opens", o.toString()).apply()
        return n
    }

    // ---------- emergency unlock (once per day, short pass) ----------

    fun emergencyAvailable(): Boolean = p.getString("emDay", "") != dayKey()

    fun useEmergency(minutes: Int) {
        p.edit()
            .putString("emDay", dayKey())
            .putLong("emUntil", System.currentTimeMillis() + minutes * 60_000L)
            .apply()
    }

    fun emergencyActive(): Boolean = System.currentTimeMillis() < p.getLong("emUntil", 0L)

    // ---------- debug capture of view ids (only used when you ask for it, only ids, no text) ----------

    fun getDump(pkg: String): String = p.getString("dump_$pkg", "") ?: ""
    fun setDump(pkg: String, s: String) {
        p.edit().putString("dump_$pkg", s).apply()
    }
}
