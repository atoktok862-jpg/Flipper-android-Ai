package com.pocketmultitool.app.flipper

import android.content.Context
import android.os.Build
import com.pocketmultitool.app.BuildConfig
import com.pocketmultitool.app.core.ConsoleHost
import com.pocketmultitool.app.core.MenuItem
import com.pocketmultitool.app.core.MenuScreen
import com.pocketmultitool.app.core.TextScreen
import com.pocketmultitool.app.hardware.Capabilities
import com.pocketmultitool.app.hardware.tools.MorseAlphabet
import kotlin.random.Random

data class FlipperDeps(
    val ir: () -> MenuScreen,
    val nfc: () -> MenuScreen,
    val cards: () -> MenuScreen,
    val scanner: () -> MenuScreen,
    val usb: () -> MenuScreen,
    val tools: () -> MenuScreen,
    val status: () -> TextScreen,
    val about: () -> TextScreen,
    val tagCount: () -> Int,
    val cardCount: () -> Int,
)

class FlipperScreens(
    private val context: Context,
    private val store: FlipperStore,
    private val deps: FlipperDeps,
) {
    fun mainScreen(): MenuScreen = MenuScreen(
        title = "FLIPPER ZERO",
        subtitle = "POCKET // ${Capabilities.report(context).size} СИСТЕМ ПРОВЕРЕНО",
        items = listOf(
            MenuItem("01  NFC 13.56 MHz", badge = "${deps.tagCount()}") { h -> h.open(deps.nfc()) },
            MenuItem("02  INFRARED", badge = "IR") { h -> h.open(deps.ir()) },
            MenuItem("03  СКАНЕРЫ", badge = "BLE/WIFI") { h -> h.open(radioScreen()) },
            MenuItem("04  ПРИЛОЖЕНИЯ", badge = "APPS") { h -> h.open(appsScreen()) },
            MenuItem("05  АРХИВ", badge = "${deps.tagCount() + deps.cardCount()}") { h -> h.open(archiveScreen()) },
            MenuItem("06  ДОЛФИН", badge = "LVL ${store.level()}") { h -> h.open(dolphinScreen()) },
            MenuItem("07  НАСТРОЙКИ") { h -> h.open(settingsScreen()) },
            MenuItem("08  ДИАГНОСТИКА") { h -> h.open(deps.status()) },
            MenuItem("09  О ПРОГРАММЕ") { h -> h.open(deps.about()) },
        ),
    )

    private fun radioScreen(): MenuScreen = MenuScreen(
        title = "СКАНЕРЫ",
        subtitle = "РЕАЛЬНОЕ ЖЕЛЕЗО ТЕЛЕФОНА",
        items = listOf(
            MenuItem("BLE-СКАНЕР") { h -> h.open(deps.scanner()) },
            MenuItem("WI-FI-СКАНЕР") { h -> h.open(deps.scanner()) },
            MenuItem("СТАТУС") { h ->
                h.open(TextScreen("СКАНЕРЫ: СТАТУС", {
                    Capabilities.report(context).filter { it.contains("BLUETOOTH") || it.contains("WI-FI") }
                }))
            },
        ),
    )

    private fun appsScreen(): MenuScreen = MenuScreen(
        title = "ПРИЛОЖЕНИЯ",
        subtitle = "ИГРЫ / УТИЛИТЫ / МОДУЛИ",
        items = listOf(
            MenuItem("DOLPHIN JUMP") { h -> h.open(DolphinJumpScreen(store)) },
            MenuItem("УГАДАЙ ЧИСЛО") { h -> guessNumber(h) },
            MenuItem("КАЛЬКУЛЯТОР") { h ->
                h.input("ВЫРАЖЕНИЕ", "(12+8)*3/4", "(12+8)*3/4") { expression ->
                    h.open(TextScreen("КАЛЬКУЛЯТОР", { FlipperTools.calculate(expression) }))
                }
            },
            MenuItem("ПЕРЕВОД ЧИСЕЛ") { h ->
                h.input("ЧИСЛО", "255 или 0xFF", "255") { value ->
                    h.open(TextScreen("ЧИСЛА", { FlipperTools.convertNumber(value) }))
                }
            },
            MenuItem("АЗБУКА МОРЗЕ") { h ->
                h.input("СООБЩЕНИЕ", "SOS", "SOS") { text ->
                    h.open(TextScreen("МОРЗЕ", {
                        listOf("ТЕКСТ: ${text.uppercase()}", "КОД: ${MorseAlphabet.encode(text)}")
                    }))
                }
            },
            MenuItem("ШТРИХКОДЫ", badge = "${deps.cardCount()}") { h -> h.open(deps.cards()) },
            MenuItem("РАДИО-СКАНЕР") { h -> h.open(deps.scanner()) },
            MenuItem("USB-ТЕРМИНАЛ") { h -> h.open(deps.usb()) },
            MenuItem("ИНСТРУМЕНТЫ") { h -> h.open(deps.tools()) },
            MenuItem("НАСТРОЙКИ") { h -> h.open(settingsScreen()) },
        ),
    )

    private fun guessNumber(host: ConsoleHost) = askGuess(host, Random.nextInt(1, 101), 0)

    private fun askGuess(host: ConsoleHost, secret: Int, attempts: Int) {
        host.input("УГАДАЙ ЧИСЛО", "1-100", "50") { text ->
            val value = text.toIntOrNull()
            if (value == null) {
                host.toast("НУЖНО ЧИСЛО")
                askGuess(host, secret, attempts)
            } else if (value == secret) {
                store.addXp(15)
                host.toast("ВЕРНО! ПОПЫТОК: ${attempts + 1}")
            } else if (attempts >= 8) {
                host.toast("ПОПЫТКИ КОНЧИЛИСЬ. ЧИСЛО: $secret")
            } else {
                host.toast(if (value < secret) "БОЛЬШЕ" else "МЕНЬШЕ")
                askGuess(host, secret, attempts + 1)
            }
        }
    }

    private fun archiveScreen(): MenuScreen = MenuScreen(
        title = "АРХИВ",
        subtitle = "ЛОКАЛЬНЫЕ ЗАПИСИ ТЕЛЕФОНА",
        items = listOf(
            MenuItem("NFC МЕТКИ", badge = "${deps.tagCount()}") { h -> h.open(deps.nfc()) },
            MenuItem("ШТРИХКОДЫ", badge = "${deps.cardCount()}") { h -> h.open(deps.cards()) },
        ),
    )

    private fun dolphinScreen(): MenuScreen = MenuScreen(
        title = "ДОЛФИН",
        subtitle = "УРОВЕНЬ ${store.level()} // XP ${store.xp()}",
        items = listOf(
            MenuItem("ПАСПОРТ") { h ->
                h.open(TextScreen("ПАСПОРТ", {
                    listOf(
                        "МОДЕЛЬ: FLIPPER ZERO POCKET",
                        "ПЛАТФОРМА: ${Build.MANUFACTURER} ${Build.MODEL}",
                        "ANDROID: ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}",
                        "СБОРКА: ${BuildConfig.VERSION_NAME}",
                        "УРОВЕНЬ: ${store.level()}",
                    )
                }))
            },
            MenuItem("СТАТИСТИКА") { h ->
                h.open(TextScreen("ДОЛФИН: СТАТ", {
                    listOf(
                        "XP: ${store.xp()}",
                        "УРОВЕНЬ: ${store.level()}",
                        "ДО УРОВНЯ: ${store.xpToNext()}",
                        "NFC МЕТОК: ${deps.tagCount()}",
                        "ШТРИХКОДОВ: ${deps.cardCount()}",
                    )
                }))
            },
            MenuItem("УВЕДОМЛЕНИЯ", badge = "${store.notifications().size}") { h -> h.open(notificationsScreen()) },
            MenuItem("ПОТРЕНИРОВАТЬСЯ") { h -> h.open(DolphinJumpScreen(store)) },
            MenuItem("НАСТРОЙКИ") { h -> h.open(settingsScreen()) },
        ),
    )

    private fun notificationsScreen(): TextScreen = TextScreen(
        title = "УВЕДОМЛЕНИЯ",
        lines = { store.notifications().ifEmpty { listOf("НЕТ НОВЫХ УВЕДОМЛЕНИЙ") } },
        footer = "OK ОЧИСТИТЬ   ◀ BACK",
        onAction = { h ->
            store.clearNotifications()
            h.toast("УВЕДОМЛЕНИЯ ОЧИЩЕНЫ")
        },
    )

    private fun settingsScreen(): MenuScreen = MenuScreen(
        title = "НАСТРОЙКИ",
        subtitle = "ТЕМА ${store.setting("lcd_theme", "GREEN")} // ВИБРАЦИЯ ${if (store.flag("vibration", true)) "ВКЛ" else "ВЫКЛ"}",
        items = listOf(
            MenuItem("ТЕМА ЭКРАНА", value = store.setting("lcd_theme", "GREEN")) { h ->
                h.choice("ТЕМА LCD", listOf("GREEN", "AMBER", "WHITE")) { index ->
                    val themes = listOf("GREEN", "AMBER", "WHITE")
                    store.setSetting("lcd_theme", themes[index])
                    h.toast("ТЕМА: ${themes[index]}")
                }
            },
            MenuItem("ВИБРАЦИЯ", value = if (store.flag("vibration", true)) "ВКЛ" else "ВЫКЛ") { h ->
                val next = !store.flag("vibration", true)
                store.setFlag("vibration", next)
                h.toast("ВИБРАЦИЯ: ${if (next) "ВКЛ" else "ВЫКЛ"}")
            },
            MenuItem("СБРОС ДОЛФИНА") { h ->
                h.confirm("СБРОС", "Сбросить XP и уровень?", onYes = {
                    store.resetProgress()
                    h.toast("ПРОГРЕСС СБРОШЕН")
                })
            },
            MenuItem("ДИАГНОСТИКА") { h -> h.open(deps.status()) },
        ),
    )
}
