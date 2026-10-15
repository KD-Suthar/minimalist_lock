package app.minimallock

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Settings. Before you press Activate they open freely. Once MinimalLock is active, opening them
 * costs a 20 second wait (then they stay open for 10 minutes), so you can't casually loosen your
 * own limits in a weak moment.
 */
class SettingsActivity : Activity() {

    companion object {
        private var unlockedUntil = 0L
        private const val GATE_SECONDS = 20
    }

    private val dlg = android.R.style.Theme_DeviceDefault_Dialog_Alert
    private lateinit var store: Store
    private lateinit var content: LinearLayout
    private var timer: CountDownTimer? = null
    private var wantExit = false
    private var built = false

    private fun dp(v: Int): Int = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        wantExit = intent.getBooleanExtra("exit", false)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content)
        setContentView(scroll)

        if (!store.active || System.currentTimeMillis() < unlockedUntil) {
            openSettings()
        } else {
            showGate()
        }
    }

    override fun onResume() {
        super.onResume()
        if (built) build()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ gate

    private fun serif(s: String, sp: Float, color: Int): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        t.typeface = Typeface.create("serif", Typeface.ITALIC)
        t.gravity = Gravity.CENTER
        return t
    }

    private fun full(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun showGate() {
        content.removeAllViews()
        content.setPadding(dp(32), dp(150), dp(32), dp(40))

        content.addView(serif("Changing settings", 30f, Color.WHITE), full())

        val l1 = serif("it's not that easy", 17f, Ui.GRAY)
        l1.setPadding(0, dp(18), 0, 0)
        content.addView(l1, full())

        val l2 = serif("it takes effort, and the time of your dreams", 17f, Ui.GRAY)
        l2.setPadding(0, dp(6), 0, 0)
        content.addView(l2, full())

        val count = TextView(this)
        count.setTextColor(Color.WHITE)
        count.textSize = 64f
        count.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        count.gravity = Gravity.CENTER
        count.setPadding(0, dp(44), 0, 0)
        count.text = GATE_SECONDS.toString()
        content.addView(count, full())

        val cancel = Ui.button(this, "Never mind") { finish() }
        val cLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cLp.gravity = Gravity.CENTER_HORIZONTAL
        cLp.topMargin = dp(40)
        content.addView(cancel, cLp)

        timer = object : CountDownTimer(GATE_SECONDS * 1000L, 1000) {
            override fun onTick(ms: Long) {
                count.text = (ms / 1000 + 1).toString()
            }

            override fun onFinish() {
                unlockedUntil = System.currentTimeMillis() + 10 * 60_000L
                openSettings()
            }
        }.start()
    }

    private fun openSettings() {
        build()
        if (wantExit) {
            wantExit = false
            Activate.exit(this)
        }
    }

    // ------------------------------------------------------------------ building blocks

    private fun header(s: String) {
        val t = Ui.text(this, s.uppercase(), 11f, Ui.GRAY, false)
        t.letterSpacing = 0.15f
        t.setPadding(0, dp(30), 0, dp(2))
        content.addView(t)
    }

    private fun row(title: String, sub: String, onClick: () -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(11), 0, dp(11))
        box.addView(Ui.text(this, title, 16f))
        if (sub.isNotEmpty()) box.addView(Ui.text(this, sub, 12f, Ui.GRAY))
        box.setOnClickListener { onClick() }
        content.addView(box)
    }

    private fun switchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.setPadding(0, dp(11), 0, dp(11))

        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        left.addView(Ui.text(this, title, 16f))
        if (sub.isNotEmpty()) left.addView(Ui.text(this, sub, 12f, Ui.GRAY))
        box.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val sw = Switch(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(130, 130, 130)))
        sw.trackTintList = ColorStateList(states, intArrayOf(Color.rgb(80, 150, 110), Color.rgb(55, 55, 55)))
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        box.addView(sw)
        content.addView(box)
    }

    // ------------------------------------------------------------------ main screen

    private fun build() {
        built = true
        content.removeAllViews()
        content.setPadding(dp(24), dp(48), dp(24), dp(40))
        content.addView(Ui.text(this, "Settings", 24f))

        header("Status")
        if (store.active) {
            row("Active", "Your limits are being enforced") { }
            row("Exit MinimalLock", "Turn it off and choose another home app") { Activate.exit(this) }
        } else {
            row("Not active", "Apps open normally. Set everything up here, then activate.") { }
            val b = Ui.button(this, "Activate") { Activate.request(this) { build() } }
            val bLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            bLp.topMargin = dp(8)
            content.addView(b, bLp)
        }

        header("Daily limits")
        val pl = store.phoneLimitMin
        row(
            "Phone",
            if (pl > 0) Ui.fmt(pl * 60_000L) + " per day, then locked until midnight" else "Off"
        ) { editPhoneLimit() }
        for ((pkg, m) in store.appLimits()) {
            row(Apps.label(this, pkg), "$m min per day") { editAppLimit(pkg) }
        }
        row("+ Add app limit", "That app locks for the rest of the day when its time is up") { addAppLimit() }
        row("Today's usage", "") { showUsage() }

        header("Session lock")
        for ((pkg, r) in store.sessionRules()) {
            row(
                Apps.label(this, pkg),
                "${r.sessionMin} min in a row, then locked for ${Ui.fmt(r.lockMin * 60_000L)}"
            ) { editSession(pkg) }
        }
        row("+ Add session rule", "Lock an app for a while after a long stretch in it") { addSession() }
        row(
            "Session break",
            "Leaving an app for more than ${store.sessionGapMin} min ends the session"
        ) { editGap() }

        header("Night block")
        switchRow("Close apps at night", "Selected apps cannot be opened during these hours", store.nightOn) {
            store.nightOn = it
        }
        row("From", Ui.hm(store.nightStartMin)) {
            pickTime("Night starts", store.nightStartMin) {
                store.nightStartMin = it
                build()
            }
        }
        row("Until", Ui.hm(store.nightEndMin)) {
            pickTime("Night ends", store.nightEndMin) {
                store.nightEndMin = it
                build()
            }
        }
        row("Apps closed at night", "${store.nightApps().size} apps") { pickNightApps() }

        header("Reels and Shorts")
        switchRow(
            "Block Reels and Shorts",
            "Sends you back when Instagram Reels or YouTube Shorts open",
            store.blockShorts
        ) { store.blockShorts = it }

        header("Pause")
        switchRow(
            "Pause before limited apps",
            "A wait that grows with each open, and a typed reason after a few",
            store.frictionOn
        ) { store.frictionOn = it }

        header("Notifications")
        switchRow(
            "Filter notifications",
            "Others are delayed to digest times",
            store.notifFilterOn
        ) { store.notifFilterOn = it }
        row("Apps that may notify immediately", "${store.notifAllow().size} apps besides necessities") {
            pickNotifAllow()
        }
        row("Digest times", "Delayed notifications reappear at: ${store.digestHours}") { editDigest() }

        header("Necessities (never blocked, not counted)")
        row("Choose necessities", "${store.essentials().size} apps") { pickEssentials() }

        header("Home screen")
        row("Favorites", "${store.favorites().size} apps, shown on the home screen") { pickFavorites() }

        header("Setup")
        row("Permissions", "") { startActivity(Intent(this, PermissionsActivity::class.java)) }

        header("Reels and Shorts detection tuning")
        row(
            "Capture screen ids",
            "Tap, then open Reels or Shorts and keep it on screen for 40 seconds"
        ) { startDump() }
        row("Show captured ids", "Only view ids, never text. Copy them if you need to share them") { showDump() }
    }

    // ------------------------------------------------------------------ dialogs

    private fun askNumbers(
        title: String,
        labels: List<String>,
        initial: List<Int>,
        removable: Boolean,
        onRemove: () -> Unit,
        onOk: (List<Int>) -> Unit
    ) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24), dp(8), dp(24), 0)
        val fields = ArrayList<EditText>()
        for (i in labels.indices) {
            box.addView(Ui.text(this, labels[i], 13f, Ui.GRAY))
            val et = EditText(this)
            et.inputType = InputType.TYPE_CLASS_NUMBER
            et.setText(initial[i].toString())
            et.setTextColor(Color.WHITE)
            et.setSelectAllOnFocus(true)
            fields.add(et)
            box.addView(et)
        }
        val b = AlertDialog.Builder(this, dlg).setTitle(title).setView(box)
            .setPositiveButton("Save") { _, _ ->
                onOk(fields.map { it.text.toString().trim().toIntOrNull() ?: 0 })
            }
            .setNegativeButton("Cancel", null)
        if (removable) b.setNeutralButton("Remove") { _, _ -> onRemove() }
        b.show()
    }

    private fun pickOne(title: String, apps: List<Apps.App>, onPick: (Apps.App) -> Unit) {
        AlertDialog.Builder(this, dlg).setTitle(title)
            .setItems(apps.map { it.label }.toTypedArray()) { _, i -> onPick(apps[i]) }
            .show()
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

    private fun pickTime(title: String, current: Int, onSet: (Int) -> Unit) {
        val d = TimePickerDialog(
            this,
            dlg,
            TimePickerDialog.OnTimeSetListener { _, h, m -> onSet(h * 60 + m) },
            current / 60,
            current % 60,
            true
        )
        d.setTitle(title)
        d.show()
    }

    private fun editPhoneLimit() {
        askNumbers(
            "Phone daily limit",
            listOf("Minutes per day (0 = off)"),
            listOf(store.phoneLimitMin),
            false,
            { }
        ) { v ->
            store.phoneLimitMin = v[0].coerceAtLeast(0)
            build()
        }
    }

    private fun addAppLimit() {
        val taken = store.appLimits().keys
        val ess = store.essentials()
        val apps = Apps.launchable(this).filter { it.pkg !in ess && it.pkg !in taken }
        pickOne("Limit which app?", apps) { editAppLimit(it.pkg) }
    }

    private fun editAppLimit(pkg: String) {
        val has = store.appLimits().containsKey(pkg)
        askNumbers(
            Apps.label(this, pkg),
            listOf("Minutes per day"),
            listOf(store.appLimits()[pkg] ?: 30),
            has,
            {
                store.setAppLimit(pkg, 0)
                build()
            }
        ) { v ->
            store.setAppLimit(pkg, v[0].coerceAtLeast(0))
            build()
        }
    }

    private fun addSession() {
        val taken = store.sessionRules().keys
        val ess = store.essentials()
        val apps = Apps.launchable(this).filter { it.pkg !in ess && it.pkg !in taken }
        pickOne("Session rule for which app?", apps) { editSession(it.pkg) }
    }

    private fun editSession(pkg: String) {
        val r = store.sessionRules()[pkg]
        askNumbers(
            Apps.label(this, pkg),
            listOf("Max minutes in one session", "Then lock it for (minutes)"),
            listOf(r?.sessionMin ?: 20, r?.lockMin ?: 90),
            r != null,
            {
                store.setSessionRule(pkg, 0, 0)
                build()
            }
        ) { v ->
            store.setSessionRule(pkg, v[0], v[1])
            build()
        }
    }

    private fun editGap() {
        askNumbers(
            "Session break",
            listOf("Minutes away that end a session"),
            listOf(store.sessionGapMin),
            false,
            { }
        ) { v ->
            store.sessionGapMin = v[0].coerceIn(1, 60)
            build()
        }
    }

    private fun pickNightApps() {
        val ess = store.essentials()
        val cand = Apps.launchable(this).filter { it.pkg !in ess }
        pickMany("Apps closed at night", cand, store.nightApps()) { sel ->
            store.setNightApps(sel)
            build()
        }
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
            "Now open Reels or Shorts and keep it on screen for 40 seconds",
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
