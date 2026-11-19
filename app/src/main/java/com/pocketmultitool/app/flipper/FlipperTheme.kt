package com.pocketmultitool.app.flipper

object FlipperTheme {
    const val SHELL = "#242526"
    const val SHELL_DARK = "#151617"
    const val YELLOW = "#FFD400"
    const val YELLOW_DIM = "#A88B00"
    const val LCD_GREEN_BG = "#0B120C"
    const val LCD_GREEN_TEXT = "#A6D08A"
    const val LCD_GREEN_DIM = "#4B6F42"
    const val LCD_GREEN_HEADER = "#16261A"
    const val LCD_GREEN_GRID = "#102015"
    const val LCD_AMBER_BG = "#0B0905"
    const val LCD_AMBER_TEXT = "#FFB347"
    const val LCD_AMBER_DIM = "#7A5218"
    const val LCD_AMBER_HEADER = "#1B1206"
    const val LCD_AMBER_GRID = "#171005"
    const val LCD_WHITE_BG = "#101216"
    const val LCD_WHITE_TEXT = "#E8EDF2"
    const val LCD_WHITE_DIM = "#6E7885"
    const val LCD_WHITE_HEADER = "#1C2027"
    const val LCD_WHITE_GRID = "#171A20"

    data class Palette(
        val bg: String,
        val text: String,
        val dim: String,
        val header: String,
        val grid: String,
        val accent: String = YELLOW,
    )

    fun palette(name: String): Palette = when (name.uppercase()) {
        "AMBER" -> Palette(LCD_AMBER_BG, LCD_AMBER_TEXT, LCD_AMBER_DIM, LCD_AMBER_HEADER, LCD_AMBER_GRID, "#FFB347")
        "WHITE" -> Palette(LCD_WHITE_BG, LCD_WHITE_TEXT, LCD_WHITE_DIM, LCD_WHITE_HEADER, LCD_WHITE_GRID, "#D9E2EC")
        else -> Palette(LCD_GREEN_BG, LCD_GREEN_TEXT, LCD_GREEN_DIM, LCD_GREEN_HEADER, LCD_GREEN_GRID)
    }
}
