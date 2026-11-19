package com.pocketmultitool.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.GestureDetector
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.pocketmultitool.app.core.BitmapScreen
import com.pocketmultitool.app.core.ConsoleHost
import com.pocketmultitool.app.core.ConsoleScreen
import com.pocketmultitool.app.core.MenuItem
import com.pocketmultitool.app.core.MenuScreen
import com.pocketmultitool.app.core.RetroConsole
import com.pocketmultitool.app.core.TextScreen
import com.pocketmultitool.app.flipper.FlipperDeps
import com.pocketmultitool.app.flipper.FlipperScreens
import com.pocketmultitool.app.flipper.FlipperStore
import com.pocketmultitool.app.flipper.FlipperTheme
import com.pocketmultitool.app.hardware.Capabilities
import com.pocketmultitool.app.hardware.cards.BarcodeFactory
import com.pocketmultitool.app.hardware.cards.CardStorage
import com.pocketmultitool.app.hardware.cards.LoyaltyCard
import com.pocketmultitool.app.hardware.ir.IrDatabase
import com.pocketmultitool.app.hardware.ir.IrRemote
import com.pocketmultitool.app.hardware.nfc.NfcController
import com.pocketmultitool.app.hardware.nfc.TagRecord
import com.pocketmultitool.app.hardware.nfc.TagStorage
import com.pocketmultitool.app.hardware.scanner.RadioScanner
import com.pocketmultitool.app.hardware.tools.ToneEngine
import com.pocketmultitool.app.hardware.tools.TorchMorse
import com.pocketmultitool.app.hardware.usb.UsbTerminal
import com.pocketmultitool.app.ui.RetroDisplayView
import kotlin.math.abs

class MainActivity : Activity(), ConsoleHost {

    companion object {
        private const val REQ_BLE = 101
        private const val REQ_WIFI = 102
        private const val REQ_CAMERA = 103
    }

    private val handler = Handler(Looper.getMainLooper())
    private val continuations = HashMap<Int, () -> Unit>()
    private val usbLog = ArrayList<String>()
    private var previousBrightness: Float? = null
    private var lastIrMessage = "КОМАНДА НЕ ОТПРАВЛЯЛАСЬ"
    private var lastNfcMessage = "МЕТКА НЕ СЧИТАНА"
    private var scannerMessage = "СКАНИРОВАНИЕ НЕ ЗАПУСКАЛОСЬ"
    private var toneMessage = "ТОН НЕ ВОСПРОИЗВОДИЛСЯ"
    private var torchMessage = "ВСПЫШКА НЕ АКТИВНА"

    private lateinit var display: RetroDisplayView
    private lateinit var console: RetroConsole
    private lateinit var flipperStore: FlipperStore
    private lateinit var flipperScreens: FlipperScreens
    private var vibrator: Vibrator? = null
    private lateinit var ir: IrRemote
    private lateinit var nfc: NfcController
    private lateinit var tagStorage: TagStorage
    private lateinit var cards: CardStorage
    private lateinit var scanner: RadioScanner
    private lateinit var usb: UsbTerminal
    private lateinit var tone: ToneEngine
    private lateinit var torch: TorchMorse

    private val ticker = object : Runnable {
        override fun run() {
            if (::console.isInitialized) console.tick()
            if (::display.isInitialized) display.invalidate()
            handler.postDelayed(this, 300L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flipperStore = FlipperStore(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        buildUi()

        ir = IrRemote(this)
        nfc = NfcController(this)
        tagStorage = TagStorage(this)
        cards = CardStorage(this)
        scanner = RadioScanner(this)
        tone = ToneEngine()
        torch = TorchMorse(this) { on ->
            torchMessage = if (on) "ВСПЫШКА: ВКЛЮЧЕНА" else "ВСПЫШКА: ВЫКЛЮЧЕНА"
            ui { display.invalidate() }
        }
        usb = UsbTerminal(this) { message ->
            ui {
                usbLog.add(0, message)
                while (usbLog.size > 40) usbLog.removeAt(usbLog.size - 1)
                display.invalidate()
            }
        }

        flipperScreens = FlipperScreens(
            context = this,
            store = flipperStore,
            deps = FlipperDeps(
                ir = { irScreen() },
                nfc = { nfcScreen() },
                cards = { cardsScreen() },
                scanner = { scannerScreen() },
                usb = { usbScreen() },
                tools = { toolsScreen() },
                status = { statusScreen() },
                about = { aboutScreen() },
                tagCount = { tagStorage.all().size },
                cardCount = { cards.all().size },
            ),
        )
        console = RetroConsole(this, flipperScreens.mainScreen())
        display.console = console
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(FlipperTheme.SHELL))
        }
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundResource(R.drawable.bg_screen_frame)
            setPadding(dp(10f), dp(12f), dp(10f), dp(8f))
            clipToPadding = true
        }
        display = RetroDisplayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            isClickable = true
        }
        frame.addView(display)
        root.addView(frame)

