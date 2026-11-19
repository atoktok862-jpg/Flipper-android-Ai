package com.pocketmultitool.app.hardware.ir

import android.content.Context
import android.hardware.ConsumerIrManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class IrRemote(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val available: Boolean
        get() = try {
            manager?.hasIrEmitter() == true
        } catch (_: Exception) {
            false
        }

    val range: String
        get() = try {
            val ranges = manager?.carrierFrequencies
            if (ranges.isNullOrEmpty()) "неизвестно"
            else ranges.joinToString(", ") { "${it.minFrequency}-${it.maxFrequency}" } + " Гц"
        } catch (_: Exception) {
            "неизвестно"
        }

    fun carrier(requested: Int): Int = try {
        val ranges = manager?.carrierFrequencies
        if (ranges.isNullOrEmpty()) {
            requested
        } else {
            val exact = ranges.firstOrNull { requested in it.minFrequency..it.maxFrequency }
            if (exact != null) {
                requested
            } else {
                val closest = ranges.minByOrNull {
                    min(abs(requested - it.minFrequency), abs(requested - it.maxFrequency))
                } ?: return requested
                min(max(requested, closest.minFrequency), closest.maxFrequency)
            }
        }
    } catch (_: Exception) {
        requested
    }

    fun sendNec(code: Long): String = transmit(carrier(38000), IrDatabase.necPattern(code))

    fun sendRaw(requestedCarrier: Int, pattern: IntArray): String {
        if (pattern.isEmpty()) return "ОШИБКА: пустая последовательность"
        if (pattern.size % 2 == 0) return "ВНИМАНИЕ: чётное число импульсов"
        return transmit(carrier(requestedCarrier), pattern)
    }

    fun parsePattern(input: String): IntArray {
        val parts = input.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
        return parts.map { it.toIntOrNull() ?: throw IllegalArgumentException("неверное число: $it") }.toIntArray()
    }

    private fun transmit(frequency: Int, pattern: IntArray): String = try {
        if (manager == null) {
            "ОШИБКА: ИК-передатчик отсутствует"
        } else if (!manager.hasIrEmitter()) {
            "ОШИБКА: ИК-передатчик недоступен"
        } else {
            manager.transmit(frequency, pattern)
            "OK: ${pattern.size} импульсов @ $frequency Гц"
        }
    } catch (e: Exception) {
        "ОШИБКА: ${e.message ?: e.javaClass.simpleName}"
    }
}
