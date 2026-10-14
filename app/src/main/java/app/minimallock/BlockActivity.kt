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

/** Shown when an app (or the whole phone) is out of time for today. */
class BlockActivity : Activity() {

    private var timer: CountDownTimer? = null
    private var timeUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val why = intent.getStringExtra("why") ?: "app"

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32), Ui.dp(this, 32))

        root.addView(Ui.text(this, "Done for today", 30f))
        val msg = if (why == "phone") {
            "Your phone time for today is used up."
        } else {
            Apps.label(this, pkg) + ": today's limit is used up."
        }
        val m = Ui.text(this, msg, 17f)
        m.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 4))
        root.addView(m)
        root.addView(Ui.text(this, "Back tomorrow. Resets at midnight.", 14f, Ui.GRAY))

        val ok = Ui.button(this, "OK") { goHome() }
        val okLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        okLp.topMargin = Ui.dp(this, 32)
        root.addView(ok, okLp)

        if (store.emergencyAvailable()) {
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
