package app.minimallock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Simple round analog clock: ring, 12 ticks, hour and minute hands. No second hand (saves battery). */
class ClockView(ctx: Context) : View(ctx) {

    private fun stroke(w: Float): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND
        return p
    }

    private val ring = stroke(3f)
    private val tick = stroke(3f)
    private val hourHand = stroke(9f)
    private val minHand = stroke(5f)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Double, len: Float, p: Paint) {
        val a = Math.toRadians(deg)
        c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), p)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 6f
        c.drawCircle(cx, cy, r, ring)

        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val len = if (i % 3 == 0) r * 0.14f else r * 0.07f
            val x1 = cx + ((r - len) * sin(a)).toFloat()
            val y1 = cy - ((r - len) * cos(a)).toFloat()
            val x2 = cx + (r * sin(a)).toFloat()
            val y2 = cy - (r * cos(a)).toFloat()
            c.drawLine(x1, y1, x2, y2, tick)
        }

        val cal = Calendar.getInstance()
        val m = cal.get(Calendar.MINUTE)
        val h = cal.get(Calendar.HOUR)
        hand(c, cx, cy, h * 30.0 + m * 0.5, r * 0.55f, hourHand)
        hand(c, cx, cy, m * 6.0, r * 0.82f, minHand)
        c.drawCircle(cx, cy, 8f, dot)
    }
}
