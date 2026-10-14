package app.minimallock

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * Settings. Opening it costs a 15 second wait (then it stays open for 10 minutes),
 * so you can't casually loosen your own limits in a weak moment.
 */
class SettingsActivity : Activity() {

    companion object {
        private var unlockedUntil = 0L
    }

    private val dlg = android.R.style.Theme_DeviceDefault_Dialog_Alert
    private lateinit var store: Store
    private lateinit var content: LinearLayout
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(Ui.dp(this, 24), Ui.dp(this, 48), Ui.dp(this, 24), Ui.dp(this, 40))
        scroll.addView(content)
        setContentView(scroll)

        if (System.currentTimeMillis() < unlockedUntil) {
            build()
            return
        }
        val gate = Ui.text(this, "", 18f)
        content.addView(gate)
        timer = object : CountDownTimer(15_000, 1000) {
            override fun onTick(ms: Long) {
                gate.text = "Changing your limits should take some effort.\n\nWait ${ms / 1000 + 1}s..."
            }

            override fun onFinish() {
                unlockedUntil = System.currentTimeMillis() + 10 * 60_000L
                build()
            }
        }.start()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ UI

    private fun header(s: String) {
        val t = Ui.text(this, s.uppercase(), 12f, Ui.GRAY)
        t.setPadding(0, Ui.dp(this, 28), 0, 0)
        content.addView(t)
    }

