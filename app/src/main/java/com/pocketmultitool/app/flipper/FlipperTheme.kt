package com.pocketmultitool.app.flipper

object FlipperTheme {
    const val SHELL = "#2E3133"
    const val SHELL_DARK = "#17191A"
    const val YELLOW = "#E8EAE4"
    const val YELLOW_DIM = "#8B8F89"
    const val LCD_GREEN_BG = "#C8D3BC"
    const val LCD_GREEN_TEXT = "#1B241A"
    const val LCD_GREEN_DIM = "#66735E"
    const val LCD_GREEN_HEADER = "#B7C2AB"
    const val LCD_GREEN_GRID = "#BFC9B5"
    const val LCD_AMBER_BG = "#D8C89C"
    const val LCD_AMBER_TEXT = "#2B2212"
    const val LCD_AMBER_DIM = "#7C6B43"
    const val LCD_AMBER_HEADER = "#C7B589"
    const val LCD_AMBER_GRID = "#CCBB90"
    const val LCD_WHITE_BG = "#E1E5E7"
    const val LCD_WHITE_TEXT = "#202529"
    const val LCD_WHITE_DIM = "#687076"
    const val LCD_WHITE_HEADER = "#D0D6D9"
    const val LCD_WHITE_GRID = "#D6DBDE"

    data class Palette(
        val bg: String,
        val text: String,
        val dim: String,
        val header: String,
        val grid: String,
        val accent: String = YELLOW,
    )

    fun palette(name: String): Palette = when (name.uppercase()) {
        "AMBER" -> Palette(LCD_AMBER_BG, LCD_AMBER_TEXT, LCD_AMBER_DIM, LCD_AMBER_HEADER, LCD_AMBER_GRID, LCD_AMBER_TEXT)
        "WHITE" -> Palette(LCD_WHITE_BG, LCD_WHITE_TEXT, LCD_WHITE_DIM, LCD_WHITE_HEADER, LCD_WHITE_GRID, LCD_WHITE_TEXT)
        else -> Palette(LCD_GREEN_BG, LCD_GREEN_TEXT, LCD_GREEN_DIM, LCD_GREEN_HEADER, LCD_GREEN_GRID, LCD_GREEN_TEXT)
    }
}
