package app.minimallock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * All settings and counters live in this app's private storage on the phone.
 * Nothing here is ever sent anywhere (the app has no network permission).
 * We store: your limits, your lists of apps, how many times you opened a limited app today,
 * and whether the daily emergency unlock was used. We do NOT store notification text,
 * screen text, browsing, or any log of what you did.
 */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("minimal_lock", Context.MODE_PRIVATE)

    var defaultsDone: Boolean
        get() = p.getBoolean("defaultsDone", false)
        set(v) { p.edit().putBoolean("defaultsDone", v).apply() }

    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }

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
