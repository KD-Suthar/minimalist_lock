package app.minimallock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.format.DateFormat
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Round analog clock with the day and the digital time written inside the dial.
 * Text is drawn last with a black outline so the hands never hide it. No second hand (saves battery).
 */
class ClockView(ctx: Context) : View(ctx) {

    private val d = ctx.resources.displayMetrics.density
    private val gray = Color.rgb(165, 165, 165)

    private fun stroke(w: Float, alpha: Int = 255): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.alpha = alpha
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * d
        p.strokeCap = Paint.Cap.ROUND
        return p
    }

    private val ring = stroke(2.5f)
    private val tickLong = stroke(3.5f)
    private val tickShort = stroke(1.8f, 150)
    private val hourHand = stroke(6f)
    private val minHand = stroke(3.5f)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val face = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = face
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        textAlign = Paint.Align.CENTER
        typeface = face
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Double, len: Float, p: Paint) {
        val a = Math.toRadians(deg)
        c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), p)
    }

    private fun label(c: Canvas, s: String, x: Float, baseline: Float, size: Float, color: Int, spacing: Float) {
        txt.textSize = size
        txt.color = color
        txt.letterSpacing = spacing
        outline.textSize = size
        outline.letterSpacing = spacing
        outline.strokeWidth = size * 0.30f
        c.drawText(s, x, baseline, outline)
        c.drawText(s, x, baseline, txt)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 6f * d

        c.drawCircle(cx, cy, r, ring)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val long = i % 3 == 0
            val len = if (long) r * 0.12f else r * 0.06f
            val x1 = cx + ((r - len) * sin(a)).toFloat()
            val y1 = cy - ((r - len) * cos(a)).toFloat()
            val x2 = cx + (r * sin(a)).toFloat()
            val y2 = cy - (r * cos(a)).toFloat()
            c.drawLine(x1, y1, x2, y2, if (long) tickLong else tickShort)
        }

        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MINUTE)
        val h12 = cal.get(Calendar.HOUR)
        hand(c, cx, cy, h12 * 30.0 + m * 0.5, r * 0.50f, hourHand)
        hand(c, cx, cy, m * 6.0, r * 0.76f, minHand)
        c.drawCircle(cx, cy, 4f * d, dot)

        // digital time and day, inside the dial
        val is24 = DateFormat.is24HourFormat(context)
        val timeStr: String
        var sub: String
        val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(cal.time).uppercase()
        if (is24) {
            timeStr = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), m)
            sub = date
        } else {
            val h = if (h12 == 0) 12 else h12
            timeStr = String.format(Locale.US, "%d:%02d", h, m)
            val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            sub = "$ampm  ·  $date"
        }
        val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time).uppercase()

        label(c, day, cx, cy - r * 0.30f, r * 0.105f, gray, 0.2f)
        label(c, timeStr, cx, cy + r * 0.56f, r * 0.30f, Color.WHITE, 0f)
        label(c, sub, cx, cy + r * 0.74f, r * 0.095f, gray, 0.12f)
    }
}
