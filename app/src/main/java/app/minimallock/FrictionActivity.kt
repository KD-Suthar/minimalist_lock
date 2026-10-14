package app.minimallock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "pause" before a limited app opens. Wait grows with each open today (10s, 20s ... 60s).
 * From the 4th open of the day you also have to type a reason. Nothing you type is saved.
 */
class FrictionActivity : Activity() {

    private var timer: CountDownTimer? = null
    private var done = false
    private var needReason = false
    private var pkg = ""
    private lateinit var reason: EditText
    private lateinit var go: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra("pkg") ?: ""
        val store = Store(this)
        val n = store.addOpen(pkg)
        val waitSec = minOf(10 + 10 * (n - 1), 60)
        needReason = n >= 4

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32))

        root.addView(Ui.text(this, Apps.label(this, pkg), 30f))
        val opened = Ui.text(this, "Opened $n time" + (if (n == 1) "" else "s") + " today", 14f, Ui.GRAY)
        opened.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 24))
        root.addView(opened)
        root.addView(Ui.text(this, "Is this what you want to be doing right now?", 18f))

        val countdown = Ui.text(this, "Wait ${waitSec}s", 40f)
        countdown.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 20))
        root.addView(countdown)

        reason = EditText(this)
        reason.hint = "Why? (a few words)"
        reason.setTextColor(Color.WHITE)
        reason.setHintTextColor(Ui.GRAY)
        reason.setSingleLine(true)
        if (needReason) root.addView(reason)

        val notNow = Ui.button(this, "Not now") { goHome() }
        go = Ui.button(this, "Continue") { proceed() }
        Ui.setEnabledLook(go, false)

        val lp1 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp1.topMargin = Ui.dp(this, 24)
        root.addView(notNow, lp1)
        val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp2.topMargin = Ui.dp(this, 12)
        root.addView(go, lp2)

        reason.onChange { update() }
        setContentView(root)

        timer = object : CountDownTimer(waitSec * 1000L, 1000) {
            override fun onTick(ms: Long) {
                countdown.text = "Wait ${ms / 1000 + 1}s"
            }

            override fun onFinish() {
                done = true
                countdown.text = "Ready"
                update()
            }
        }.start()
    }

    private fun update() {
        val ok = done && (!needReason || reason.text.toString().trim().length >= 6)
        Ui.setEnabledLook(go, ok)
    }

    private fun proceed() {
        if (!go.isEnabled) return
        BlockerService.grantGrace(pkg, 3 * 60_000L)
        finish()
    }

    private fun goHome() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        finish()
    }

    @Deprecated("Back means not now")
    override fun onBackPressed() {
        goHome()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
