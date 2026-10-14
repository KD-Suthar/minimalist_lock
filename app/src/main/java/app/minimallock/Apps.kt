package app.minimallock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

object Apps {
    data class App(val pkg: String, val label: String)

    // Apps people typically cannot live without. Only those actually installed are used.
    private val known = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp", // BHIM
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.google.android.apps.maps",
        "com.ubercab",
        "com.olacabs.customer",
        "com.google.android.deskclock",
        "com.google.android.dialer",
        "com.google.android.apps.messaging"
    )

    // Name hints so banking / payment / utility apps show up in the "necessities" picker.
    private val keywords = listOf(
        "bank", "pay", "upi", "wallet", "authenticator", "maps", "clock",
        "dialer", "phone", "message", "camera", "settings", "contacts", "calculator"
    )

    fun launchable(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<App>()
        for (ri in pm.queryIntentActivities(i, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || !seen.add(pkg)) continue
            out.add(App(pkg, ri.loadLabel(pm).toString()))
        }
        out.sortBy { it.label.lowercase() }
        return out
    }

    fun label(ctx: Context, pkg: String): String {
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun launch(ctx: Context, pkg: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
        }
    }

    private fun resolve(ctx: Context, i: Intent): String? {
        val ri = ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        val p = ri?.activityInfo?.packageName
        return if (p == null || p == "android" || p == ctx.packageName) null else p
    }

    /** Dialer, SMS, camera, clock, settings, maps: whatever the phone uses for these. */
    private fun intentPkgs(ctx: Context): List<String> {
        val intents = listOf(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:123")),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
            Intent(AlarmClock.ACTION_SHOW_ALARMS),
            Intent(Settings.ACTION_SETTINGS),
            Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=cafe"))
        )
        return intents.mapNotNull { resolve(ctx, it) }
    }

    /** The only apps offered in the "necessities" picker, so it can't be filled with random apps. */
    fun essentialCandidates(ctx: Context): List<App> {
        val all = launchable(ctx)
        val viaIntent = intentPkgs(ctx).toSet()
        return all.filter { a ->
            a.pkg in viaIntent ||
                a.pkg in known ||
                keywords.any { k ->
                    a.label.lowercase().contains(k) || a.pkg.lowercase().contains(k)
                }
        }
    }

    fun ensureDefaults(ctx: Context, store: Store) {
        if (store.defaultsDone) return
        val launch = launchable(ctx).map { it.pkg }.toSet()
        if (launch.isEmpty()) return
        val viaIntent = intentPkgs(ctx)
        val ess = HashSet<String>()
        for (p in viaIntent) if (p in launch) ess.add(p)
        for (p in known) if (p in launch) ess.add(p)
        store.setEssentials(ess)
        store.setNotifAllow(ess)
        val favs = ArrayList<String>()
        for (p in viaIntent.take(3)) if (p in launch && p !in favs) favs.add(p)
        store.setFavorites(favs)
        store.defaultsDone = true
    }
}