        val keys = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(86f))
        }
        keys.addView(makeKey("▲\nВВЕРХ", R.drawable.bg_key, 1f) { if (::console.isInitialized) console.up() })
        keys.addView(makeKey("▼\nВНИЗ", R.drawable.bg_key, 1f) {
            if (::console.isInitialized) console.down(console.rows().size)
        })
        keys.addView(makeKey("OK\nВЫБОР", R.drawable.bg_key_ok, 1.15f) { if (::console.isInitialized) console.ok() })
        keys.addView(makeKey("◀\nНАЗАД", R.drawable.bg_key_back, 1f) { if (::console.isInitialized) console.back() })
        root.addView(keys)
        setContentView(root)

        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!::console.isInitialized) return false
                val index = display.rowAt(e.y)
                if (index < 0) return false
                if (index == console.selection) {
                    console.ok()
                } else {
                    console.select(index)
                    display.invalidate()
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (!::console.isInitialized) return false
                if (abs(velocityY) < 70f) return false
                if (velocityY < 0) console.down(console.rows().size) else console.up()
                display.invalidate()
                return true
            }
        })
        display.setOnTouchListener { _: View?, event: MotionEvent -> detector.onTouchEvent(event) }
    }

    private fun makeKey(label: String, background: Int, weight: Float, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setBackgroundResource(background)
            setTextColor(Color.parseColor(FlipperTheme.YELLOW))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dp(5f), dp(9f), dp(5f), dp(12f))
            }
            setOnClickListener { action() }
        }

    override fun onResume() {
        super.onResume()
        if (::nfc.isInitialized) nfc.enableForeground()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        if (::nfc.isInitialized) nfc.disableForeground()
        super.onPause()
    }

    override fun onDestroy() {
        if (::scanner.isInitialized) scanner.stopAll()
        if (::usb.isInitialized) usb.destroy()
        if (::tone.isInitialized) tone.stop()
        if (::torch.isInitialized) torch.stop()
        restoreBrightness()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!::console.isInitialized || !console.back()) super.onBackPressed()
    }

    @SuppressLint("MissingSuperCall", "DEPRECATION")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!::nfc.isInitialized) return
        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag ?: return
        nfc.handleTag(tag) { result ->
            lastNfcMessage = result
            ui {
                toast(result)
                display.invalidate()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            continuations.remove(requestCode)?.invoke()
        } else {
            continuations.remove(requestCode)
            toast("ОТКАЗ В РАЗРЕШЕНИИ")
        }
    }

    override fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun open(screen: ConsoleScreen) = console.open(screen)

    override fun back() {
        console.back()
    }

    override fun input(title: String, hint: String, default: String, onResult: (String) -> Unit) {
        val field = EditText(this).apply {
            this.hint = hint
            setText(default)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 8
            setTextColor(Color.parseColor("#FFD08A"))
            setHintTextColor(Color.parseColor("#7A5218"))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(field)
            .setPositiveButton("ОК") { _, _ -> onResult(field.text.toString().trim()) }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    override fun choice(title: String, options: List<String>, onChoose: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which -> onChoose(which) }
            .setNegativeButton("ОТМЕНА", null)
            .show()
    }

    override fun confirm(title: String, message: String, onYes: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ДА") { _, _ -> onYes() }
            .setNegativeButton("НЕТ", null)
            .show()
    }

    @Suppress("DEPRECATION")
    override fun vibrate(ms: Long) {
        if (::flipperStore.isInitialized && !flipperStore.flag("vibration", true)) return
        vibrator?.let { if (it.hasVibrator()) it.vibrate(ms) }
    }

    override fun requestPermissions(permissions: Array<String>, code: Int, onGranted: () -> Unit) {
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            onGranted()
            return
        }
        continuations[code] = onGranted
        super.requestPermissions(missing.toTypedArray(), code)
    }

    override fun ui(block: () -> Unit) {
        handler.post(block)
    }

    private fun setBrightness(value: Float) {
        if (previousBrightness == null) previousBrightness = window.attributes.screenBrightness
        val params = window.attributes
        params.screenBrightness = value
        window.attributes = params
    }

    private fun restoreBrightness() {
        previousBrightness?.let { value ->
            val params = window.attributes
            params.screenBrightness = value
            window.attributes = params
        }
        previousBrightness = null
    }

    private fun irScreen(): MenuScreen = MenuScreen(
        title = "ИК-ПУЛЬТ",
        subtitle = "ПЕРЕДАТЧИК: ${ir.range}",
        items = listOf(
            MenuItem("БАЗА ТЕЛЕВИЗОРОВ", badge = "NEC") { h -> h.open(irBrandsScreen()) },
            MenuItem("ПОСЛЕДНЯЯ КОМАНДА", value = lastIrMessage.take(12)) { h ->
                h.open(TextScreen("ИК: ПОСЛЕДНЯЯ", { listOf(lastIrMessage) }))
            },
            MenuItem("RAW / NEC ВРУЧНУЮ") { h -> h.open(irRawScreen()) },
            MenuItem("ДИАГНОСТИКА ИК") { h ->
                h.open(TextScreen("ИК: ДИАГНОСТИКА", {
                    listOf(
                        "ПЕРЕДАТЧИК: ${if (ir.available) "ЕСТЬ" else "ОТСУТСТВУЕТ"}",
                        "ДИАПАЗОН: ${ir.range}",
                        "ФОРМАТ: NEC 32-bit, 38 кГц",
                        "ПОСЛЕДНЯЯ: $lastIrMessage",
                    )
                }))
            },
        ),
    )

    private fun irBrandsScreen(): MenuScreen = MenuScreen(
        title = "ИК: БРЕНД",
        subtitle = "ВСТРОЕННАЯ БАЗА КОДОВ",
        items = IrDatabase.brands.keys.map { brand ->
            MenuItem(brand) { h -> h.open(irKeysScreen(brand)) }
        },
    )

    private fun irKeysScreen(brand: String): MenuScreen {
        val keys = IrDatabase.brands[brand] ?: IrDatabase.brands.values.first()
        return MenuScreen(
            title = "ИК: $brand",
            subtitle = "NEC @ 38 кГц",
            items = listOf(
                MenuItem("ПИТАНИЕ") { sendIr(keys.power, "ПИТАНИЕ") },
                MenuItem("ГРОМКОСТЬ +") { sendIr(keys.volumeUp, "ГРОМКОСТЬ +") },
                MenuItem("ГРОМКОСТЬ -") { sendIr(keys.volumeDown, "ГРОМКОСТЬ -") },
                MenuItem("КАНАЛ +") { sendIr(keys.channelUp, "КАНАЛ +") },
                MenuItem("КАНАЛ -") { sendIr(keys.channelDown, "КАНАЛ -") },
                MenuItem("MUTE") { sendIr(keys.mute, "MUTE") },
                MenuItem("RAW: NEC-КОД") { h ->
                    h.input("NEC-КОД", "Например: E0E040BF", "E0E040BF") { text ->
                        try {
                            val code = text.removePrefix("0x").removePrefix("0X").replace(" ", "").toLong(16)
                            lastIrMessage = ir.sendNec(code)
                        } catch (e: Exception) {
                            lastIrMessage = "ОШИБКА КОДА: ${e.message ?: "неверный формат"}"
                        }
                        h.toast(lastIrMessage)
                    }
                },
                MenuItem("RAW: ИМПУЛЬСЫ") { h -> h.open(irRawScreen()) },
            ),
        )
    }

    private fun irRawScreen(): MenuScreen = MenuScreen(
        title = "ИК: RAW-РЕЖИМ",
        subtitle = "ИМПУЛЬСЫ В МИКРОСЕКУНДАХ",
        items = listOf(
            MenuItem("ВВЕСТИ И ОТПРАВИТЬ") { h ->
                h.input("НЕСУЩАЯ ЧАСТОТА", "38000", "38000") { carrierText ->
                    h.input("ПОСЛЕДОВАТЕЛЬНОСТЬ", "9000,4500,560,560,...", "9000,4500,560,560,560") { patternText ->
                        try {
                            val carrier = carrierText.toIntOrNull() ?: 38000
                            val pattern = ir.parsePattern(patternText)
                            lastIrMessage = ir.sendRaw(carrier, pattern)
                        } catch (e: Exception) {
                            lastIrMessage = "ОШИБКА ИМПУЛЬСОВ: ${e.message ?: "неверный формат"}"
                        }
                        h.toast(lastIrMessage)
                    }
                }
            },
            MenuItem("ПОСЛЕДНЯЯ КОМАНДА", value = lastIrMessage.take(12)) { h ->
                h.open(TextScreen("ИК: ПОСЛЕДНЯЯ", { listOf(lastIrMessage) }))
            },
        ),
    )

    private fun sendIr(code: Long, label: String) {
        lastIrMessage = "$label: ${ir.sendNec(code)}"
        toast(lastIrMessage)
        display.invalidate()
    }

    private fun nfcScreen(): MenuScreen = MenuScreen(
        title = "NFC МЕНЕДЖЕР",
        subtitle = if (nfc.enabled) "АДАПТЕР ВКЛЮЧЁН" else "АДАПТЕР ВЫКЛЮЧЕН ИЛИ ОТСУТСТВУЕТ",
        items = listOf(
            MenuItem("ЧИТАТЬ МЕТКУ") {
                lastNfcMessage = "ПОДНЕСИТЕ МЕТКУ К ЗАДНЕЙ ПАНЕЛИ"
                toast(lastNfcMessage)
            },
            MenuItem("ПОСЛЕДНЯЯ МЕТКА", value = lastNfcMessage.take(12)) { h ->
                h.open(TextScreen("NFC: ПОСЛЕДНЯЯ", { listOf(lastNfcMessage) }))
            },
            MenuItem("СОХРАНИТЬ ПОСЛЕДНЮЮ") { h ->
                val info = nfc.lastInfo
                if (info == null) {
                    h.toast("СНАЧАЛА СЧИТАЙТЕ МЕТКУ")
                } else {
                    tagStorage.save(TagRecord(info.uid, info.techs.joinToString(", "), info.details.joinToString(" | "), info.time))
                    h.toast("МЕТКА СОХРАНЕНА")
                }
            },
            MenuItem("БАЗА МЕТОК", badge = "${tagStorage.all().size}") { h -> h.open(tagListScreen()) },
            MenuItem("ЗАПИСАТЬ ТЕКСТ") { h ->
                h.input("ТЕКСТ ДЛЯ ЗАПИСИ", "Hello from Pocket Multitool", "HELLO") { text ->
                    if (text.isBlank()) {
                        h.toast("ПУСТОЙ ТЕКСТ")
                    } else {
                        nfc.queueText(text)
                        lastNfcMessage = "ПОДНЕСИТЕ ПУСТУЮ NDEF-МЕТКУ ДЛЯ ЗАПИСИ"
                        h.toast(lastNfcMessage)
                    }
                }
            },
            MenuItem("ЗАПИСАТЬ ССЫЛКУ") { h ->
                h.input("URL", "https://example.com", "https://example.com") { url ->
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        h.toast("НУЖЕН АДРЕС http:// ИЛИ https://")
                    } else {
                        nfc.queueUri(url)
                        lastNfcMessage = "ПОДНЕСИТЕ ПУСТУЮ NDEF-МЕТКУ ДЛЯ ЗАПИСИ"
                        h.toast(lastNfcMessage)
                    }
                }
            },
            MenuItem("ОТМЕНИТЬ ЗАПИСЬ") { h ->
                nfc.cancelWrite()
                h.toast("ЗАПИСЬ ОТМЕНЕНА")
            },
            MenuItem("ДИАГНОСТИКА NFC") { h ->
                h.open(TextScreen("NFC: ДИАГНОСТИКА", {
                    listOf(
                        "АДАПТЕР: ${if (nfc.available) "ЕСТЬ" else "НЕТ"}",
                        "СОСТОЯНИЕ: ${if (nfc.enabled) "ВКЛЮЧЁН" else "ВЫКЛЮЧЕН"}",
                        "ОЖИДАНИЕ ЗАПИСИ: ${if (nfc.waitingForWrite) "ДА" else "НЕТ"}",
                        "МЕТОК В БАЗЕ: ${tagStorage.all().size}",
                    )
                }))
            },
        ),
    )

    private fun tagListScreen(): MenuScreen {
        val tags = tagStorage.all()
        val items = tags.map { tag ->
            MenuItem(tag.uid, value = tag.time) { h -> h.open(tagDetailScreen(tag.uid)) }
        }.toMutableList()
        items.add(MenuItem("ОЧИСТИТЬ БАЗУ") { h ->
            h.confirm("ОЧИСТКА БАЗЫ", "Удалить все сохранённые метки?", onYes = {
                tagStorage.clear()
                h.back()
                h.toast("БАЗА ОЧИЩЕНА")
            })
        })
        return MenuScreen("NFC: БАЗА МЕТОК", items, subtitle = "${tags.size} ЗАПИСЕЙ")
    }

    private fun tagDetailScreen(uid: String): ConsoleScreen {
        val tag = tagStorage.all().firstOrNull { it.uid == uid }
            ?: return TextScreen("МЕТКА НЕ НАЙДЕНА", { listOf("ЗАПИСЬ УДАЛЕНА") })
        return TextScreen(
            title = "МЕТКА ${tag.uid.take(12)}",
            lines = {
                listOf(
                    "UID: ${tag.uid}",
                    "ТЕХ: ${tag.techs}",
                    "ВРЕМЯ: ${tag.time}",
                    "СОДЕРЖИМОЕ:",
                ) + tag.ndef.lines()
            },
            footer = "OK УДАЛИТЬ   ◀ НАЗАД",
            onAction = { h ->
                h.confirm("УДАЛЕНИЕ", "Удалить метку ${tag.uid}?", onYes = {
                    tagStorage.remove(uid)
                    h.back()
                    h.toast("МЕТКА УДАЛЕНА")
                })
            },
        )
    }

    private fun cardsScreen(): MenuScreen = MenuScreen(
        title = "КАРТЫ И КОДЫ",
        subtitle = "QR / EAN-13 / CODE-128",
        items = listOf(
            MenuItem("ДОБАВИТЬ КАРТУ") { h -> addCardFlow(h) },
            MenuItem("МОИ КАРТЫ", badge = "${cards.all().size}") { h -> h.open(cardListScreen()) },
            MenuItem("ОЧИСТИТЬ БАЗУ") { h ->
                h.confirm("ОЧИСТКА БАЗЫ", "Удалить все карты?", onYes = {
                    cards.clear()
                    h.toast("БАЗА ОЧИЩЕНА")
                })
            },
        ),
    )

    private fun addCardFlow(host: ConsoleHost) {
        host.input("НОВАЯ КАРТА", "Название", "Магазин") { name ->
            if (name.isBlank()) {
                host.toast("НУЖНО НАЗВАНИЕ")
                return@input
            }
            host.input("НОМЕР КАРТЫ", "Цифры или текст", "4600000000000") { number ->
                if (number.isBlank()) {
                    host.toast("НУЖЕН НОМЕР")
                    return@input
                }
                host.choice("ФОРМАТ КОДА", BarcodeFactory.formats) { index ->
                    val format = BarcodeFactory.formats[index]
                    if (format == BarcodeFactory.EAN13) {
                        try {
                            BarcodeFactory.normalizeEan13(number)
                        } catch (e: Exception) {
                            host.toast("EAN-13: ${e.message}")
                            return@choice
                        }
                    }
                    cards.save(LoyaltyCard(name, number, format))
                    host.toast("КАРТА СОХРАНЕНА")
                    host.open(cardListScreen())
                }
            }
        }
    }

    private fun cardListScreen(): MenuScreen {
        val all = cards.all()
        val items = all.map { card ->
            MenuItem(card.name, value = card.number, badge = card.format) { h -> h.open(cardViewScreen(card)) }
        }.toMutableList()
        items.add(0, MenuItem("+ ДОБАВИТЬ КАРТУ") { h -> addCardFlow(h) })
        return MenuScreen("КАРТЫ: СПИСОК", items, subtitle = "${all.size} КАРТ")
    }

    private fun cardViewScreen(card: LoyaltyCard): BitmapScreen = object : BitmapScreen(
        title = "КАРТА: ${card.name.uppercase()}",
        subtitle = "${card.format} / ЯРКОСТЬ 100%",
        picture = {
            try {
                BarcodeFactory.make(card.number, card.format)
            } catch (e: Exception) {
                errorBitmap("ОШИБКА КОДА: ${e.message ?: "неверные данные"}")
            }
        },
        caption = {
            listOf(
                "НОМЕР: ${card.number}",
                "ФОРМАТ: ${card.format}",
                "ДЛЯ СКАНИРОВАНИЯ ДЕРЖИТЕ ЭКРАН ПЕРПЕНДИКУЛЯРНО",
            )
        },
    ) {
        override fun onEnter(host: ConsoleHost) {
            setBrightness(1.0f)
        }

        override fun onExit(host: ConsoleHost) {
            restoreBrightness()
            super.onExit(host)
        }
    }

    private fun errorBitmap(message: String): Bitmap {
        val bitmap = Bitmap.createBitmap(560, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8B1A1A")
            textSize = 20f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText(message, 16f, 55f, paint)
        return bitmap
    }

    private fun scannerScreen(): MenuScreen = MenuScreen(
        title = "РАДИО-СКАНЕР",
        subtitle = if (scanner.bluetoothAvailable) "BLE: ${if (scanner.bluetoothEnabled) "ВКЛ" else "ВЫКЛ"}" else "BLE ОТСУТСТВУЕТ",
        items = listOf(
            MenuItem("BLE: СКАНИРОВАТЬ") { h -> startBle(h) },
            MenuItem("WI-FI: СКАНИРОВАТЬ") { h -> startWifi(h) },
            MenuItem("РЕЗУЛЬТАТЫ (LIVE)", badge = "${scanner.bleResults.size + scanner.wifiResults.size}") { h ->
                h.open(liveScanScreen())
            },
            MenuItem("ОСТАНОВИТЬ ВСЁ") {
                scanner.stopAll()
                scannerMessage = "СКАНИРОВАНИЕ ОСТАНОВЛЕНО"
                toast(scannerMessage)
            },
            MenuItem("ДИАГНОСТИКА") { h -> h.open(scanStatusScreen()) },
        ),
    )

    private fun startBle(host: ConsoleHost) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        host.requestPermissions(permissions, REQ_BLE) {
            scannerMessage = scanner.startBle()
            host.toast(scannerMessage)
        }
    }

    private fun startWifi(host: ConsoleHost) {
        host.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQ_WIFI) {
            scannerMessage = scanner.startWifi()
            host.toast(scannerMessage)
        }
    }

    private fun liveScanScreen(): TextScreen = TextScreen(
        title = "СКАНЕР: РЕЗУЛЬТАТЫ",
        subtitle = "ОБНОВЛЕНИЕ КАЖДЫЕ 0.3 С",
        footer = "OK ОБНОВИТЬ   ◀ НАЗАД",
        refreshMs = 300L,
        lines = {
            val result = ArrayList<String>()
            result.add("BLE: ${scanner.bleResults.size} ${if (scanner.scanningBle) "[СКАН]" else ""}")
            scanner.bleResults.take(7).forEach { device ->
                result.add("${device.name.take(10)} ${device.rssi}дБм")
            }
            result.add("WI-FI: ${scanner.wifiResults.size} ${if (scanner.scanningWifi) "[СКАН]" else ""}")
            scanner.wifiResults.take(7).forEach { net ->
                result.add("${net.ssid.take(10)} CH${net.channel} ${net.level}дБм")
            }
            if (scanner.lastError.isNotEmpty()) result.add("ОШИБКА: ${scanner.lastError}")
            result.add(scannerMessage.take(34))
            result
        },
    )

    private fun scanStatusScreen(): TextScreen = TextScreen(
        title = "СКАНЕР: СТАТУС",
        lines = {
            listOf(
                "BLUETOOTH: ${if (scanner.bluetoothAvailable) "ЕСТЬ" else "НЕТ"}",
                "BT СОСТОЯНИЕ: ${if (scanner.bluetoothEnabled) "ВКЛЮЧЁН" else "ВЫКЛЮЧЕН"}",
                "BLE СКАН: ${if (scanner.scanningBle) "АКТИВЕН" else "ОСТАНОВЛЕН"}",
                "WI-FI: ${if (scanner.wifiAvailable) "ЕСТЬ" else "НЕТ"}",
                "WI-FI СКАН: ${if (scanner.scanningWifi) "АКТИВЕН" else "ОСТАНОВЛЕН"}",
                "BLE НАЙДЕНО: ${scanner.bleResults.size}",
                "WI-FI НАЙДЕНО: ${scanner.wifiResults.size}",
                "ПОСЛЕДНЕЕ: $scannerMessage",
            )
        },
    )

    private fun usbScreen(): MenuScreen = MenuScreen(
        title = "USB-OTG ТЕРМИНАЛ",
        subtitle = if (usb.available) "${usb.devices().size} УСТРОЙСТВ / ${usb.baud} БОД" else "USB HOST ОТСУТСТВУЕТ",
        items = listOf(
            MenuItem("УСТРОЙСТВА", badge = "${usb.devices().size}") { h -> h.open(usbDevicesScreen()) },
            MenuItem("ВЫБРАТЬ ПЕРВОЕ") { h ->
                val device = usb.devices().firstOrNull()
                if (device == null) h.toast("УСТРОЙСТВО НЕ НАЙДЕНО") else usb.select(device)
            },
            MenuItem("СКОРОСТЬ", value = "${usb.baud}") { h ->
                usb.baud = if (usb.baud == 9600) 115200 else 9600
                h.toast("СКОРОСТЬ: ${usb.baud} БОД")
            },
            MenuItem("ОТКРЫТЬ ПОРТ", badge = if (usb.isOpen) "ОТКРЫТ" else "ЗАКРЫТ") { h ->
                h.toast(usb.open())
            },
            MenuItem("ОТПРАВИТЬ ТЕКСТ") { h ->
                h.input("КОМАНДА", "Например: AT\\r\\n", "AT\\r") { text ->
                    h.toast(usb.send(text))
                }
            },
            MenuItem("ПРИНЯТЬ ОТВЕТ") { h -> h.open(TextScreen("USB: ОТВЕТ", { listOf(usb.receive()) })) },
            MenuItem("ЗАКРЫТЬ ПОРТ") { h -> h.toast(usb.close()) },
            MenuItem("ЖУРНАЛ", badge = "${usbLog.size}") { h -> h.open(usbLogScreen()) },
        ),
    )

    private fun usbDevicesScreen(): MenuScreen {
        val devices = usb.devices()
        val items = devices.map { device ->
            MenuItem(device.deviceName, value = "%04X:%04X".format(device.vendorId, device.productId)) { h ->
                usb.select(device)
                h.back()
            }
        }
        return MenuScreen("USB: УСТРОЙСТВА", items, subtitle = "ПОДКЛЮЧИТЕ УСТРОЙСТВО ЧЕРЕЗ OTG")
    }

    private fun usbLogScreen(): TextScreen = TextScreen(
        title = "USB: ЖУРНАЛ",
        lines = { usbLog.toList() },
        footer = "OK ОЧИСТИТЬ   ◀ НАЗАД",
        onAction = { usbLog.clear() },
    )

    private fun toolsScreen(): MenuScreen = MenuScreen(
        title = "ИНСТРУМЕНТЫ",
        subtitle = "AUDIO / MORSE / FLASH",
        items = listOf(
            MenuItem("ГЕНЕРАТОР ТОНА") { h ->
                h.input("ЧАСТОТА, ГЦ", "20 - 20000", "440") { text ->
                    val frequency = text.toIntOrNull()
                    if (frequency == null) {
                        h.toast("НУЖНО ЧИСЛО")
                    } else {
                        toneMessage = tone.play(frequency)
                        h.toast(toneMessage)
                    }
                }
            },
            MenuItem("СТОП ТОН") {
                tone.stop()
                toneMessage = "ТОН ОСТАНОВЛЕН"
                toast(toneMessage)
            },
            MenuItem("МОРЗЕ ВСПЫШКОЙ") { h ->
                h.input("СООБЩЕНИЕ", "SOS", "SOS") { text ->
                    if (text.isBlank()) {
                        h.toast("ПУСТОЕ СООБЩЕНИЕ")
                    } else {
                        h.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA) {
                            if (!torch.available) {
                                h.toast("ВСПЫШКА НЕ ОБНАРУЖЕНА")
                            } else {
                                torch.play(text)
                                h.toast("ПЕРЕДАЧА: ${text.take(18)}")
                            }
                        }
                    }
                }
            },
            MenuItem("ТЕСТ ВСПЫШКИ") { h ->
                h.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA) {
                    if (!torch.available) {
                        h.toast("ВСПЫШКА НЕ ОБНАРУЖЕНА")
                    } else {
                        torch.play("E", 150)
                        h.toast("ИМПУЛЬС ВСПЫШКИ")
                    }
                }
            },
            MenuItem("СТОП МОРЗЕ") {
                torch.stop()
                torchMessage = "ПЕРЕДАЧА ОСТАНОВЛЕНА"
                toast(torchMessage)
            },
            MenuItem("ВИБРАЦИЯ") {
                vibrate(220)
                toast("ВИБРАЦИЯ")
            },
            MenuItem("СТАТУС") { h -> h.open(toolsStatusScreen()) },
        ),
    )

    private fun toolsStatusScreen(): TextScreen = TextScreen(
        title = "ИНСТРУМЕНТЫ: СТАТУС",
        lines = {
            listOf(
                torch.status(),
                "СОСТОЯНИЕ: $torchMessage",
                "ТОН: ${if (tone.isPlaying) "ИГРАЕТ" else "СТОП"}",
                "ПОСЛЕДНИЙ: $toneMessage",
                "АЗБУКА: .- / -... / -.-.",
                "ДИАПАЗОН ТОНА: 20 - 20000 Гц",
            )
        },
    )

    private fun statusScreen(): TextScreen = TextScreen(
        title = "ДИАГНОСТИКА",
        subtitle = "FLIPPER HARDWARE SCAN",
        footer = "OK ОБНОВИТЬ   ◀ НАЗАД",
        refreshMs = 1000L,
        lines = { Capabilities.report(this) },
    )

    private fun aboutScreen(): TextScreen = TextScreen(
        title = "О ПРОГРАММЕ",
        lines = {
            listOf(
                "FLIPPER ZERO POCKET v1.0",
                "KOTLIN / ANDROID / MATERIAL-YELLOW",
                "ЭКРАН: 1.47 INCH LCD STYLE",
                "РАЗДЕЛЫ: SUB-GHZ, RFID, NFC, IR",
                "РАЗДЕЛЫ: iBUTTON, BADUSB, BLE, GPIO",
                "ПРИЛОЖЕНИЯ: ИГРЫ И КАЛЬКУЛЯТОР",
                "АРХИВ: СИГНАЛЫ, КАРТЫ, КЛЮЧИ, СКРИПТЫ",
                "ДОЛФИН: XP, УРОВНИ, УВЕДОМЛЕНИЯ",
                "ЖЕЛЕЗО: ИК, NFC, BLE, WI-FI, USB, FLASH",
                "ЧАСТЬ ФУНКЦИЙ ТРЕБУЕТ ВНЕШНИЙ МОДУЛЬ",
            )
        },
    )
}
