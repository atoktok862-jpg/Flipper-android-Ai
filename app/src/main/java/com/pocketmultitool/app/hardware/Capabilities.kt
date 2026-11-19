package com.pocketmultitool.app.hardware

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager
import android.os.Build
import com.pocketmultitool.app.BuildConfig

object Capabilities {
    fun isRedmi12(): Boolean {
        val model = "${Build.MANUFACTURER} ${Build.MODEL} ${Build.PRODUCT}".uppercase()
        return model.contains("REDMI 12") || model.contains("22081212") || model.contains("23053RN02")
    }

    fun report(context: Context): List<String> {
        val pm = context.packageManager
        val irManager = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        val ir = try { irManager?.hasIrEmitter() == true } catch (_: Exception) { false }
        val ranges = runCatching { irManager?.carrierFrequencies }.getOrNull()
        val carrier = if (ranges.isNullOrEmpty()) "нет данных"
        else ranges.joinToString(", ") { "${it.minFrequency}-${it.maxFrequency}" } + " Гц"
        return listOf(
            "УСТРОЙСТВО: ${Build.MANUFACTURER} ${Build.MODEL}",
            "ПРОДУКТ: ${Build.PRODUCT}",
            "ANDROID: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "ВЕРСИЯ: ${BuildConfig.VERSION_NAME}",
            "REDMI 12: ${if (isRedmi12()) "ОПРЕДЕЛЁН" else "НЕ ОПРЕДЕЛЁН"}",
            "ИК-ПЕРЕДАТЧИК: ${if (ir) "ЕСТЬ" else "НЕТ"} / $carrier",
            "NFC: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_NFC))}",
            "BLUETOOTH LE: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))}",
            "WI-FI: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_WIFI))}",
            "USB HOST: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST))}",
            "КАМЕРА: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY))}",
            "ВИБРАЦИЯ: ${yes(pm.hasSystemFeature(PackageManager.FEATURE_VIBRATOR))}",
        )
    }

    private fun yes(value: Boolean) = if (value) "ЕСТЬ" else "НЕТ"
}
