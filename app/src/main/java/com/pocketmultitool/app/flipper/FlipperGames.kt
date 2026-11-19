package com.pocketmultitool.app.flipper

import com.pocketmultitool.app.core.ConsoleHost
import com.pocketmultitool.app.core.ConsoleRow
import com.pocketmultitool.app.core.ConsoleScreen
import kotlin.random.Random

class DolphinJumpScreen(private val store: FlipperStore) : ConsoleScreen {
    override val title = "DOLPHIN JUMP"
    override val subtitle: String get() = "СЧЁТ $score  ЛУЧШИЙ $best"
    override val footer = "OK ПРЫЖОК/СТАРТ   ◀ НАЗАД"

    private val width = 18
    private val height = 6
    private var dolphinY = 3
    private var velocity = 0
    private var obstacles = mutableListOf<Pair<Int, Int>>()
    private var score = 0
    private var running = false
    private var ticks = 0
    private var best = store.setting("best_jump", "0").toIntOrNull() ?: 0

    override fun rows(host: ConsoleHost): List<ConsoleRow> {
        val grid = Array(height) { CharArray(width) { ' ' } }
        obstacles.forEach { (x, gap) ->
            for (y in 0 until height) {
                if (y !in gap until gap + 2) grid[y][x] = '#'
            }
        }
        if (dolphinY in 0 until height) grid[dolphinY][3] = 'D'
        return (listOf("ИГРА: ${if (running) "ИДЁТ" else "ПАУЗА"}") + grid.map { it.joinToString("") })
            .map { ConsoleRow(it) }
    }

    override fun onEnter(host: ConsoleHost) {
        reset()
    }

    override fun onTick(host: ConsoleHost) {
        if (!running) return
        ticks++
        velocity += 1
        dolphinY += velocity / 3
        if (dolphinY < 0) {
            dolphinY = 0
            velocity = 0
        }
        if (dolphinY >= height) {
            gameOver(host)
            return
        }
        if (ticks % 3 == 0) {
            obstacles.add(width - 1 to Random.nextInt(0, height - 2))
        }
        obstacles = obstacles
            .map { (x, gap) -> (x - 1) to gap }
            .filter { it.first >= 0 }
            .toMutableList()
        val hit = obstacles.any { (x, gap) -> x == 3 && dolphinY !in gap until gap + 2 }
        if (hit) {
            gameOver(host)
        } else if (obstacles.none { it.first == 3 }) {
            score++
        }
    }

    override fun onConfirm(index: Int, host: ConsoleHost) {
        if (!running) {
            running = true
            ticks = 0
        }
        velocity = -4
        host.vibrate(12)
    }

    private fun reset() {
        dolphinY = 3
        velocity = 0
        obstacles = mutableListOf()
        score = 0
        ticks = 0
        running = false
    }

    private fun gameOver(host: ConsoleHost) {
        running = false
        if (score > best) {
            best = score
            store.setSetting("best_jump", score.toString())
            store.addXp(10)
        }
        host.toast("ФИНИШ: $score ОЧКОВ")
    }
}

class SpectrumScreen : ConsoleScreen {
    override val title = "SUB-GHZ: АНАЛИЗ"
    override val subtitle = "ДЕМО-СПЕКТР 300-1000 МГц"
    override val footer = "OK ОБНОВИТЬ   ◀ НАЗАД"

    private var bins = MutableList(12) { Random.nextInt(1, 8) }

    override fun rows(host: ConsoleHost): List<ConsoleRow> {
        val rows = ArrayList<ConsoleRow>()
        rows.add(ConsoleRow("ЧАСТОТА", "УРОВЕНЬ"))
        bins.forEachIndexed { index, level ->
            val frequency = 300 + index * 64
            val bar = "#".repeat(level).padEnd(8, '.')
            rows.add(ConsoleRow("$frequency МГц", bar))
        }
        rows.add(ConsoleRow("РЕЖИМ", "ДЕМОНСТРАЦИЯ"))
        return rows
    }

    override fun onTick(host: ConsoleHost) {
        val index = Random.nextInt(bins.size)
        bins[index] = Random.nextInt(1, 9)
    }

    override fun onConfirm(index: Int, host: ConsoleHost) {
        bins = MutableList(12) { Random.nextInt(1, 9) }
        host.vibrate(15)
    }
}
