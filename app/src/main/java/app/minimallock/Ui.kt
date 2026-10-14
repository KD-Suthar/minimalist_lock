package app.minimallock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.TextView

fun EditText.onChange(cb: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            cb()
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })
}

object Ui {
    val GRAY: Int = Color.rgb(140, 140, 140)

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun text(
        ctx: Context,
        s: String,
        sp: Float = 16f,
        color: Int = Color.WHITE,
        light: Boolean = true
    ): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        t.typeface = Typeface.create(if (light) "sans-serif-light" else "sans-serif", Typeface.NORMAL)
        return t
    }

    fun button(ctx: Context, s: String, onClick: () -> Unit): TextView {
        val b = text(ctx, s, 16f, Color.WHITE, false)
        b.gravity = Gravity.CENTER
        b.setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12))
        val bg = GradientDrawable()
        bg.setColor(Color.TRANSPARENT)
        bg.setStroke(dp(ctx, 1), Color.WHITE)
        bg.cornerRadius = dp(ctx, 24).toFloat()
        b.background = bg
        b.setOnClickListener { onClick() }
        return b
    }

    fun setEnabledLook(b: TextView, on: Boolean) {
        b.isEnabled = on
        b.alpha = if (on) 1f else 0.35f
    }

    fun fmt(ms: Long): String {
        if (ms <= 0) return "0m"
        val totalMin = ms / 60000
        if (totalMin == 0L) return "<1m"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${h}h ${m.toString().padStart(2, '0')}m" else "${m}m"
    }

    fun register(ctx: Context, r: BroadcastReceiver, f: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(r, f)
        }
    }
}
