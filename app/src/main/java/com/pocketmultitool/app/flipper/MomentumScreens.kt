package com.pocketmultitool.app.flipper

import com.pocketmultitool.app.core.ConsoleHost
import com.pocketmultitool.app.core.MenuItem
import com.pocketmultitool.app.core.MenuScreen
import com.pocketmultitool.app.core.TextScreen

class MomentumScreens(
    private val store: FlipperStore,
    private val openNfc: () -> MenuScreen,
    private val openUsb: () -> MenuScreen,
    private val openScanner: () -> MenuScreen,
) {
    fun momentumScreen(): MenuScreen = MenuScreen(
        title = "MOMENTUM / CFW",
        subtitle = "mntm-012 // ${catalog.size} ПРИЛОЖЕНИЙ В КАТАЛОГЕ",
        items = listOf(
            MenuItem("01  MOMENTUM APP") { h -> h.open(momentumAppScreen()) },
            MenuItem("02  ASSET PACKS", value = store.setting("m_asset_pack", "DEFAULT").take(10)) { h -> h.open(assetPacksScreen()) },
            MenuItem("03  ИНТЕРФЕЙС И МЕНЮ", value = store.setting("m_menu_style", "GRID").take(8)) { h -> h.open(interfaceScreen()) },
            MenuItem("04  CONTROL CENTER") { h -> h.open(controlCenterScreen()) },
            MenuItem("05  BLE SPAM") { h -> h.open(bleSpamScreen()) },
            MenuItem("06  FIND MY FLIPPER", value = if (store.flag("m_findmy", false)) "ВКЛ" else "ВЫКЛ") { h -> h.open(findMyScreen()) },
            MenuItem("07  BAD KB") { h -> h.open(badKbScreen()) },
            MenuItem("08  NFC EXTENDED") { h -> h.open(nfcExtendedScreen()) },
            MenuItem("09  SUB-GHZ EXTENDED") { h -> h.open(subGhzExtendedScreen()) },
            MenuItem("10  WI-FI / WARDRIVER") { h -> h.open(wifiScreen()) },
            MenuItem("11  ФАЙЛОВЫЙ МЕНЕДЖЕР") { h -> h.open(fileManagerScreen()) },
            MenuItem("12  БЕЗОПАСНОСТЬ И SPOOF") { h -> h.open(securityScreen()) },
            MenuItem("13  GPIO И МОДУЛИ") { h -> h.open(gpioAppsScreen()) },
            MenuItem("14  DISK / JS SCRIPTS") { h -> h.open(diskJsScreen()) },
            MenuItem("15  КАТАЛОГ ПРИЛОЖЕНИЙ", badge = "${catalog.size}") { h -> h.open(catalogScreen()) },
            MenuItem("16  О MOMENTUM") { h -> h.open(aboutMomentumScreen()) },
        ),
    )

    private fun momentumAppScreen(): MenuScreen = MenuScreen(
        title = "MOMENTUM APP",
        subtitle = "ЦЕНТР УПРАВЛЕНИЯ ПРОШИВКОЙ",
        items = listOf(
            MenuItem("ВЕРСИЯ", value = "mntm-012") { h -> h.open(aboutMomentumScreen()) },
            toggleItem("FIND MY FLIPPER", "findmy"),
            toggleItem("BLE SPAM", "ble_spam"),
            toggleItem("BAD KB EXTRA", "badkb_extra"),
            toggleItem("NFC TYPE 4", "nfc_type4"),
            toggleItem("NFC EMV", "nfc_emv"),
            toggleItem("SUBDRIVING", "subdriving"),
            toggleItem("ROLLING CODE", "rolling_code"),
            toggleItem("FILE SEARCH", "file_search", true),
            toggleItem("DISK IMAGE", "disk_image"),
            toggleItem("JS API", "js_api", true),
            toggleItem("RGB BACKLIGHT", "rgb_backlight", true),
        ),
    )

    private fun assetPacksScreen(): MenuScreen = MenuScreen(
        title = "ASSET PACKS",
        subtitle = "ТЕМА: ${store.setting("m_asset_pack", "DEFAULT")}",
        items = listOf(
            choiceItem(
                "АКТИВНЫЙ ПАК",
                "asset_pack",
                listOf("DEFAULT", "NEON_ZERO", "RETRO_GREEN", "DOLPHIN_CITY", "MILITARY", "CANDY"),
                "ASSET PACK",
            ),
            MenuItem("СКОРОСТЬ АНИМАЦИЙ", value = "${store.setting("m_anim_speed", "100")}%") { h ->
                h.input("СКОРОСТЬ, %", "25-300", store.setting("m_anim_speed", "100")) { value ->
                    val speed = value.toIntOrNull()?.coerceIn(25, 300)
                    if (speed == null) {
                        h.toast("НУЖНО ЧИСЛО 25-300")
                    } else {
                        store.setSetting("m_anim_speed", speed.toString())
                        h.toast("СКОРОСТЬ: $speed%")
                    }
                }
            },
            toggleItem("ЦИКЛИЧЕСКИЕ АНИМАЦИИ", "cycle_anims", true),
            toggleItem("ANIM UNLOCK", "unlock_anims"),
            toggleItem("ЗВУКИ ПАКА", "pack_sounds", true),
            MenuItem("ИМПОРТ ПАКА") { h ->
                h.open(TextScreen("ASSET: ИМПОРТ", {
                    listOf("ПОМЕСТИТЕ ПАПКУ ПАКА В /ASSETS", "ПОДДЕРЖКА: ИКОНКИ, ШРИФТЫ, АНИМАЦИИ", "ПЕРЕЗАПУСТИТЕ ПРОШИВКУ ДЛЯ ПРИМЕНЕНИЯ")
                }))
            },
        ),
    )

    private fun interfaceScreen(): MenuScreen = MenuScreen(
        title = "MOMENTUM: ИНТЕРФЕЙС",
        subtitle = "СТИЛЬ: ${store.setting("m_menu_style", "GRID")}",
        items = listOf(
            choiceItem(
                "СТИЛЬ МЕНЮ",
                "menu_style",
                listOf("GRID", "LIST", "ROWS", "CARDS", "DOTS", "SKYSCRAPER", "CIRCLE", "COMBO", "MOMENTUM"),
                "СТИЛЬ ГЛАВНОГО МЕНЮ",
            ),
            toggleItem("ЧАСЫ В СТАТУСБАРЕ", "show_clock", true),
            toggleItem("ИКОНКИ В СТАТУСБАРЕ", "show_icons", true),
            toggleItem("ГРАНИЦЫ СТАТУСБАРА", "bar_borders"),
            toggleItem("ПОПУП OVERLAY", "popup_overlay", true),
            toggleItem("ПОКАЗЫВАТЬ ПУТЬ", "show_path", true),
            toggleItem("ПАПКИ СВЕРХУ", "folders_top", true),
            toggleItem("СКРЫТЫЕ ФАЙЛЫ", "hidden_files"),
            choiceItem("ПРОКРУТКА ТЕКСТА", "text_scroll", listOf("NORMAL", "SLOW", "FAST", "OFF"), "TEXT SCROLL"),
            MenuItem("ЭКРАН БЛОКИРОВКИ") { h -> h.open(lockScreenScreen()) },
            MenuItem("СБРОС МЕНЮ") { h ->
                h.confirm("СБРОС", "Вернуть стандартное меню?", onYes = {
                    store.setSetting("m_menu_style", "GRID")
                    h.toast("МЕНЮ СБРОШЕНО")
                })
            },
        ),
    )

    private fun lockScreenScreen(): MenuScreen = MenuScreen(
        title = "MOMENTUM: LOCKSCREEN",
        subtitle = "НАСТРОЙКИ ЭКРАНА БЛОКИРОВКИ",
        items = listOf(
            toggleItem("БЛОКИРОВКА ПРИ ЗАГРУЗКЕ", "lock_boot"),
            toggleItem("ФОРМАТ ПРИ НЕВЕРНОМ PIN", "format_bad_pin"),
            toggleItem("USB RPC ПРИ БЛОКИРОВКЕ", "usb_rpc_locked"),
            toggleItem("BLE RPC ПРИ БЛОКИРОВКЕ", "ble_rpc_locked"),
            toggleItem("ВЫКЛЮЧЕНИЕ ПРИ БЛОКИРОВКЕ", "allow_poweroff"),
            toggleItem("ВРЕМЯ", "lock_time", true),
            toggleItem("СЕКУНДЫ", "lock_seconds"),
            toggleItem("ДАТА", "lock_date", true),
            toggleItem("СТАТУСБАР", "lock_statusbar", true),
            toggleItem("ПРОЗРАЧНОСТЬ", "lock_transparent"),
            MenuItem("PIN-КОД") { h ->
                h.open(TextScreen("LOCKSCREEN: PIN", {
                    val pin = store.setting("pin", "")
                    listOf("PIN: ${if (pin.isEmpty()) "НЕ УСТАНОВЛЕН" else "УСТАНОВЛЕН"}", "ИЗМЕНЕНИЕ: НАСТРОЙКИ -> PIN-КОД")
                }))
            },
        ),
    )

    private fun controlCenterScreen(): MenuScreen = MenuScreen(
        title = "CONTROL CENTER",
        subtitle = "БЫСТРЫЕ ПЕРЕКЛЮЧАТЕЛИ MOMENTUM",
        items = listOf(
            toggleItem("BLE", "cc_ble", true),
            toggleItem("WI-FI", "cc_wifi", true),
            toggleItem("ЗВУК", "cc_sound", true),
            toggleItem("ВИБРАЦИЯ", "vibration", true),
            toggleItem("ЯРКОСТЬ AUTO", "cc_auto_bright", true),
            toggleItem("OFFLINE MODE", "cc_offline"),
            toggleItem("LOCK DEVICE", "cc_lock"),
            MenuItem("ЯРКОСТЬ", value = "${store.setting("m_brightness", "60")}%") { h ->
                h.input("ЯРКОСТЬ, %", "1-100", store.setting("m_brightness", "60")) { value ->
                    val level = value.toIntOrNull()?.coerceIn(1, 100)
                    if (level == null) h.toast("НУЖНО ЧИСЛО 1-100") else {
                        store.setSetting("m_brightness", level.toString())
                        h.toast("ЯРКОСТЬ: $level%")
                    }
                }
            },
        ),
    )

    private fun bleSpamScreen(): MenuScreen = MenuScreen(
        title = "BLE SPAM",
        subtitle = "НУЖЕН FLIPPER С BLE, ТЕЛЕФОН НЕ ПЕРЕДАЁТ РЕКЛАМУ",
        items = listOf(
            choiceItem("ПЕЙЛОАД", "ble_payload", listOf("IOS", "ANDROID", "WINDOWS", "SAMSUNG", "ALL"), "BLE PAYLOAD"),
            toggleItem("ЦИКЛИЧЕСКАЯ ОТПРАВКА", "ble_cycle"),
            toggleItem("СЛУЧАЙНЫЙ MAC", "ble_random_mac"),
            MenuItem("СТАТУС") { h ->
                h.open(TextScreen("BLE SPAM: СТАТУС", {
                    listOf(
                        "ПЕЙЛОАД: ${store.setting("m_ble_payload", "IOS")}",
                        "ЦИКЛ: ${onOff("m_ble_cycle")}",
                        "MAC: ${onOff("m_ble_random_mac")}",
                        "ЗАПУСК: ТОЛЬКО НА FLIPPER ZERO",
                    )
                }))
            },
            MenuItem("ЗАПУСТИТЬ") { h -> h.toast("ЗАПУСК DOSTUPEN ТОЛЬКО НА FLIPPER") },
        ),
    )

    private fun findMyScreen(): MenuScreen = MenuScreen(
        title = "FIND MY FLIPPER",
        subtitle = "ПОИСК ПО BLE-СЕТИ",
        items = listOf(
            toggleItem("СЛУЖБА ВКЛЮЧЕНА", "findmy"),
            MenuItem("ИНТЕРВАЛ, С", value = store.setting("m_findmy_interval", "60")) { h ->
                h.input("ИНТЕРВАЛ, СЕКУНД", "10-600", store.setting("m_findmy_interval", "60")) { value ->
                    val seconds = value.toIntOrNull()?.coerceIn(10, 600)
                    if (seconds == null) h.toast("НУЖНО ЧИСЛО 10-600") else {
                        store.setSetting("m_findmy_interval", seconds.toString())
                        h.toast("ИНТЕРВАЛ: $seconds С")
                    }
                }
            },
            toggleItem("ОТПРАВЛЯТЬ КООРДИНАТЫ", "findmy_gps"),
            MenuItem("ПОСЛЕДНЕЕ МЕСТОПОЛОЖЕНИЕ") { h ->
                h.open(TextScreen("FIND MY", {
                    listOf("ШИРОТА: ${store.setting("m_last_lat", "НЕТ")}", "ДОЛГОТА: ${store.setting("m_last_lon", "НЕТ")}", "ПОИСК ЧЕРЕЗ СЕТЬ FLIPPER")
                }))
            },
        ),
    )

    private fun badKbScreen(): MenuScreen = MenuScreen(
        title = "BAD KB / MOMENTUM",
        subtitle = "РАСШИРЕННАЯ КЛАВИАТУРА И PAYLOADS",
        items = listOf(
            choiceItem("РАСКЛАДКА", "badkb_layout", listOf("QWERTY", "AZERTY", "QWERTZ", "COLEMAK", "DVORAK"), "KEYBOARD LAYOUT"),
            toggleItem("EXTRA OPTIONS", "badkb_extra"),
            toggleItem("ПОВТОР КОМАНД", "badkb_repeat"),
            toggleItem("AUTO ENTER", "badkb_autoenter"),
            toggleItem("STEALTH MODE", "badkb_stealth"),
            MenuItem("РЕДАКТОР") { h -> h.toast("ИСПОЛЬЗУЙТЕ BADUSB -> НОВЫЙ СКРИПТ") },
            MenuItem("ПРИМЕРЫ КОМАНД") { h ->
                h.open(TextScreen("BAD KB: КОМАНДЫ", {
                    listOf("GUI r", "STRING cmd", "ENTER", "DELAY 500", "CTRL SHIFT ESC", "ALT F4")
                }))
            },
        ),
    )

    private fun nfcExtendedScreen(): MenuScreen = MenuScreen(
        title = "NFC EXTENDED",
        subtitle = "MOMENTUM NFC-ПРОТОКОЛЫ И ПАРСЕРЫ",
        items = listOf(
            toggleItem("NFC TYPE 4 / NTAG4XX", "nfc_type4"),
            toggleItem("NFC EMV PARSER", "nfc_emv"),
            toggleItem("MIFARE UL-C KEYS", "nfc_ulc"),
            toggleItem("MFKEY 4.0", "nfc_mfkey"),
            toggleItem("FELICA TOOLS", "nfc_felica"),
            toggleItem("AMIIBO TOOLKIT", "nfc_amiibo"),
            toggleItem("NFC MAKER", "nfc_maker"),
            toggleItem("NFC PLAYLIST", "nfc_playlist"),
            MenuItem("ОТКРЫТЬ NFC") { h -> h.open(openNfc()) },
            MenuItem("СПИСОК ПАРСЕРОВ") { h ->
                h.open(TextScreen("NFC: ПАРСЕРЫ", {
                    listOf("MIFARE CLASSIC", "ULTRALIGHT / UL-C", "NTAG4xx TYPE 4", "FELICA", "AMIIBO", "SAFLOK", "BANAPASS", "METROFLIP")
                }))
            },
        ),
    )

    private fun subGhzExtendedScreen(): MenuScreen = MenuScreen(
        title = "SUB-GHZ EXTENDED",
        subtitle = "ПРОТОКОЛЫ, ЧАСТОТЫ, SUBDRIVING",
        items = listOf(
            toggleItem("ДУБЛИКАТЫ: ИГНОР", "subghz_dupes"),
            toggleItem("АВТОСОХРАНЕНИЕ", "subghz_autosave", true),
            toggleItem("ИСТОРИЯ", "subghz_history", true),
            toggleItem("ROLLING CODE", "rolling_code"),
            toggleItem("PROTOPIRATE", "subghz_protopirate"),
            toggleItem("SUB-GHZ SCHEDULER", "subghz_scheduler"),
            MenuItem("СВОИ ЧАСТОТЫ", value = store.setting("m_freqs", "433.92").take(12)) { h ->
                h.input("ЧАСТОТЫ ЧЕРЕЗ ЗАПЯТУЮ", "315,433.92,868.35", store.setting("m_freqs", "433.92")) { value ->
                    if (value.isNotBlank()) {
                        store.setSetting("m_freqs", value)
                        h.toast("ЧАСТОТЫ СОХРАНЕНЫ")
                    }
                }
            },
            MenuItem("SUBDRIVING: КООРДИНАТЫ") { h ->
                h.input("ШИРОТА", "55.7558", store.setting("m_last_lat", "55.7558")) { lat ->
                    h.input("ДОЛГОТА", "37.6173", store.setting("m_last_lon", "37.6173")) { lon ->
                        store.setSetting("m_last_lat", lat)
                        store.setSetting("m_last_lon", lon)
                        h.toast("КООРДИНАТЫ СОХРАНЕНЫ")
                    }
                }
            },
            MenuItem("ДИАПАЗОНЫ") { h ->
                h.open(TextScreen("SUB-GHZ: ДИАПАЗОНЫ", {
                    listOf("ДИАПАЗОН 281-361 МГц", "ДИАПАЗОН 378-481 МГц", "ДИАПАЗОН 749-962 МГц", "ЗАВИСИТ ОТ РЕГИОНА")
                }))
            },
        ),
    )

    private fun wifiScreen(): MenuScreen = MenuScreen(
        title = "WI-FI / WARDRIVER",
        subtitle = "MOMENTUM WI-FI И ГЕОЛОКАЦИЯ",
        items = listOf(
            MenuItem("СКАНЕР WI-FI / BLE") { h -> h.open(openScanner()) },
            toggleItem("WARDRIVER", "wardriver"),
            toggleItem("WIFI ПРОШИВКА", "wifi_setup"),
            toggleItem("FLIPHTTP", "fliphttp"),
            toggleItem("SOFTAP", "softap"),
            MenuItem("FLIPMAP") { h ->
                h.open(TextScreen("FLIPMAP", {
                    listOf("КАРТА TOЧЕК WI-FI", "НУЖЕН GPS-МОДУЛЬ", "СОХРАНЕНИЕ В /maps")
                }))
            },
            MenuItem("TELEGRAM") { h ->
                h.open(TextScreen("FLIPTELEGRAM", {
                    listOf("НУЖЕН WIFI ИМОДУЛЬ", "ЧЕРЕЗ FLIPHTTP", "ОТПРАВКА СООБЩЕНИЙ И ФАЙЛОВ")
                }))
            },
        ),
    )

    private fun fileManagerScreen(): MenuScreen = MenuScreen(
        title = "FILE MANAGER",
        subtitle = "ПОИСК И УПРАВЛЕНИЕ ФАЙЛАМИ",
        items = listOf(
            MenuItem("ПОИСК ПО ВСЕМУ") { h -> searchFlow(h) },
            toggleItem("ПОКАЗЫВАТЬ СКРЫТЫЕ", "hidden_files"),
            toggleItem("ПАПКИ СВЕРХУ", "folders_top", true),
            toggleItem("ПОКАЗ ПУТИ", "show_path", true),
            toggleItem("CUT/COPY/PASTE", "file_ops", true),
            MenuItem("НОВАЯ ПАПКА") { h ->
                h.input("ИМЯ ПАПКИ", "MY_FOLDER", "NEW_DIR") { name ->
                    if (name.isNotBlank()) h.toast("ПАПКА: /$name // ДЕМО")
                }
            },
            MenuItem("АРХИВ ПРИЛОЖЕНИЯ") { h ->
                h.open(TextScreen("АРХИВ", {
                    listOf("СИГНАЛЫ: ${store.signals().size}", "RFID: ${store.rfidCards().size}", "iBUTTON: ${store.ibuttons().size}", "СКРИПТЫ: ${store.scripts().size}")
                }))
            },
        ),
    )

    private fun searchFlow(host: ConsoleHost) {
        host.input("ПОИСК", "Название или категория", "BLE") { query ->
            val lower = query.trim().lowercase()
            val found = catalog.filter {
                it.name.lowercase().contains(lower) || it.category.lowercase().contains(lower)
            }.map { "${it.category}: ${it.name}" }
            val saved = store.signals().filter { it.name.lowercase().contains(lower) }.map { "SUBGHZ: ${it.name}" } +
                store.rfidCards().filter { it.name.lowercase().contains(lower) }.map { "RFID: ${it.name}" } +
                store.scripts().filter { it.name.lowercase().contains(lower) }.map { "BADUSB: ${it.name}" }
            val results = found + saved
            host.open(TextScreen("ПОИСК: $query", {
                if (results.isEmpty()) listOf("НИЧЕГО НЕ НАЙДЕНО") else results.take(12)
            }))
        }
    }

    private fun securityScreen(): MenuScreen = MenuScreen(
        title = "SECURITY / SPOOF",
        subtitle = "ЗАЩИТА И ПОДМЕНА ИДЕНТИФИКАТОРОВ",
        items = listOf(
            toggleItem("LOCK ON BOOT", "lock_boot"),
            toggleItem("FORMAT ON BAD PINS", "format_bad_pin"),
            toggleItem("USB RPC LOCKED", "usb_rpc_locked"),
            toggleItem("BLE RPC LOCKED", "ble_rpc_locked"),
            inputItem("SPOOF NAME", "spoof_name", "Flipper Zero", "ИМЯ УСТРОЙСТВА", "Flipper Zero"),
            inputItem("SPOOF MAC", "spoof_mac", "00:11:22:33:44:55", "MAC-АДРЕС", "AA:BB:CC:DD:EE:FF"),
            inputItem("SPOOF SERIAL", "spoof_serial", "FLZ000000", "СЕРИЙНЫЙ НОМЕР", "FLZ000000"),
            MenuItem("ПРОФИЛЬ") { h ->
                h.open(TextScreen("SPOOF: ПРОФИЛЬ", {
                    listOf(
                        "ИМЯ: ${store.setting("m_spoof_name", "Flipper Zero")}",
                        "MAC: ${store.setting("m_spoof_mac", "00:11:22:33:44:55")}",
                        "SERIAL: ${store.setting("m_spoof_serial", "FLZ000000")}",
                    )
                }))
            },
        ),
    )

    private fun gpioAppsScreen(): MenuScreen = MenuScreen(
        title = "GPIO / МОДУЛИ",
        subtitle = "РАСШИРЕНИЯ MOMENTUM",
        items = listOf(
            MenuItem("UART-ТЕРМИНАЛ") { h -> h.open(openUsb()) },
            MenuItem("GPIO EXPLORER") { h -> h.open(appDetail(catalog.first { it.name == "GPIO Explorer" })) },
            MenuItem("SPI TERMINAL") { h -> h.open(appDetail(catalog.first { it.name == "SPI Terminal" })) },
            MenuItem("FM TRANSMITTER") { h -> h.open(appDetail(catalog.first { it.name == "FM Transmitter KT0803" })) },
            MenuItem("ESP32 GHOST") { h -> h.open(appDetail(catalog.first { it.name == "Ghost ESP" })) },
            MenuItem("INA METER") { h -> h.open(appDetail(catalog.first { it.name == "INA Meter" })) },
            MenuItem("CO2 LOGGER") { h -> h.open(appDetail(catalog.first { it.name == "MH-Z19 CO2 Logger" })) },
            MenuItem("FLIPBOARD") { h -> h.open(appDetail(catalog.first { it.name == "FlipBoard Blinky" })) },
        ),
    )

    private fun diskJsScreen(): MenuScreen = MenuScreen(
        title = "DISK / JS SCRIPTS",
        subtitle = "ХРАНИЛИЩЕ И СКРИПТИНГ",
        items = listOf(
            toggleItem("DISK IMAGE", "disk_image"),
            toggleItem("JS API", "js_api", true),
            toggleItem("USBDISK", "usbdisk", true),
            toggleItem("MASS STORAGE", "mass_storage"),
            MenuItem("JS МОДУЛИ") { h ->
                h.open(TextScreen("JS: МОДУЛИ", {
                    listOf("STORAGE", "GUI", "BLE", "SUBGHZ", "USBDISK", "MASS STORAGE", "NOTIFICATIONS", "INPUT")
                }))
            },
            MenuItem("ДИСКОВЫЙ ОБРАЗ") { h ->
                h.open(TextScreen("DISK IMAGE", {
                    listOf("МОНТИРОВАНИЕ .IMG", "ПРОСМОТР СОДЕРЖИМОГО", "ПЕРЕДАЧА В MASS STORAGE")
                }))
            },
        ),
    )

    private fun catalogScreen(): MenuScreen = MenuScreen(
        title = "КАТАЛОГ MOMENTUM",
        subtitle = "${catalog.size} ПРИЛОЖЕНИЙ И ИНСТРУМЕНТОВ",
        items = listOf(
            MenuItem("ПОИСК ПРИЛОЖЕНИЯ") { h -> searchFlow(h) },
        ) + catalog.map { entry ->
            MenuItem(entry.name, value = entry.category.take(10)) { h -> h.open(appDetail(entry)) }
        },
    )

    private fun appDetail(entry: AppEntry): TextScreen = TextScreen(
        title = entry.name.uppercase(),
        subtitle = entry.category.uppercase(),
        lines = {
            listOf(
                "КАТЕГОРИЯ: ${entry.category}",
                "ОПИСАНИЕ: ${entry.description}",
                "УСТАНОВКА: ЧЕРЕЗ МЕНЕДЖЕР ПРОШИВКИ FLIPPER",
                "ЗДЕСЬ: СПРАВОЧНИК ВОЗМОЖНОСТЕЙ",
            )
        },
    )

    private fun aboutMomentumScreen(): TextScreen = TextScreen(
        title = "О MOMENTUM",
        subtitle = "NEXT-FLIP / КАСТОМНАЯ ПРОШИВКА",
        lines = {
            listOf(
                "ВЕРСИЯ: mntm-012",
                "ПРОЕКТ: NEXT-FLIP MOMENTUM FIRMWARE",
                "ОСНОВА: ОФИЦИАЛЬНАЯ ПРОШИВКА + OFW",
                "ФУНКЦИИ: ASSET PACKS, BLE SPAM, FIND MY",
                "ФУНКЦИИ: BAD KB, NFC TYPE 4, EMV, MFKEY",
                "ФУНКЦИИ: SUBDRIVING, WARDRIVER, FILE SEARCH",
                "ФУНКЦИИ: DISK IMAGES, JS API, KEYBINDS",
                "МЕНЮ: 9 СТИЛЕЙ, CONTROL CENTER",
                "БЕЗОПАСНОСТЬ: LOCK ON BOOT, BAD PIN FORMAT",
                "ЭТА ВКЛАДКА: ЭМУЛЯЦИЯ НАСТРОЕК И СПРАВОЧНИК",
            )
        },
    )

    private fun toggleItem(label: String, key: String, default: Boolean = false): MenuItem =
        MenuItem(label, value = if (store.flag("m_$key", default)) "ВКЛ" else "ВЫКЛ") { h ->
            val next = !store.flag("m_$key", default)
            store.setFlag("m_$key", next)
            h.toast("$label: ${if (next) "ВКЛ" else "ВЫКЛ"}")
        }

    private fun choiceItem(label: String, key: String, options: List<String>, title: String): MenuItem =
        MenuItem(label, value = store.setting("m_$key", options.first()).take(12)) { h ->
            h.choice(title, options) { index ->
                store.setSetting("m_$key", options[index])
                h.toast("$label: ${options[index]}")
            }
        }

    private fun inputItem(label: String, key: String, default: String, title: String, hint: String): MenuItem =
        MenuItem(label, value = store.setting("m_$key", default).take(12)) { h ->
            h.input(title, hint, store.setting("m_$key", default)) { value ->
                if (value.isBlank()) {
                    h.toast("ЗНАЧЕНИЕ ПУСТОЕ")
                } else {
                    store.setSetting("m_$key", value)
                    h.toast("СОХРАНЕНО")
                }
            }
        }

    private fun onOff(key: String): String = if (store.flag("m_$key", false)) "ВКЛ" else "ВЫКЛ"

    private data class AppEntry(
        val name: String,
        val category: String,
        val description: String,
    )

    private val catalog: List<AppEntry> = listOf(
        AppEntry("Momentum Settings", "Settings", "Главное приложение настройки прошивки"),
        AppEntry("Asset Packs", "Interface", "Анимации, иконки и шрифты без пересборки"),
        AppEntry("BLE Spam", "Bluetooth", "Отправка рекламных пакетов iOS, Android, Windows, Samsung"),
        AppEntry("Find My Flipper", "Bluetooth", "Поиск потерянного флиппера по BLE"),
        AppEntry("Bad KB", "USB", "Расширенная клавиатура и дополнительные опции"),
        AppEntry("NFC Maker", "NFC", "Создание пустых и записанных NDEF-меток"),
        AppEntry("Wardriver", "Wi-Fi", "Сбор точек доступа с координатами"),
        AppEntry("File Search", "Tools", "Поиск по SD-карте"),
        AppEntry("Disk Image", "Storage", "Монтирование и просмотр образов дисков"),
        AppEntry("Extended JS", "Scripts", "Расширенный API для сценариев"),
        AppEntry("NFC Type 4", "NFC", "NDEF на NTAG4xx и DESFire"),
        AppEntry("NFC EMV", "NFC", "Базовое чтение информации о карте"),
        AppEntry("MFKey 4.0", "NFC", "Восстановление ключей MIFARE Classic"),
        AppEntry("UL-C Bruteforce", "NFC", "Подбор ключей MIFARE Ultralight C"),
        AppEntry("FeliCa Tools", "NFC", "Дамп и эмуляция FeliCa"),
        AppEntry("Amiibo Toolkit", "NFC", "Работа с картами Amiibo"),
        AppEntry("NFC Playlist", "NFC", "Воспроизведение набора NFC-меток"),
        AppEntry("SubDriving", "Sub-GHz", "Сохранение координат для найденных сигналов"),
        AppEntry("ProtoPirate", "Sub-GHz", "Расширенный анализ протоколов"),
        AppEntry("Rolling Code", "Sub-GHz", "Поддержка часть кодов с плавающим кодом"),
        AppEntry("Sub-GHz Scheduler", "Sub-GHz", "Отправка сигналов по расписанию"),
        AppEntry("HC-11 Modem", "Sub-GHz", "Работа с радиомодемами HC-11"),
        AppEntry("Flipper Share", "Sub-GHz", "Передача файлов между флипперами"),
        AppEntry("Chief Cooker", "Sub-GHz", "Сценарии для Sub-GHz"),
        AppEntry("Geometry Flip", "Games", "Аркадная игра"),
        AppEntry("Asteroids", "Games", "Классическая игра"),
        AppEntry("FlipBoard Blinky", "GPIO", "Светодиодные эффекты FlipBoard"),
        AppEntry("FlipBoard Keyboard", "GPIO", "Клавиатура на FlipBoard"),
        AppEntry("FlipBoard Simon", "GPIO", "Игра Simon на FlipBoard"),
        AppEntry("GPIO Explorer", "GPIO", "Исследование пинов и режимов"),
        AppEntry("SPI Terminal", "GPIO", "Обмен по SPI"),
        AppEntry("FM Transmitter KT0803", "GPIO", "Передача FM-сигнала"),
        AppEntry("Ghost ESP", "GPIO", "Управление ESP32"),
        AppEntry("INA Meter", "GPIO", "Измерение тока и напряжения"),
        AppEntry("MH-Z19 CO2 Logger", "GPIO", "Логгер CO2"),
        AppEntry("FlipMap", "Wi-Fi", "Карта найденных сетей"),
        AppEntry("FlipTelegram", "Wi-Fi", "Мессенджер через FlipperHTTP"),
        AppEntry("Free Roam", "Wi-Fi", "Работа с сетями без подключения"),
        AppEntry("FlipSocial", "Wi-Fi", "Социальная сеть для флипперов"),
        AppEntry("FlipDownloader", "Wi-Fi", "Загрузка репозиториев с GitHub"),
        AppEntry("ESP Flasher", "GPIO", "Прошивка ESP-модулей"),
        AppEntry("FlipWiFi", "Wi-Fi", "Настройка Wi-Fi"),
        AppEntry("Image Viewer", "Media", "Просмотр изображений"),
        AppEntry("Video Player", "Media", "Воспроизведение видео"),
        AppEntry("Fmatrix", "Media", "Визуальные эффекты"),
        AppEntry("Space Playground", "Games", "Космическая игра"),
        AppEntry("Hitachi AC Remote", "Infrared", "Пульт кондиционера Hitachi"),
        AppEntry("Midea AC Remote", "Infrared", "Пульт кондиционера Midea"),
        AppEntry("Mitsubishi AC Remote", "Infrared", "Пульт кондиционера Mitsubishi"),
        AppEntry("Xbox Controller", "Infrared", "Эмуляция контроллера Xbox"),
        AppEntry("LIDAR Emulator", "Infrared", "Имитация лидара"),
        AppEntry("iButton Converter", "iButton", "Преобразование ключей 1-Wire"),
        AppEntry("CAN Tools", "Tools", "Работа с шиной CAN"),
        AppEntry("FlipCrypt", "Tools", "Шифрование и хеширование"),
        AppEntry("Programmer Calculator", "Tools", "Калькулятор для разработчика"),
        AppEntry("Resistance Calculator", "Tools", "Расчёт сопротивлений"),
        AppEntry("Voltage Calculator", "Tools", "Расчёт напряжения"),
        AppEntry("Tasks", "Tools", "Список задач"),
        AppEntry("Pomodoro", "Tools", "Таймер продуктивности"),
        AppEntry("Combo Cracker", "Tools", "Подбор комбинаций замков"),
        AppEntry("KeyCopier", "RFID", "Копирование ключей"),
        AppEntry("Mass Storage", "USB", "Режим USB-диска"),
        AppEntry("USB-MIDI", "USB", "MIDI-устройство"),
        AppEntry("LEGO Dimensions Toy Pad", "USB", "Эмуляция игровой панели"),
        AppEntry("Metroflip", "NFC", "Транспортные карты"),
        AppEntry("Nearby Files", "GPS", "Файлы рядом с координатами"),
        AppEntry("IconEdit", "Tools", "Редактор иконок"),
        AppEntry("Flipper Wedge", "Tools", "Утилита для тестов"),
    )
}
