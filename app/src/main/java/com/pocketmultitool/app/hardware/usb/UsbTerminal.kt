package com.pocketmultitool.app.hardware.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UsbTerminal(context: Context, private val onLog: (String) -> Unit) {
    private val app = context.applicationContext
    private val manager: UsbManager? = app.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val permissionAction = "${app.packageName}.USB_PERMISSION"
    private var receiverRegistered = false

    var selectedDevice: UsbDevice? = null
    var baud: Int = 115200

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    val available: Boolean
        get() = manager != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    val isOpen: Boolean get() = connection != null

    init {
        val filter = IntentFilter(permissionAction)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            app.registerReceiver(permissionReceiver, filter)
        }
        receiverRegistered = true
    }

    fun devices(): List<UsbDevice> = manager?.deviceList?.values?.toList() ?: emptyList()

    fun describe(device: UsbDevice): String =
        "${device.deviceName} VID:${"%04X".format(device.vendorId)} PID:${"%04X".format(device.productId)} IF:${device.interfaceCount}"

    fun select(device: UsbDevice) {
        selectedDevice = device
        onLog("ВЫБРАНО: ${describe(device)}")
    }

    fun requestPermission(device: UsbDevice) {
        if (manager == null) {
            onLog("ОШИБКА: USB host недоступен")
            return
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        val intent = Intent(permissionAction).setPackage(app.packageName)
        val pending = PendingIntent.getBroadcast(app, 41, intent, flags)
        manager.requestPermission(device, pending)
        onLog("ЗАПРОС РАЗРЕШЕНИЯ USB...")
    }

    fun open(): String {
        val device = selectedDevice ?: devices().firstOrNull() ?: return "ОШИБКА: USB-устройство не найдено"
        selectedDevice = device
        if (manager == null) return "ОШИБКА: USB host недоступен"
        if (!manager.hasPermission(device)) {
            requestPermission(device)
            return "ОЖИДАНИЕ РАЗРЕШЕНИЯ USB"
        }
        close()
        val opened = manager.openDevice(device) ?: return "ОШИБКА: не удалось открыть устройство"
        val intf = findInterface(device) ?: run {
            opened.close()
            return "ОШИБКА: нет интерфейса с bulk-конечными точками"
        }
        if (!opened.claimInterface(intf, true)) {
            opened.close()
            return "ОШИБКА: не удалось захватить интерфейс"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !opened.setInterface(intf)) {
            onLog("ВНИМАНИЕ: не удалось выбрать alternate interface")
        }
        val bulkEndpoints = (0 until intf.endpointCount).map { intf.getEndpoint(it) }
            .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val input = bulkEndpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        val output = bulkEndpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (input == null || output == null) {
            opened.releaseInterface(intf)
            opened.close()
            return "ОШИБКА: нет IN/OUT bulk-конечных точек"
        }
        connection = opened
        usbInterface = intf
        endpointIn = input
        endpointOut = output

        if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
            val result = setLineCoding(opened, intf.id, baud)
            onLog("LINE CODING: $result")
        } else {
            onLog("ВНИМАНИЕ: не CDC-устройство, скорость задаётся прошивкой")
        }
        return "ПОРТ ОТКРЫТ: ${baud} бод, IN=${input.endpointNumber} OUT=${output.endpointNumber}"
    }

    fun send(text: String): String {
        val conn = connection ?: return "ОШИБКА: порт закрыт"
        val endpoint = endpointOut ?: return "ОШИБКА: нет OUT-конечной точки"
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty()) return "ОШИБКА: пустая строка"
        return try {
            val written = conn.bulkTransfer(endpoint, bytes, bytes.size, 1000)
            if (written < 0) "ОШИБКА записи: $written" else "ОТПРАВЛЕНО: $written байт"
        } catch (e: Exception) {
            "ОШИБКА записи: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun receive(): String {
        val conn = connection ?: return "ОШИБКА: порт закрыт"
        val endpoint = endpointIn ?: return "ОШИБКА: нет IN-конечной точки"
        return try {
            val buffer = ByteArray(4096)
            val length = conn.bulkTransfer(endpoint, buffer, buffer.size, 500)
            if (length <= 0) "ПРИНЯТО: 0 байт" else "ПРИНЯТО:\n${String(buffer, 0, length, Charsets.UTF_8)}"
        } catch (e: Exception) {
            "ОШИБКА чтения: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun close(): String {
        val conn = connection ?: return "ПОРТ УЖЕ ЗАКРЫТ"
        usbInterface?.let { try { conn.releaseInterface(it) } catch (_: Exception) {} }
        conn.close()
        connection = null
        usbInterface = null
        endpointIn = null
        endpointOut = null
        return "ПОРТ ЗАКРЫТ"
    }

    fun destroy() {
        close()
        if (receiverRegistered) {
            try { app.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
    }

    private fun findInterface(device: UsbDevice): UsbInterface? {
        val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
        return interfaces.firstOrNull {
            (0 until it.endpointCount).any { i -> it.getEndpoint(i).type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        }
    }

    private fun setLineCoding(conn: UsbDeviceConnection, interfaceId: Int, speed: Int): String {
        val payload = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(speed)
            .put(0.toByte())
            .put(0.toByte())
            .put(8.toByte())
            .array()
        val requestType = UsbConstants.USB_TYPE_CLASS or UsbConstants.USB_DIR_OUT or UsbConstants.USB_RECIP_INTERFACE
        val written = conn.controlTransfer(requestType, 0x20, 0, interfaceId, payload, payload.size, 1000)
        return if (written >= 0) "OK ($speed)" else "НЕ ПОДДЕРЖИВАЕТСЯ ($written)"
    }

    private val permissionReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != permissionAction) return
            val device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (device == null) {
                onLog("ОШИБКА: устройство не получено")
                return
            }
            if (granted) {
                selectedDevice = device
                onLog("РАЗРЕШЕНИЕ ПОЛУЧЕНО: ${describe(device)}")
                onLog(open())
            } else {
                onLog("ОТКАЗ В РАЗРЕШЕНИИ USB")
            }
        }
    }
}
