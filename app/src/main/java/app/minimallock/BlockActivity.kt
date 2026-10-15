package app.minimallock

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Shown when an app (or the whole phone) is closed: daily limit, session lock, or night block. */
class BlockActivity : Activity() {

    private var timer: CountDownTimer? = null
    private var timeUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val why = intent.getStringExtra("why") ?: "app"
        val name = Apps.label(this, pkg)

        val title: String
        val msg: String
        val sub: String
        when (why) {
            "session" -> {
                title = "Take a break"
                msg = "$name is on a break.\nBack at ${Ui.clockTime(store.lockUntil(pkg))}"
                sub = "You were in it for a long stretch."
            }
            "night" -> {
                title = "Good night"
                msg = "$name is closed for the night.\nBack at ${Ui.hm(store.nightEndMin)}"
                sub = "Night block is on."
            }
            "phone" -> {
                title = "Done for today"
                msg = "Your phone time for today is used up."
                sub = "Back tomorrow. Resets at midnight."
            }
            else -> {
                title = "Done for today"
                msg = "$name: today's limit is used up."
                sub = "Back tomorrow. Resets at midnight."
            }
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32))

        val titleTv = Ui.text(this, title, 28f)
        titleTv.gravity = Gravity.CENTER
        root.addView(titleTv)
        val m = Ui.text(this, msg, 16f)
        m.gravity = Gravity.CENTER
        m.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4))
        root.addView(m)
        val s = Ui.text(this, sub, 13f, Ui.GRAY)
        s.gravity = Gravity.CENTER
        root.addView(s)

        val ok = Ui.button(this, "OK") { goHome() }
        val okLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        okLp.topMargin = Ui.dp(this, 32)
        root.addView(ok, okLp)

        // The emergency pass is for real emergencies; it never opens the night block.
        if (why != "night" && store.emergencyAvailable()) {
            val phrase = "I need this now"
            val panel = LinearLayout(this)
            panel.orientation = LinearLayout.VERTICAL
            panel.visibility = View.GONE

            val info = Ui.text(this, "", 14f, Ui.GRAY)
            val typed = EditText(this)
            typed.hint = phrase
            typed.setTextColor(Color.WHITE)
            typed.setHintTextColor(Color.rgb(80, 80, 80))
            typed.setSingleLine(true)
            val confirm: TextView = Ui.button(this, "Unlock for 5 minutes") {
                store.useEmergency(5)
                // A session lock must not outlive the pass.
                store.setLock(pkg, 0L)
                Toast.makeText(this, "Unlocked for 5 minutes", Toast.LENGTH_SHORT).show()
                finish()
            }
            Ui.setEnabledLook(confirm, false)

            val refresh = {
                Ui.setEnabledLook(
                    confirm,
                    timeUp && typed.text.toString().trim().equals(phrase, ignoreCase = true)
                )
            }
            typed.onChange { refresh() }

            panel.addView(info)
            panel.addView(typed)
            val cLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cLp.topMargin = Ui.dp(this, 12)
            panel.addView(confirm, cLp)

            val open = Ui.button(this, "Emergency unlock (once a day)") {
                if (panel.visibility == View.GONE) {
                    panel.visibility = View.VISIBLE
                    timer = object : CountDownTimer(60_000, 1000) {
                        override fun onTick(ms: Long) {
                            info.text = "Wait ${ms / 1000 + 1}s, then type: $phrase"
                        }

                        override fun onFinish() {
                            timeUp = true
                            info.text = "Now type: $phrase"
                            refresh()
                        }
                    }.start()
                }
            }
            val oLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            oLp.topMargin = Ui.dp(this, 16)
            root.addView(open, oLp)
            root.addView(panel)
        }

        setContentView(root)
    }

    private fun goHome() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        finish()
    }

    @Deprecated("Back means go home")
    override fun onBackPressed() {
        goHome()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
