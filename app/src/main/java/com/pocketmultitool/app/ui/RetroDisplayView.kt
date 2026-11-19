package com.pocketmultitool.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.pocketmultitool.app.core.RetroConsole
import com.pocketmultitool.app.flipper.FlipperTheme
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import android.os.SystemClock
import kotlin.math.floor
import kotlin.math.min

class RetroDisplayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    lateinit var console: RetroConsole

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("flipper_zero_store", Context.MODE_PRIVATE)
    private val bgPaint = Paint()
    private val gridPaint = Paint()
    private val scanPaint = Paint().apply { color = Color.argb(18, 0, 0, 0) }
    private val headerPaint = Paint()
    private val linePaint = Paint()
    private val selectedPaint = Paint().apply { color = Color.argb(46, 255, 212, 0) }
    private val brightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
        setShadowLayer(7f, 0f, 0f, Color.argb(90, 166, 208, 138))
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val mascotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mascotDimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false }
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var paletteName = ""

    private var rowTop = 0f
    private var rowHeight = 0f
    private var firstVisible = 0
    private var visibleCount = 0

    private val dolphin = arrayOf(
        "..........##",
        ".........###",
        "........####",
        ".......#####",
        "......######",
        ".....#######",
        "....########",
        "...#########",
        "..##########",
        ".#####..####",
        "####.....###",
        "###.......##"
    )

    private fun applyPalette() {
        val name = prefs.getString("lcd_theme", "GREEN") ?: "GREEN"
        if (name == paletteName) return
        paletteName = name
        val palette = FlipperTheme.palette(name)
        bgPaint.color = Color.parseColor(palette.bg)
        gridPaint.color = Color.parseColor(palette.grid)
        headerPaint.color = Color.parseColor(palette.header)
        linePaint.color = Color.parseColor(palette.dim)
        brightPaint.color = Color.parseColor(palette.text)
        textPaint.color = Color.parseColor(palette.text)
        dimPaint.color = Color.parseColor(palette.dim)
        badgePaint.color = Color.parseColor(palette.accent)
        mascotPaint.color = Color.parseColor(palette.text)
        mascotDimPaint.color = Color.parseColor(palette.dim)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun baseline(paint: Paint, y: Float): Float = y + paint.textSize * 0.36f

    private fun fit(text: String, maxWidth: Float): String {
        if (textPaint.measureText(text) <= maxWidth) return text
        var result = text
        while (result.isNotEmpty() && textPaint.measureText("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result…"
    }

    fun rowAt(y: Float): Int {
        if (visibleCount == 0 || y < rowTop) return -1
        val index = floor((y - rowTop) / rowHeight).toInt()
        return if (index in 0 until visibleCount) firstVisible + index else -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || !::console.isInitialized) return
        applyPalette()

        canvas.drawRect(0f, 0f, w, h, bgPaint)
        drawMatrix(canvas, w, h)

        val headerH = dp(52f)
        val footerH = dp(34f)
        canvas.drawRect(0f, 0f, w, headerH, headerPaint)
        canvas.drawLine(0f, headerH, w, headerH, linePaint)
        canvas.drawLine(0f, h - footerH, w, h - footerH, linePaint)

        val screen = console.current()
        val rows = console.rows()
        val pad = dp(12f)

        textPaint.textSize = dp(13f)
        dimPaint.textSize = dp(10f)
        brightPaint.textSize = dp(17f)
        badgePaint.textSize = dp(10f)

        val titleWidth = w - dp(84f)
        canvas.drawText(fit(screen.title, titleWidth), pad, baseline(brightPaint, dp(19f)), brightPaint)
        val sub = screen.subtitle.ifEmpty { "FLIPPER ZERO // POCKET EDITION" }
        canvas.drawText(fit(sub, titleWidth), pad, baseline(dimPaint, dp(39f)), dimPaint)
        dimPaint.textSize = dp(10f)
        drawStatusIcons(canvas, w)
        drawMascot(canvas, w - dp(48f), dp(26f), dp(2.1f))

        var top = headerH + dp(10f)
        val bottom = h - footerH - dp(8f)
        val picture = screen.bitmap
        if (picture != null && !picture.isRecycled) {
            val maxBitmapH = min((bottom - top) * 0.68f, dp(250f))
            val scale = min((w - pad * 2) / picture.width.toFloat(), maxBitmapH / picture.height.toFloat())
            val bw = picture.width * scale
            val bh = picture.height * scale
            val dst = RectF((w - bw) / 2f, top, (w + bw) / 2f, top + bh)
            canvas.drawBitmap(picture, null, dst, bitmapPaint)
            top = dst.bottom + dp(8f)
        }

        rowHeight = dp(if (picture != null) 21f else 29f)
        visibleCount = maxOf(0, floor((bottom - top) / rowHeight).toInt())
        if (console.selection >= rows.size) console.resetSelection()
        if (console.selection < console.scroll) console.scroll = console.selection
        if (visibleCount > 0 && console.selection >= console.scroll + visibleCount) {
            console.scroll = console.selection - visibleCount + 1
        }
        if (console.scroll < 0) console.scroll = 0
        firstVisible = console.scroll
        rowTop = top

        textPaint.textSize = dp(if (picture != null) 11f else 13f)
        brightPaint.textSize = dp(if (picture != null) 12f else 14f)
        val valueRight = w - dp(10f)

        for (i in 0 until visibleCount) {
            val index = firstVisible + i
            if (index >= rows.size) break
            val row = rows[index]
            val y = top + i * rowHeight
            val selected = index == console.selection
            if (selected) {
                canvas.drawRect(pad - dp(4f), y, w - pad + dp(4f), y + rowHeight - dp(2f), selectedPaint)
            }
            val activePaint = if (selected) brightPaint else if (row.enabled) textPaint else dimPaint
            if (selected) canvas.drawText(">", pad - dp(2f), baseline(activePaint, y + rowHeight * 0.55f), activePaint)
            val labelX = pad + dp(16f)
            val rightReserve = if (row.value.isNotEmpty() || row.badge.isNotEmpty()) dp(110f) else dp(4f)
            canvas.drawText(
                fit(row.label, w - labelX - rightReserve),
                labelX,
                baseline(activePaint, y + rowHeight * 0.55f),
                activePaint,
            )
            var valueX = valueRight
            if (row.badge.isNotEmpty()) {
                canvas.drawText(fit(row.badge, dp(54f)), valueX - textPaint.measureText(row.badge), baseline(badgePaint, y + rowHeight * 0.55f), badgePaint)
                valueX -= dp(62f)
            }
            if (row.value.isNotEmpty()) {
                val value = fit(row.value, dp(86f))
                canvas.drawText(value, valueX - textPaint.measureText(value), baseline(activePaint, y + rowHeight * 0.55f), activePaint)
            }
        }

        if (rows.isEmpty() && picture == null) {
            dimPaint.textSize = dp(12f)
            canvas.drawText("НЕТ ДАННЫХ", w / 2f - textPaint.measureText("НЕТ ДАННЫХ") / 2f, top + dp(24f), dimPaint)
        }

        dimPaint.textSize = dp(9f)
        val progress = if (rows.isEmpty()) "00/00" else "${console.selection + 1}/${rows.size}".padStart(5, '0')
        val footerText = "${screen.footer}  [$progress]"
        canvas.drawText(fit(footerText, w - pad * 2), w / 2f - min(textPaint.measureText(footerText), w - pad * 2) / 2f, h - dp(11f), dimPaint)
    }

    private fun drawMatrix(canvas: Canvas, w: Float, h: Float) {
        val step = dp(13f)
        var x = step
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += step
        }
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += step
        }
        y = dp(3f)
        while (y < h) {
            canvas.drawLine(0f, y, w, y, scanPaint)
            y += dp(4f)
        }
    }

    private fun drawStatusIcons(canvas: Canvas, w: Float) {
        val y = dp(12f)
        dimPaint.textSize = dp(9f)
        canvas.drawText(clock.format(Date()), w - dp(112f), baseline(dimPaint, y), dimPaint)
        badgePaint.textSize = dp(8f)
        canvas.drawText("SD", w - dp(86f), baseline(badgePaint, y), badgePaint)
        canvas.drawText("B", w - dp(62f), baseline(badgePaint, y), badgePaint)
        drawBattery(canvas, w - dp(36f), y - dp(8f), dp(24f), dp(11f))
        dimPaint.textSize = dp(10f)
        badgePaint.textSize = dp(10f)
    }

    private fun drawBattery(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(FlipperTheme.YELLOW)
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        canvas.drawRect(x, y, x + w, y + h, stroke)
        canvas.drawRect(x + w, y + h * 0.25f, x + w + dp(2f), y + h * 0.75f, stroke)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(FlipperTheme.YELLOW) }
        canvas.drawRect(x + dp(2f), y + dp(2f), x + w * 0.72f, y + h - dp(2f), fill)
    }

    private fun drawMascot(canvas: Canvas, x: Float, y: Float, px: Float) {
        for (r in dolphin.indices) {
            val line = dolphin[r]
            for (c in line.indices) {
                if (line[c] == '.') continue
                val paint = if (line[c] == '#') mascotPaint else mascotDimPaint
                val left = x + c * px
                val top = y + r * px
                canvas.drawRect(left, top, left + px * 0.86f, top + px * 0.86f, paint)
            }
        }
        val eyeX = x + 9 * px
        val eyeY = y + 3 * px
        if (SystemClock.uptimeMillis() % 3600L < 140L) {
            canvas.drawLine(eyeX, eyeY + px * 0.2f, eyeX + px * 0.45f, eyeY + px * 0.2f, mascotPaint)
        } else {
            canvas.drawRect(eyeX, eyeY, eyeX + px * 0.35f, eyeY + px * 0.35f, bgPaint)
        }
    }
}
