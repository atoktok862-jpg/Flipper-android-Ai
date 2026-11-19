package com.pocketmultitool.app.hardware.scanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.abs

data class BleDevice(val name: String, val address: String, val rssi: Int)
data class WifiNet(val ssid: String, val frequency: Int, val channel: Int, val level: Int)

class RadioScanner(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        (app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val wifiManager: WifiManager? = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var leScanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var bleRegistered = false
    private var wifiRegistered = false

    val bleResults = ArrayList<BleDevice>()
    val wifiResults = ArrayList<WifiNet>()
    var scanningBle = false
        private set
    var scanningWifi = false
        private set
    var lastError = ""

    val bluetoothAvailable: Boolean get() = bluetoothAdapter != null
    val bluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true
    val wifiAvailable: Boolean get() = wifiManager != null

    @SuppressLint("MissingPermission")
    fun startBle(): String {
        if (bluetoothAdapter == null) return "ОШИБКА: Bluetooth отсутствует"
        if (!bluetoothAdapter.isEnabled) return "ОШИБКА: Bluetooth выключен"
        if (scanningBle) return "СКАНИРОВАНИЕ УЖЕ ИДЁТ"
        return try {
            leScanner = bluetoothAdapter.bluetoothLeScanner
            if (leScanner == null) return "ОШИБКА: BLE-сканер недоступен"
            bleResults.clear()
            leScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
            scanningBle = true
            bleRegistered = true
            handler.postDelayed({ stopBle() }, 20000L)
            "BLE-СКАНИРОВАНИЕ СТАРТОВАЛО"
        } catch (e: SecurityException) {
            "ОШИБКА: нет разрешения на BLE (${e.message ?: "security"})"
        } catch (e: Exception) {
            "ОШИБКА: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBle() {
        if (!scanningBle) return
        try {
            leScanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanningBle = false
        bleRegistered = false
    }

    @SuppressLint("MissingPermission")
    fun startWifi(): String {
        if (wifiManager == null) return "ОШИБКА: Wi-Fi отсутствует"
        if (scanningWifi) return "СКАНИРОВАНИЕ УЖЕ ИДЁТ"
        return try {
            if (!wifiRegistered) {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    app.registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    app.registerReceiver(wifiReceiver, filter)
                }
                wifiRegistered = true
            }
            wifiResults.clear()
            val started = wifiManager.startScan()
            scanningWifi = true
            if (started) "WI-FI-СКАНИРОВАНИЕ СТАРТОВАЛО" else "СКАН ЗАПУЩЕН, ОЖИДАНИЕ РЕЗУЛЬТАТОВ"
        } catch (e: SecurityException) {
            "ОШИБКА: нужно разрешение ACCESS_FINE_LOCATION"
        } catch (e: Exception) {
            "ОШИБКА: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun stopWifi() {
        if (wifiRegistered) {
            try { app.unregisterReceiver(wifiReceiver) } catch (_: Exception) {}
            wifiRegistered = false
        }
        scanningWifi = false
    }

    fun stopAll() {
        stopBle()
        stopWifi()
    }

    @SuppressLint("MissingPermission")
    private fun refreshWifi() {
        val manager = wifiManager ?: return
        try {
            wifiResults.clear()
            manager.scanResults.forEach { result ->
                val channel = when {
                    result.frequency in 2412..2472 -> (result.frequency - 2407) / 5
                    result.frequency == 2484 -> 14
                    result.frequency in 5000..5900 -> (result.frequency - 5000) / 5
                    else -> 0
                }
                wifiResults.add(WifiNet(result.SSID ?: "<скрытая>", result.frequency, channel, result.level))
            }
            wifiResults.sortByDescending { it.level }
            lastError = ""
        } catch (e: SecurityException) {
            lastError = "нет разрешения на геолокацию"
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = try { device.name ?: "<без имени>" } catch (_: Exception) { "<скрыто>" }
            val address = try { device.address ?: "<неизвестно>" } catch (_: Exception) { "<неизвестно>" }
            val found = bleResults.firstOrNull { it.address == address }
            if (found == null) {
                bleResults.add(BleDevice(name, address, result.rssi))
            } else {
                bleResults[bleResults.indexOf(found)] = BleDevice(name, address, result.rssi)
            }
            bleResults.sortByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            lastError = "BLE ошибка $errorCode"
            scanningBle = false
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) refreshWifi()
        }
    }

    fun bandLabel(frequency: Int): String = if (frequency < 3000) "2.4 ГГц" else "5 ГГц"

    fun signalBars(level: Int): String = when {
        level >= -50 -> "||||"
        level >= -65 -> "|||"
        level >= -78 -> "||"
        abs(level) == Int.MAX_VALUE -> "?"
        else -> "|"
    }
}
