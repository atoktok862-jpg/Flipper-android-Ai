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
import com.pocketmultitool.app.core.ConsoleRow
import com.pocketmultitool.app.core.RetroConsole
import com.pocketmultitool.app.flipper.FlipperTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

class RetroDisplayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    lateinit var console: RetroConsole

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("flipper_zero_store", Context.MODE_PRIVATE)
    private val bgPaint = Paint()
    private val headerPaint = Paint()
    private val linePaint = Paint()
    private val cellPaint = Paint().apply { style = Paint.Style.STROKE }
    private val selectionPaint = Paint()
    private val textPaint = Paint().apply { typeface = Typeface.MONOSPACE }
    private val titlePaint = Paint().apply { typeface = Typeface.MONOSPACE }
    private val dimPaint = Paint().apply { typeface = Typeface.MONOSPACE }
    private val badgePaint = Paint().apply { typeface = Typeface.MONOSPACE }
    private val inversePaint = Paint().apply { typeface = Typeface.MONOSPACE }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var paletteName = ""

    private var rowTop = 0f
    private var rowHeight = 0f
    private var firstVisible = 0
    private var visibleCount = 0

    private var gridMode = false
    private var gridLeft = 0f
    private var gridTop = 0f
    private var gridCellWidth = 0f
    private var gridCellHeight = 0f
    private var gridColumns = 3
    private var gridCount = 0

    private fun applyPalette() {
        val name = prefs.getString("lcd_theme", "GREEN") ?: "GREEN"
        if (name == paletteName) return
        paletteName = name
        val palette = FlipperTheme.palette(name)
        bgPaint.color = Color.parseColor(palette.bg)
        headerPaint.color = Color.parseColor(palette.header)
        linePaint.color = Color.parseColor(palette.dim)
        cellPaint.color = Color.parseColor(palette.dim)
        cellPaint.strokeWidth = dp(1f)
        selectionPaint.color = Color.parseColor(palette.text)
        textPaint.color = Color.parseColor(palette.text)
        titlePaint.color = Color.parseColor(palette.text)
        dimPaint.color = Color.parseColor(palette.dim)
        badgePaint.color = Color.parseColor(palette.text)
        inversePaint.color = Color.parseColor(palette.bg)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun baseline(paint: Paint, y: Float): Float = y + paint.textSize * 0.34f

    private fun fit(text: String, maxWidth: Float): String {
        if (textPaint.measureText(text) <= maxWidth) return text
        var result = text
        while (result.isNotEmpty() && textPaint.measureText("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result…"
    }

    fun rowAt(y: Float): Int {
        if (gridMode) return -1
        if (visibleCount == 0 || y < rowTop) return -1
        val index = floor((y - rowTop) / rowHeight).toInt()
        return if (index in 0 until visibleCount) firstVisible + index else -1
    }

    fun itemAt(x: Float, y: Float): Int {
        if (!gridMode) return rowAt(y)
        val column = floor((x - gridLeft) / gridCellWidth).toInt()
        val row = floor((y - gridTop) / gridCellHeight).toInt()
        if (column !in 0 until gridColumns || row < 0) return -1
        val index = row * gridColumns + column
        return if (index < gridCount) index else -1
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0 || !::console.isInitialized) return
        applyPalette()

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val headerH = dp(26f)
        val footerH = dp(18f)
        canvas.drawRect(0f, 0f, w, headerH, headerPaint)
        canvas.drawLine(0f, headerH, w, headerH, linePaint)
        canvas.drawLine(0f, h - footerH, w, h - footerH, linePaint)

        val screen = console.current()
        val rows = console.rows()
        val pad = dp(9f)

        titlePaint.textSize = dp(11f)
        dimPaint.textSize = dp(8f)
        badgePaint.textSize = dp(7f)
        textPaint.textSize = dp(11f)

        val titleWidth = w - dp(116f)
        canvas.drawText(fit(screen.title, titleWidth), pad, baseline(titlePaint, dp(10f)), titlePaint)
        val sub = screen.subtitle.ifEmpty { "FLIPPER ZERO POCKET" }
        canvas.drawText(fit(sub, titleWidth), pad, baseline(dimPaint, dp(20f)), dimPaint)
        drawStatusIcons(canvas, w)

        var top = headerH + dp(5f)
        val bottom = h - footerH - dp(4f)
        val picture = screen.bitmap
        if (picture != null && !picture.isRecycled) {
            val maxBitmapH = min((bottom - top) * 0.72f, dp(220f))
            val scale = min((w - pad * 2) / picture.width.toFloat(), maxBitmapH / picture.height.toFloat())
            val bw = picture.width * scale
            val bh = picture.height * scale
            val dst = RectF((w - bw) / 2f, top, (w + bw) / 2f, top + bh)
            canvas.drawBitmap(picture, null, dst, bitmapPaint)
            top = dst.bottom + dp(5f)
        }

        if (console.selection >= rows.size) console.resetSelection()

        val useGrid = picture == null && rows.size >= 6 && screen.title == "FLIPPER ZERO"
        gridMode = useGrid

        if (useGrid) {
            console.scroll = 0
            firstVisible = 0
            visibleCount = rows.size
            rowTop = top
            drawGridMenu(canvas, rows, top, bottom, pad, w)
        } else {
            rowHeight = dp(if (picture != null) 15f else 21f)
            visibleCount = maxOf(0, floor((bottom - top) / rowHeight).toInt())
            if (console.selection < console.scroll) console.scroll = console.selection
            if (visibleCount > 0 && console.selection >= console.scroll + visibleCount) {
                console.scroll = console.selection - visibleCount + 1
            }
            if (console.scroll < 0) console.scroll = 0
            firstVisible = console.scroll
            rowTop = top

            textPaint.textSize = dp(if (picture != null) 9f else 11f)
            titlePaint.textSize = dp(if (picture != null) 10f else 11f)
            inversePaint.textSize = textPaint.textSize
            badgePaint.textSize = dp(8f)
            val valueRight = w - dp(7f)

            for (i in 0 until visibleCount) {
                val index = firstVisible + i
                if (index >= rows.size) break
                val row = rows[index]
                val y = top + i * rowHeight
                val selected = index == console.selection
                if (selected) {
                    canvas.drawRect(pad - dp(3f), y, w - pad + dp(3f), y + rowHeight - dp(1f), selectionPaint)
                }
                val activePaint = if (selected) inversePaint else if (row.enabled) textPaint else dimPaint
                val labelX = pad + dp(3f)
                val rightReserve = when {
                    row.value.isNotEmpty() && row.badge.isNotEmpty() -> dp(112f)
                    row.value.isNotEmpty() || row.badge.isNotEmpty() -> dp(66f)
                    else -> dp(4f)
                }
                val lineY = baseline(activePaint, y + rowHeight * 0.62f)
                canvas.drawText(fit(row.label, w - labelX - rightReserve), labelX, lineY, activePaint)
                var valueX = valueRight
                if (row.badge.isNotEmpty()) {
                    val badge = fit(row.badge, dp(42f))
                    canvas.drawText(badge, valueX - badgePaint.measureText(badge), lineY, if (selected) inversePaint else badgePaint)
                    valueX -= dp(46f)
                }
                if (row.value.isNotEmpty()) {
                    val value = fit(row.value, dp(58f))
                    canvas.drawText(value, valueX - activePaint.measureText(value), lineY, activePaint)
                }
            }
        }

        if (rows.isEmpty() && picture == null) {
            dimPaint.textSize = dp(10f)
            canvas.drawText("НЕТ ДАННЫХ", w / 2f - dimPaint.measureText("НЕТ ДАННЫХ") / 2f, top + dp(18f), dimPaint)
        }

        dimPaint.textSize = dp(8f)
        val footerSource = if (gridMode) "◀▶ РАЗДЕЛ   OK ВХОД   BACK НАЗАД" else screen.footer
        val footerText = fit(footerSource, w - pad * 2)
        canvas.drawText(footerText, (w - dimPaint.measureText(footerText)) / 2f, h - dp(5f), dimPaint)
    }

    private fun gridGlyph(label: String): String = when {
        label.contains("NFC", ignoreCase = true) -> "NFC"
        label.contains("INFRARED", ignoreCase = true) -> "IR"
        label.contains("СКАНЕР", ignoreCase = true) -> "RF"
        label.contains("ПРИЛОЖЕНИЯ", ignoreCase = true) -> "APPS"
        label.contains("АРХИВ", ignoreCase = true) -> "ARC"
        label.contains("ДОЛФИН", ignoreCase = true) -> "DFL"
        label.contains("НАСТРОЙКИ", ignoreCase = true) -> "SET"
        label.contains("ДИАГНОСТИКА", ignoreCase = true) -> "SYS"
        label.contains("ПРОГРАММЕ", ignoreCase = true) -> "INFO"
        else -> label.take(4)
    }

    private fun drawGridMenu(canvas: Canvas, rows: List<ConsoleRow>, top: Float, bottom: Float, pad: Float, w: Float) {
        gridColumns = 3
        gridCount = rows.size
        gridLeft = pad
        gridTop = top
        gridCellWidth = (w - pad * 2f) / gridColumns
        gridCellHeight = (bottom - top) / maxOf(1, (rows.size + gridColumns - 1) / gridColumns)

        for (index in rows.indices) {
            val rowIndex = index / gridColumns
            val column = index % gridColumns
            val x = gridLeft + column * gridCellWidth
            val y = gridTop + rowIndex * gridCellHeight
            val row = rows[index]
            val inset = dp(2f)
            val box = RectF(x + inset, y + inset, x + gridCellWidth - inset, y + gridCellHeight - inset)
            val selected = index == console.selection
            if (selected) {
                canvas.drawRect(box, selectionPaint)
            } else {
                canvas.drawRect(box, cellPaint)
            }

            val activePaint = if (selected) inversePaint else if (row.enabled) textPaint else dimPaint
            val activeBadgePaint = if (selected) inversePaint else badgePaint

            textPaint.textSize = dp(12f)
            inversePaint.textSize = textPaint.textSize
            val glyph = gridGlyph(row.label)
            canvas.drawText(glyph, x + (gridCellWidth - textPaint.measureText(glyph)) / 2f, baseline(textPaint, y + gridCellHeight * 0.34f), activePaint)

            textPaint.textSize = dp(8f)
            inversePaint.textSize = textPaint.textSize
            val label = fit(row.label, gridCellWidth - dp(8f))
            canvas.drawText(label, x + (gridCellWidth - textPaint.measureText(label)) / 2f, baseline(textPaint, y + gridCellHeight * 0.66f), activePaint)

            val info = row.badge.ifEmpty { row.value }
            if (info.isNotEmpty()) {
                badgePaint.textSize = dp(7f)
                inversePaint.textSize = badgePaint.textSize
                val text = fit(info, gridCellWidth - dp(8f))
                canvas.drawText(text, x + (gridCellWidth - badgePaint.measureText(text)) / 2f, baseline(badgePaint, y + gridCellHeight * 0.88f), activeBadgePaint)
            }
        }
    }

    private fun drawStatusIcons(canvas: Canvas, w: Float) {
        val y = dp(9f)
        dimPaint.textSize = dp(7f)
        badgePaint.textSize = dp(7f)
        canvas.drawText(clock.format(Date()), w - dp(98f), baseline(dimPaint, y), dimPaint)
        canvas.drawText("SD", w - dp(68f), baseline(badgePaint, y), badgePaint)
        canvas.drawText("BT", w - dp(48f), baseline(badgePaint, y), badgePaint)
        drawBattery(canvas, w - dp(30f), y - dp(6f), dp(20f), dp(9f))
        textPaint.textSize = dp(8f)
        badgePaint.textSize = dp(8f)
    }

    private fun drawBattery(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val stroke = Paint().apply {
            color = Color.parseColor(FlipperTheme.palette(paletteName).text)
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        canvas.drawRect(x, y, x + w, y + h, stroke)
        canvas.drawRect(x + w, y + h * 0.25f, x + w + dp(1.5f), y + h * 0.75f, stroke)
        val fill = Paint().apply { color = Color.parseColor(FlipperTheme.palette(paletteName).text) }
        canvas.drawRect(x + dp(1.5f), y + dp(1.5f), x + w * 0.72f, y + h - dp(1.5f), fill)
    }
}
