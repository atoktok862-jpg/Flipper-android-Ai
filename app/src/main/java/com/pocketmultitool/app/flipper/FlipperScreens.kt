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
        subtitle = "POCKET BUILD // ${Capabilities.report(context).size} СИСТЕМ ПРОВЕРЕНО",
        items = listOf(
            MenuItem("01  SUB-GHZ", badge = "DEMO") { h -> h.open(subGhzScreen()) },
            MenuItem("02  RFID 125 kHz", badge = "${store.rfidCards().size}") { h -> h.open(rfidScreen()) },
            MenuItem("03  NFC 13.56 MHz", badge = "${deps.tagCount()}") { h -> h.open(deps.nfc()) },
            MenuItem("04  INFRARED", badge = "IR") { h -> h.open(deps.ir()) },
            MenuItem("05  iBUTTON / 1-WIRE", badge = "${store.ibuttons().size}") { h -> h.open(ibuttonScreen()) },
            MenuItem("06  BADUSB", badge = "${store.scripts().size}") { h -> h.open(badUsbScreen()) },
            MenuItem("07  BLUETOOTH", badge = "BLE") { h -> h.open(bluetoothScreen()) },
            MenuItem("08  GPIO / EXPANSION", badge = "OTG") { h -> h.open(gpioScreen()) },
            MenuItem("09  FM RADIO", badge = "DEMO") { h -> h.open(fmScreen()) },
            MenuItem("10  ПРИЛОЖЕНИЯ", badge = "APPS") { h -> h.open(appsScreen()) },
            MenuItem("11  АРХИВ", badge = "${store.totalRecords() + deps.tagCount() + deps.cardCount()}") { h -> h.open(archiveScreen()) },
            MenuItem("12  ДОЛФИН", badge = "LVL ${store.level()}") { h -> h.open(dolphinScreen()) },
            MenuItem("13  НАСТРОЙКИ") { h -> h.open(settingsScreen()) },
            MenuItem("14  ДИАГНОСТИКА") { h -> h.open(deps.status()) },
            MenuItem("15  О ПРОГРАММЕ") { h -> h.open(deps.about()) },
        ),
    )

    private fun subGhzScreen(): MenuScreen = MenuScreen(
        title = "SUB-GHZ",
        subtitle = "ПЕРЕДАЧА: НУЖЕН ВНЕШНИЙ ПРИЁМОПЕРЕДАТЧИК",
        items = listOf(
            MenuItem("МОИ СИГНАЛЫ", badge = "${store.signals().size}") { h -> h.open(subGhzListScreen()) },
            MenuItem("НОВЫЙ СИГНАЛ") { h -> newSubGhzSignal(h) },
            MenuItem("ЧАСТОТЫ И ПРОТОКОЛЫ") { h ->
                h.open(TextScreen("SUB-GHZ: ЧАСТОТЫ", { FlipperTools.subGhzPresets() + listOf("ДИАПАЗОНЫ ЗАВИСЯТ ОТ РЕГИОНА") }))
            },
            MenuItem("СПЕКТР-АНАЛИЗАТОР", badge = "DEMO") { h -> h.open(SpectrumScreen()) },
            MenuItem("ОТПРАВИТЬ RAW") { h ->
                h.input("ЧАСТОТА, КГЦ", "433920", "433920") { frequency ->
                    h.input("ДАННЫЕ HEX", "A1B2C3D4", "A1B2C3D4") { data ->
                        h.toast("RAW $frequency КГЦ: $data // НУЖЕН ПЕРЕДАТЧИК")
                        store.addXp(2)
                    }
                }
            },
            MenuItem("ОГРАНИЧЕНИЯ") { h ->
                h.open(TextScreen("SUB-GHZ: ЛИМИТЫ", {
                    listOf(
                        "ТЕЛЕФОН НЕ ИМЕЕТ SUB-GHZ РАДИО",
                        "ДОСТУПНО: БАЗА СИГНАЛОВ И RAW-ДАННЫЕ",
                        "ПЕРЕДАЧА: ЧЕРЕЗ ВНЕШНИЙ МОДУЛЬ",
                        "ПРИЁМ: ЧЕРЕЗ ВНЕШНИЙ ПРИЁМНИК",
                    )
                }))
            },
        ),
    )

    private fun subGhzListScreen(): MenuScreen {
        val signals = store.signals()
        val items = signals.map { signal ->
            MenuItem(signal.name, value = signal.frequency) { h -> h.open(subGhzDetailScreen(signal)) }
        }.toMutableList()
        if (signals.isEmpty()) items.add(MenuItem("ЗАПИСЕЙ НЕТ — ДОБАВЬТЕ СИГНАЛ") { h -> newSubGhzSignal(h) })
        items.add(MenuItem("НАЗАД В SUB-GHZ") { h -> h.back() })
        return MenuScreen("SUB-GHZ: БАЗА", items, subtitle = "${signals.size} СИГНАЛОВ")
    }

    private fun subGhzDetailScreen(signal: FlipperSignal): TextScreen = TextScreen(
        title = "СИГНАЛ: ${signal.name.uppercase()}",
        lines = {
            listOf(
                "ЧАСТОТА: ${signal.frequency} КГЦ",
                "ПРОТОКОЛ: ${signal.protocol}",
                "ДАННЫЕ: ${signal.data}",
                "СОХРАНЕНО: ${signal.time}",
                "ПЕРЕДАЧА: НУЖЕН ВНЕШНИЙ МОДУЛЬ",
            )
        },
        footer = "OK УДАЛИТЬ   ◀ НАЗАД",
        onAction = { h ->
            h.confirm("УДАЛЕНИЕ", "Удалить сигнал ${signal.name}?", onYes = {
                store.removeSignal(signal.name)
                h.back()
                h.toast("СИГНАЛ УДАЛЁН")
            })
        },
    )

    private fun newSubGhzSignal(host: ConsoleHost) {
        host.input("ИМЯ СИГНАЛА", "Гараж", "СИГНАЛ ${store.signals().size + 1}") { name ->
            if (name.isBlank()) {
                host.toast("НУЖНО ИМЯ")
                return@input
            }
            val frequencies = listOf("433920", "315000", "868350", "915000", "ВРУЧНУЮ")
            host.choice("ЧАСТОТА, КГЦ", frequencies) { index ->
                if (frequencies[index] == "ВРУЧНУЮ") {
                    host.input("ЧАСТОТА, КГЦ", "433920", "433920") { custom ->
                        chooseSubGhzProtocol(host, name, custom.ifBlank { "433920" })
                    }
                } else {
                    chooseSubGhzProtocol(host, name, frequencies[index])
                }
            }
        }
    }

    private fun chooseSubGhzProtocol(host: ConsoleHost, name: String, frequency: String) {
        val protocols = listOf("ASK/OOK", "FSK", "PWM", "RAW")
        host.choice("ПРОТОКОЛ", protocols) { index ->
            host.input("ДАННЫЕ HEX", "A1B2C3D4", "A1B2C3D4") { data ->
                if (data.isBlank()) {
                    host.toast("НУЖНЫ ДАННЫЕ")
                    return@input
                }
                store.saveSignal(name.trim(), frequency, protocols[index], data.uppercase())
                host.toast("СИГНАЛ СОХРАНЁН")
            }
        }
    }

    private fun rfidScreen(): MenuScreen = MenuScreen(
        title = "RFID 125 kHz",
        subtitle = "ГЕНЕРАЦИЯ И БАЗА КАРТ EM4100",
        items = listOf(
            MenuItem("ГЕНЕРАТОР EM4100") { h ->
                h.input("UID КАРТЫ", "10 HEX-ЦИФР", "041A2B3C4D") { uid ->
                    h.open(em4100Screen(uid))
                }
            },
            MenuItem("WIEGAND-26") { h ->
                h.input("ОБЪЕКТ", "0-255", "1") { facility ->
                    h.input("НОМЕР КАРТЫ", "0-65535", "12345") { card ->
                        h.open(wiegandScreen(facility, card))
                    }
                }
            },
            MenuItem("МОИ КАРТЫ", badge = "${store.rfidCards().size}") { h -> h.open(rfidListScreen()) },
            MenuItem("ЧТЕНИЕ КАРТЫ") { h ->
                h.open(TextScreen("RFID: ЧТЕНИЕ", {
                    listOf("У ТЕЛЕФОНА НЕТ 125 kHz", "НУЖЕН ВНЕШНИЙ RFID-РИДЕР", "NFC НЕ ЧИТАЕТ ЭТИ КАРТЫ")
                }))
            },
            MenuItem("ЭМУЛЯЦИЯ") { h ->
                h.open(TextScreen("RFID: ЭМУЛЯЦИЯ", {
                    listOf("НУЖЕН 125 kHz ПЕРЕДАТЧИК", "МОЖНО СОХРАНИТЬ И ЭКСПОРТИРОВАТЬ UID", "ЗАПИСЬ T5577 ТОЛЬКО С МОДУЛЕМ")
                }))
            },
        ),
    )

    private fun em4100Screen(uid: String): TextScreen = TextScreen(
        title = "EM4100: ${uid.uppercase()}",
        lines = { FlipperTools.em4100(uid) },
        footer = "OK СОХРАНИТЬ   ◀ НАЗАД",
        onAction = { h ->
            val clean = uid.uppercase().replace(":", "").replace(" ", "")
            if (clean.length != 10) {
                h.toast("НЕВЕРНЫЙ UID")
            } else {
                h.input("ИМЯ КАРТЫ", "Например: Подъезд", "EM4100") { name ->
                    if (name.isNotBlank()) {
                        store.saveRfidCard(name, clean, "EM4100")
                        h.toast("КАРТА СОХРАНЕНА")
                    }
                }
            }
        },
    )

    private fun wiegandScreen(facility: String, card: String): TextScreen = TextScreen(
        title = "WIEGAND-26",
        lines = { FlipperTools.wiegand26(facility.toIntOrNull() ?: -1, card.toIntOrNull() ?: -1) },
        footer = "OK СОХРАНИТЬ   ◀ НАЗАД",
        onAction = { h ->
            val value = facility.toIntOrNull()
            val number = card.toIntOrNull()
            if (value == null || number == null) {
                h.toast("НУЖНЫ ЧИСЛА")
            } else {
                h.input("ИМЯ КАРТЫ", "Например: Офис", "WIEGAND") { name ->
                    if (name.isNotBlank()) {
                        store.saveRfidCard(name, "$value:$number", "WIEGAND-26")
                        h.toast("КАРТА СОХРАНЕНА")
                    }
                }
            }
        },
    )

    private fun rfidListScreen(): MenuScreen {
        val cards = store.rfidCards()
        val items = cards.map { card ->
            MenuItem(card.name, value = card.type) { h -> h.open(rfidDetailScreen(card)) }
        }.toMutableList()
        if (cards.isEmpty()) items.add(MenuItem("СПИСОК ПУСТ") { h -> h.toast("СОЗДАЙТЕ КАРТУ В ГЕНЕРАТОРЕ") })
        return MenuScreen("RFID: КАРТЫ", items, subtitle = "${cards.size} ЗАПИСЕЙ")
    }

    private fun rfidDetailScreen(card: FlipperRfidCard): TextScreen = TextScreen(
        title = "КАРТА: ${card.name.uppercase()}",
        lines = { listOf("UID: ${card.uid}", "ТИП: ${card.type}", "ВРЕМЯ: ${card.time}") },
        footer = "OK УДАЛИТЬ   ◀ НАЗАД",
        onAction = { h ->
            store.removeRfidCard(card.name)
            h.back()
            h.toast("КАРТА УДАЛЕНА")
        },
    )

    private fun ibuttonScreen(): MenuScreen = MenuScreen(
        title = "iBUTTON / 1-WIRE",
        subtitle = "DS1990A И PODA ГЕНЕРАТОРЫ",
        items = listOf(
            MenuItem("ГЕНЕРАТОР DS1990A") { h ->
                h.input("СЕРИЯ, 12 HEX", "00000A1B2C3D", "00000A1B2C3D") { serial ->
                    h.open(ibuttonDetailScreen(serial))
                }
            },
            MenuItem("МОИ КЛЮЧИ", badge = "${store.ibuttons().size}") { h -> h.open(ibuttonListScreen()) },
            MenuItem("ЧТЕНИЕ 1-WIRE") { h ->
                h.open(TextScreen("iBUTTON: ЧТЕНИЕ", {
                    listOf("НУЖЕН 1-WIRE ПРОБНИК", "ПОДДЕРЖКА: DS1990A, DS18B20", "ТЕЛЕФОН НЕ ИМЕЕТ ВСТРОЕННОГО 1-WIRE")
                }))
            },
            MenuItem("ЭМУЛЯЦИЯ") { h ->
                h.open(TextScreen("iBUTTON: ЭМУЛЯЦИЯ", {
                    listOf("НУЖЕН ВНЕШНИЙ 1-WIRE ПЕРЕДАТЧИК", "МОЖНО ХРАНИТЬ ROM-КОДА", "ДЛЯ DS18B20 НУЖЕН ТЕРМОДАТЧИК")
                }))
            },
        ),
    )

    private fun ibuttonDetailScreen(serial: String): TextScreen = TextScreen(
        title = "iBUTTON: ${serial.uppercase().take(12)}",
        lines = { FlipperTools.ds1990a(serial) },
        footer = "OK СОХРАНИТЬ   ◀ НАЗАД",
        onAction = { h ->
            val lines = FlipperTools.ds1990a(serial)
            val rom = lines.firstOrNull { it.startsWith("ROM:") }?.removePrefix("ROM:")?.trim()
            if (rom == null) {
                h.toast("НЕВЕРНАЯ СЕРИЯ")
            } else {
                h.input("ИМЯ КЛЮЧА", "Например: Домофон", "iBUTTON") { name ->
                    if (name.isNotBlank()) {
                        store.saveIButton(name, rom)
                        h.toast("КЛЮЧ СОХРАНЁН")
                    }
                }
            }
        },
    )

    private fun ibuttonListScreen(): MenuScreen {
        val keys = store.ibuttons()
        val items = keys.map { key ->
            MenuItem(key.name, value = key.rom.take(10)) { h -> h.open(ibuttonKeyDetailScreen(key)) }
        }.toMutableList()
        if (keys.isEmpty()) items.add(MenuItem("СПИСОК ПУСТ") { h -> h.toast("СГЕНЕРИРУЙТЕ ROM-КОД") })
        return MenuScreen("iBUTTON: КЛЮЧИ", items, subtitle = "${keys.size} ЗАПИСЕЙ")
    }

    private fun ibuttonKeyDetailScreen(key: FlipperIButton): TextScreen = TextScreen(
        title = "КЛЮЧ: ${key.name.uppercase()}",
        lines = { listOf("ROM: ${key.rom}", "ВРЕМЯ: ${key.time}") },
        footer = "OK УДАЛИТЬ   ◀ НАЗАД",
        onAction = { h ->
            store.removeIButton(key.name)
            h.back()
            h.toast("КЛЮЧ УДАЛЁН")
        },
    )

    private fun badUsbScreen(): MenuScreen = MenuScreen(
        title = "BADUSB",
        subtitle = "DUCKY SCRIPT / НУЖЕН USB HID",
        items = listOf(
            MenuItem("НОВЫЙ СКРИПТ") { h ->
                    h.input("ИМЯ СКРИПТА", "Autostart", "SCRIPT ${store.scripts().size + 1}") { name ->
                    if (name.isBlank()) {
                        h.toast("НУЖНО ИМЯ")
                        return@input
                    }
                    h.input("ТЕКСТ СКРИПТА", "STRING hello\nDELAY 500\nENTER", "STRING hello\nENTER") { body ->
                        store.saveScript(name.trim(), body)
                        h.toast("СКРИПТ СОХРАНЁН")
                    }
                }
            },
            MenuItem("МОИ СКРИПТЫ", badge = "${store.scripts().size}") { h -> h.open(scriptListScreen()) },
            MenuItem("ПРОВЕРКА ПРИМЕРА") { h ->
                h.open(TextScreen("BADUSB: ПРОВЕРКА", { FlipperTools.validateDucky(DEMO_SCRIPT) }))
            },
            MenuItem("ЗАПУСК") { h ->
                h.open(TextScreen("BADUSB: ЗАПУСК", {
                    listOf("ЗАПУСК ВОЗМОЖЕН С USB HID", "ANDROID НЕ ПОДДЕРЖИВАЕТ HID ИЗ КОРОБКИ", "НУЖЕН ROOT ИЛИ ВНЕШНИЙ КОНТРОЛЛЕР")
                }))
            },
            MenuItem("КОМАНДЫ") { h ->
                h.open(TextScreen("DUCKY: КОМАНДЫ", {
                    listOf("STRING текст", "DELAY мс", "ENTER / TAB / ESC", "CTRL ALT DEL", "GUI r", "REPEAT число")
                }))
            },
        ),
    )

    private fun scriptListScreen(): MenuScreen {
        val scripts = store.scripts()
        val items = scripts.map { script ->
            MenuItem(script.name, value = script.time) { h -> h.open(scriptDetailScreen(script)) }
        }.toMutableList()
        if (scripts.isEmpty()) items.add(MenuItem("СПИСОК ПУСТ") { h -> h.toast("СОЗДАЙТЕ СКРИПТ") })
        return MenuScreen("BADUSB: СКРИПТЫ", items, subtitle = "${scripts.size} ФАЙЛОВ")
    }

    private fun scriptDetailScreen(script: FlipperScript): MenuScreen = MenuScreen(
        title = "СКРИПТ: ${script.name.uppercase()}",
        subtitle = script.time,
        items = listOf(
            MenuItem("ПОКАЗАТЬ ТЕКСТ") { h ->
                h.open(TextScreen("BADUSB: ${script.name.uppercase()}", { script.body.lines().ifEmpty { listOf("ПУСТО") } }))
            },
            MenuItem("ПРОВЕРИТЬ") { h ->
                h.open(TextScreen("BADUSB: ПРОВЕРКА", { FlipperTools.validateDucky(script.body) }))
            },
            MenuItem("ЗАПУСТИТЬ") { h ->
                h.toast("НУЖЕН USB HID КОНТРОЛЛЕР")
            },
            MenuItem("УДАЛИТЬ") { h ->
                store.removeScript(script.name)
                h.back()
                h.toast("СКРИПТ УДАЛЁН")
            },
        ),
    )

    private fun bluetoothScreen(): MenuScreen = MenuScreen(
        title = "BLUETOOTH",
        subtitle = "BLE / HID / SERIAL",
        items = listOf(
            MenuItem("BLE-СКАНЕР") { h -> h.open(deps.scanner()) },
            MenuItem("WI-FI-СКАНЕР") { h -> h.open(deps.scanner()) },
            MenuItem("HID КЛАВИАТУРА") { h ->
                h.open(TextScreen("BLE HID", {
                    listOf("ANDROID НЕ УМЕЕТ ЛОКОВОЙ HID", "НУЖЕН ВНЕШНИЙ BLE-МОДУЛЬ", "ПОДДЕРЖКА: КЛАВИАТУРА, МЫШЬ, МЕДИА")
                }))
            },
            MenuItem("SERIAL UART") { h ->
                h.open(TextScreen("BLE UART", {
                    listOf("ПОДКЛЮЧИТЕ BLE-UART МОДУЛЬ", "НАСТРОЙТЕ 115200 8N1", "ТЕРМИНАЛ ДОСТУПЕН ЧЕРЕЗ USB-OTG")
                }))
            },
            MenuItem("СТАТУС") { h ->
                h.open(TextScreen("BLUETOOTH: СТАТУС", { Capabilities.report(context).filter { it.contains("BLUETOOTH") || it.contains("WI-FI") } }))
            },
        ),
    )

    private fun gpioScreen(): MenuScreen = MenuScreen(
        title = "GPIO / EXPANSION",
        subtitle = "UART / I2C / SPI ЧЕРЕЗ ВНЕШНИЙ АДАПТЕР",
        items = listOf(
            MenuItem("РАСПИНОВКА") { h -> h.open(TextScreen("GPIO: ПИНЫ", { FlipperTools.gpioReference() })) },
            MenuItem("UART-ТЕРМИНАЛ") { h -> h.open(deps.usb()) },
            MenuItem("I2C-СКАНЕР") { h ->
                h.open(TextScreen("I2C: СКАНЕР", {
                    listOf("НУЖЕН USB-I2C АДАПТЕР", "АДРЕСА: 0x08-0x77", "СКАНИРОВАНИЕ ЧЕРЕЗ ВНЕШНИЙ МОДУЛЬ")
                }))
            },
            MenuItem("SPI") { h ->
                h.open(TextScreen("SPI: РЕЖИМ", {
                    listOf("НУЖЕН USB-SPI АДАПТЕР", "РЕЖИМЫ: 0,1,2,3", "СКОРОСТЬ: ДО 10 МГц")
                }))
            },
            MenuItem("ЛОГИЧЕСКИЙ АНАЛИЗ") { h ->
                h.open(TextScreen("GPIO: АНАЛИЗ", {
                    listOf("НУЖЕН ЛОГИЧЕСКИЙ АНАЛИЗАТОР", "ПОДДЕРЖКА: 8 КАНАЛОВ", "ЧАСТОТА ДИСКРЕТИЗАЦИИ ДО 100 МГц")
                }))
            },
        ),
    )

    private fun fmScreen(): MenuScreen = MenuScreen(
        title = "FM RADIO",
        subtitle = "НУЖЕН FM-ПРИЁМНИК ИЛИ RTL-SDR",
        items = listOf(
            MenuItem("ПРЕСЕТЫ") { h ->
                h.open(TextScreen("FM: ПРЕСЕТЫ", {
                    listOf("87.5 МГц  диапазон START", "88.0 МГц  RADIO ULTRA", "91.5 МГц  LOCAL FM", "101.0 МГц INFO", "107.9 МГц диапазон END")
                }))
            },
            MenuItem("АВТОПОИСК") { h ->
                h.open(TextScreen("FM: ПОИСК", {
                    listOf("ВСТРОЕННОГО FM-ПРИЁМНИКА НЕТ", "ПОДКЛЮЧИТЕ USB SDR", "ДИАПАЗОН 87.5-108 МГц")
                }))
            },
            MenuItem("RDS") { h ->
                h.open(TextScreen("FM: RDS", {
                    listOf("RDS ДЕКОДИРУЕТСЯ SDR-МОДУЛЕМ", "ПОЛЯ: PS, RT, PTY, AF", "АНАЛОГОВЫЙ ВХОД ОТСУТСТВУЕТ")
                }))
            },
            MenuItem("ЧАСТОТЫ") { h ->
                h.open(TextScreen("FM: ДИАПАЗОН", {
                    listOf("87.5-108.0 МГц  ОСНОВНОЙ", "76-90 МГц      ЯПОНИЯ", "ШИРИНА КАНАЛА 100 кГц")
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
        subtitle = "${store.totalRecords()} ЛОКАЛЬНЫХ ЗАПИСЕЙ",
        items = listOf(
            MenuItem("SUB-GHZ СИГНАЛЫ", badge = "${store.signals().size}") { h -> h.open(subGhzListScreen()) },
            MenuItem("RFID КАРТЫ", badge = "${store.rfidCards().size}") { h -> h.open(rfidListScreen()) },
            MenuItem("iBUTTON КЛЮЧИ", badge = "${store.ibuttons().size}") { h -> h.open(ibuttonListScreen()) },
            MenuItem("BADUSB СКРИПТЫ", badge = "${store.scripts().size}") { h -> h.open(scriptListScreen()) },
            MenuItem("NFC МЕТКИ", badge = "${deps.tagCount()}") { h -> h.open(deps.nfc()) },
            MenuItem("ШТРИХКОДЫ", badge = "${deps.cardCount()}") { h -> h.open(deps.cards()) },
            MenuItem("ОЧИСТИТЬ ЛОКАЛЬНЫЙ АРХИВ") { h ->
                h.confirm("ОЧИСТКА", "Удалить все локальные записи Flipper?", onYes = {
                    store.clearAll()
                    h.toast("АРХИВ ОЧИЩЕН")
                })
            },
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
                        "ЗАПИСЕЙ: ${store.totalRecords()}",
                    )
                }))
            },
            MenuItem("СТАТИСТИКА") { h ->
                h.open(TextScreen("ДОЛФИН: СТАТ", {
                    listOf(
                        "XP: ${store.xp()}",
                        "УРОВЕНЬ: ${store.level()}",
                        "ДО УРОВНЯ: ${store.xpToNext()}",
                        "СИГНАЛОВ: ${store.signals().size}",
                        "RFID: ${store.rfidCards().size}",
                        "КЛЮЧЕЙ: ${store.ibuttons().size}",
                        "СКРИПТОВ: ${store.scripts().size}",
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
        footer = "OK ОЧИСТИТЬ   ◀ НАЗАД",
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
            MenuItem("PIN-КОД", value = if (store.setting("pin", "").isEmpty()) "НЕТ" else "УСТАНОВЛЕН") { h ->
                h.input("PIN-КОД", "4 цифры", store.setting("pin", "")) { pin ->
                    if (pin.length != 4 || pin.any { !it.isDigit() }) {
                        h.toast("НУЖНО 4 ЦИФРЫ")
                    } else {
                        store.setSetting("pin", pin)
                        h.toast("PIN СОХРАНЁН")
                    }
                }
            },
            MenuItem("ЯЗЫК", value = "RU") { h -> h.toast("РУССКИЙ") },
            MenuItem("СБРОС ДОЛФИНА") { h ->
                h.confirm("СБРОС", "Сбросить XP и уровень?", onYes = {
                    store.resetProgress()
                    h.toast("ПРОГРЕСС СБРОШЕН")
                })
            },
            MenuItem("ДИАГНОСТИКА") { h -> h.open(deps.status()) },
        ),
    )

    companion object {
        private const val DEMO_SCRIPT = "REM demo\nDELAY 300\nSTRING hello flipper\nENTER"
    }
}