    private fun row(title: String, sub: String, onClick: () -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12))
        box.addView(Ui.text(this, title, 18f))
        if (sub.isNotEmpty()) box.addView(Ui.text(this, sub, 13f, Ui.GRAY))
        box.setOnClickListener { onClick() }
        content.addView(box)
    }

    private fun onOff(b: Boolean): String = if (b) "On" else "Off"

    private fun build() {
        content.removeAllViews()
        content.addView(Ui.text(this, "Settings", 28f))

        header("Limits")
        val pl = store.phoneLimitMin
        row("Phone daily limit", if (pl > 0) Ui.fmt(pl * 60_000L) + " per day" else "Off") { pickPhoneLimit() }
        for ((pkg, m) in store.appLimits()) {
            row(Apps.label(this, pkg), "$m min per day") { pickMinutes(pkg) }
        }
        row("+ Add app limit", "Locks that app for the rest of the day when time is up") { addAppLimit() }
        row("Today's usage", "") { showUsage() }

        header("Friction and blocking")
        row("Pause before limited apps", onOff(store.frictionOn)) {
            store.frictionOn = !store.frictionOn
            build()
        }
        row("Block Shorts and Reels", onOff(store.blockShorts)) {
            store.blockShorts = !store.blockShorts
            build()
        }

        header("Necessities (never blocked, not counted)")
        row("Choose necessities", "${store.essentials().size} apps") { pickEssentials() }

        header("Home screen")
        row("Favorites", "${store.favorites().size} apps, shown on the home screen") { pickFavorites() }

        header("Notifications")
        row("Filter notifications", onOff(store.notifFilterOn)) {
            store.notifFilterOn = !store.notifFilterOn
            build()
        }
        row("Apps that may notify immediately", "${store.notifAllow().size} apps besides necessities") {
            pickNotifAllow()
        }
        row("Digest times", "Delayed notifications reappear at: ${store.digestHours}") { editDigest() }

        header("Other")
        row("Permissions and setup", "") { startActivity(Intent(this, PermissionsActivity::class.java)) }

        header("Shorts / Reels detection tuning")
        row(
            "Capture screen ids",
            "Tap, then open Reels or Shorts and keep it on screen for 25 seconds"
        ) { startDump() }
        row("Show captured ids", "Only view ids, never text. Copy them if you need to share them") { showDump() }
    }

    // ------------------------------------------------------------------ dialogs

    private fun pickPhoneLimit() {
        val labels = arrayOf("1 h", "1.5 h", "2 h", "2.5 h", "3 h", "4 h", "5 h", "Off")
        val values = intArrayOf(60, 90, 120, 150, 180, 240, 300, 0)
        AlertDialog.Builder(this, dlg).setTitle("Phone daily limit")
            .setItems(labels) { _, i ->
                store.phoneLimitMin = values[i]
                build()
            }
            .show()
    }

    private fun addAppLimit() {
        val lim = store.appLimits().keys
        val ess = store.essentials()
        val apps = Apps.launchable(this).filter { it.pkg !in ess && it.pkg !in lim }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this, dlg).setTitle("Limit which app?")
            .setItems(labels) { _, i -> pickMinutes(apps[i].pkg) }
            .show()
    }

    private fun pickMinutes(pkg: String) {
        val values = intArrayOf(10, 15, 20, 30, 45, 60, 90, 120)
        val labels = values.map { "$it min per day" }.toTypedArray()
        val b = AlertDialog.Builder(this, dlg).setTitle(Apps.label(this, pkg))
            .setItems(labels) { _, i ->
                store.setAppLimit(pkg, values[i])
                build()
            }
        if (store.appLimits().containsKey(pkg)) {
            b.setNeutralButton("Remove limit") { _, _ ->
                store.setAppLimit(pkg, 0)
                build()
            }
        }
        b.show()
    }

    private fun pickMany(
        title: String,
        apps: List<Apps.App>,
        selected: Set<String>,
        onDone: (Set<String>) -> Unit
    ) {
        val labels = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].pkg in selected }
        AlertDialog.Builder(this, dlg).setTitle(title)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Save") { _, _ ->
                val out = HashSet<String>()
                for (i in apps.indices) if (checked[i]) out.add(apps[i].pkg)
                onDone(out)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickEssentials() {
        val all = Apps.launchable(this)
        val cand = ArrayList(Apps.essentialCandidates(this))
        for (a in all) {
            if (a.pkg in store.essentials() && cand.none { it.pkg == a.pkg }) cand.add(a)
        }
        cand.sortBy { it.label.lowercase() }
        pickMany("Necessities", cand, store.essentials()) { sel ->
            store.setEssentials(sel)
            build()
        }
    }

    private fun pickFavorites() {
        val all = Apps.launchable(this)
        val current = store.favorites()
        pickMany("Favorites (max 6)", all, current.toSet()) { sel ->
            val ordered = ArrayList<String>()
            for (p in current) if (p in sel) ordered.add(p)
            for (p in sel) if (p !in ordered) ordered.add(p)
            store.setFavorites(ordered.take(6))
            build()
        }
    }

    private fun pickNotifAllow() {
        pickMany("Notify immediately", Apps.launchable(this), store.notifAllow()) { sel ->
            store.setNotifAllow(sel)
            build()
        }
    }

    private fun editDigest() {
        val et = EditText(this)
        et.setText(store.digestHours)
        et.inputType = InputType.TYPE_CLASS_TEXT
        et.setTextColor(Color.WHITE)
        AlertDialog.Builder(this, dlg).setTitle("Digest hours (24h, comma separated)")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                store.digestHours = et.text.toString()
                build()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUsage() {
        if (!Perms.usage(this)) {
            Toast.makeText(this, "Grant usage access first", Toast.LENGTH_SHORT).show()
            return
        }
        val r = Usage.compute(this, store.essentials())
        val sb = StringBuilder("Counted toward your phone limit: ${Ui.fmt(r.total)}\n\n")
        r.perApp.entries
            .sortedByDescending { it.value }
            .filter { it.value > 60_000L }
            .take(10)
            .forEach { sb.append("${Apps.label(this, it.key)}   ${Ui.fmt(it.value)}\n") }
        AlertDialog.Builder(this, dlg).setTitle("Today")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun startDump() {
        val svc = BlockerService.instance
        if (svc == null) {
            Toast.makeText(this, "Turn on the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }
        svc.requestDump()
        Toast.makeText(
            this,
            "Now open Reels or Shorts and keep it on screen for 25 seconds",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showDump() {
        val yt = store.getDump(BlockerService.YT).ifEmpty { "(nothing captured)" }
        val ig = store.getDump(BlockerService.IG).ifEmpty { "(nothing captured)" }
        val t = "YouTube:\n$yt\n\nInstagram:\n$ig"
        AlertDialog.Builder(this, dlg).setTitle("Captured view ids")
            .setMessage(t)
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("ids", t))
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
