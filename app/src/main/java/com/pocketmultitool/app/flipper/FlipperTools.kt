package com.pocketmultitool.app.flipper

data class FlipperSignal(
    val name: String,
    val frequency: String,
    val protocol: String,
    val data: String,
    val time: String,
)

data class FlipperRfidCard(
    val name: String,
    val uid: String,
    val type: String,
    val time: String,
)

data class FlipperIButton(
    val name: String,
    val rom: String,
    val time: String,
)

data class FlipperScript(
    val name: String,
    val body: String,
    val time: String,
)

object FlipperTools {
    fun subGhzPresets(): List<String> = listOf(
        "433.920 МГц  ASK/OOK  гаражные пульты",
        "433.075 МГц  ASK/OOK  датчики",
        "315.000 МГц  ASK/OOK  автосигнализации",
        "868.350 МГц  FSK      умные дома",
        "915.000 МГц  FSK      ISM-устройства",
    )

    fun em4100(uidHex: String): List<String> {
        val clean = uidHex.uppercase().replace(" ", "").replace(":", "").replace("-", "")
        if (clean.length != 10 || clean.any { it !in '0'..'9' && it !in 'A'..'F' }) {
            return listOf("ОШИБКА: НУЖНО 10 HEX-ЦИФР", "ПРИМЕР: 041A2B3C4D")
        }
        val nibbles = clean.map { it.toString().toInt(16) }
        val header = "1".repeat(9)
        val rows = buildString {
            nibbles.forEach { nibble ->
                val bits = nibble.toString(2).padStart(4, '0')
                val parity = if (bits.count { it == '1' } % 2 == 1) "1" else "0"
                append(bits).append(parity)
            }
        }
        val columns = (0 until 4).joinToString("") { column ->
            val shift = 3 - column
            val ones = nibbles.count { (it shr shift) and 1 == 1 }
            if (ones % 2 == 1) "1" else "0"
        }
        val frame = header + rows + columns + "0"
        val hex = frame.chunked(4).joinToString(" ") { chunk ->
            chunk.toInt(2).toString(16).uppercase().padStart(1, '0')
        }
        return listOf(
            "UID: $clean",
            "КАДР 1: ${frame.substring(0, 32)}",
            "КАДР 2: ${frame.substring(32)}",
            "HEX: $hex",
            "ДЛИНА: ${frame.length} БИТ",
        )
    }

    fun wiegand26(facility: Int, card: Int): List<String> {
        if (facility !in 0..0xFF || card !in 0..0xFFFF) {
            return listOf("ОШИБКА: ОБЪЕКТ 0-255, КАРТА 0-65535")
        }
        val bits = (((facility and 0xFF) shl 16) or (card and 0xFFFF)).toString(2).padStart(24, '0')
        val high = bits.substring(0, 12)
        val low = bits.substring(12)
        val even = if (high.count { it == '1' } % 2 == 0) "0" else "1"
        val odd = if (low.count { it == '1' } % 2 == 1) "0" else "1"
        return listOf("ОБЪЕКТ: $facility", "КАРТА: $card", "КАДР: $even$bits$odd", "ФОРМАТ: WIEGAND-26")
    }

    fun ds1990a(serialHex: String): List<String> {
        val clean = serialHex.uppercase().replace(" ", "").replace(":", "").replace("-", "")
        if (clean.length != 12 || clean.any { it !in '0'..'9' && it !in 'A'..'F' }) {
            return listOf("ОШИБКА: НУЖНО 12 HEX-ЦИФР", "ПРИМЕР: 00000A1B2C3D")
        }
        val serial = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val rom = byteArrayOf(0x01) + serial
        val crc = crc8Maxim(rom)
        val full = rom + crc
        return listOf(
            "СЕРИЯ: $clean",
            "СЕМЕЙСТВО: 01 (DS1990A)",
            "ROM: ${full.toHexCompact()}",
            "CRC8: ${"%02X".format(crc.toInt() and 0xFF)}",
        )
    }

    fun validateDucky(script: String): List<String> {
        val known = setOf(
            "REM", "STRING", "DELAY", "ENTER", "TAB", "ESC", "SPACE", "BACKSPACE",
            "CTRL", "ALT", "SHIFT", "GUI", "UP", "DOWN", "LEFT", "RIGHT", "HOME",
            "END", "PAGEUP", "PAGEDOWN", "INSERT", "DELETE", "REPEAT", "HOLD",
        )
        val errors = ArrayList<String>()
        var commands = 0
        script.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("REM", true)) return@forEachIndexed
            commands++
            val command = line.substringBefore(' ').uppercase()
            val argument = line.substringAfter(' ', "").trim()
            when {
                command !in known -> errors.add("${index + 1}: НЕИЗВЕСТНАЯ КОМАНДА $command")
                command == "DELAY" && argument.toIntOrNull() == null -> errors.add("${index + 1}: DELAY НУЖНО ЧИСЛО")
                command == "REPEAT" && argument.toIntOrNull() == null -> errors.add("${index + 1}: REPEAT НУЖНО ЧИСЛО")
                command == "STRING" && argument.isEmpty() -> errors.add("${index + 1}: ПУСТАЯ СТРОКА")
            }
        }
        return if (errors.isEmpty()) {
            listOf("ПРОВЕРКА: OK", "КОМАНД: $commands", "СТРОК: ${script.lines().size}", "ЗАПУСК: НУЖЕН USB HID")
        } else {
            listOf("ПРОВЕРКА: ОШИБКИ") + errors.take(8)
        }
    }

    fun convertNumber(input: String): List<String> {
        val value = when {
            input.startsWith("0X", true) -> input.substring(2).toLongOrNull(16)
            input.startsWith("0B", true) -> input.substring(2).toLongOrNull(2)
            input.startsWith("0O", true) -> input.substring(2).toLongOrNull(8)
            else -> input.toLongOrNull()
        } ?: return listOf("ОШИБКА: НЕ ЧИСЛО", "ПРИМЕРЫ: 255, 0xFF, 0b1111")
        return listOf(
            "DEC: $value",
            "HEX: 0x${value.toString(16).uppercase()}",
            "BIN: 0b${value.toString(2)}",
            "OCT: 0o${value.toString(8)}",
        )
    }

    fun calculate(expression: String): List<String> = try {
        val result = ExpressionParser(expression).parse()
        val text = if (result % 1.0 == 0.0) result.toLong().toString() else result.toString()
        listOf("ВЫРАЖЕНИЕ: $expression", "РЕЗУЛЬТАТ: $text")
    } catch (e: Exception) {
        listOf("ОШИБКА: ${e.message ?: "неверное выражение"}", "ПРИМЕР: (12+8)*3/4")
    }

    fun gpioReference(): List<String> = listOf(
        "GPIO  ПИНЫ ESP32/FLIPPER",
        "GND   земля, общий контакт",
        "3V3   питание 3.3В",
        "5V    питание 5В (только OTG)",
        "TX    UART передача",
        "RX    UART приём",
        "SDA   I2C данные",
        "SCL   I2C такты",
        "MOSI  SPI данные",
        "MISO  SPI данные",
        "SCK   SPI такты",
        "CS    SPI выбор",
    )

    private fun crc8Maxim(bytes: ByteArray): Byte {
        var crc = 0
        bytes.forEach { byte ->
            var value = (byte.toInt() and 0xFF) xor crc
            repeat(8) {
                value = if (value and 1 != 0) (value shr 1) xor 0x8C else value shr 1
            }
            crc = value and 0xFF
        }
        return crc.toByte()
    }

    private fun ByteArray.toHexCompact(): String = joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private class ExpressionParser(private val source: String) {
        private var position = 0

        fun parse(): Double {
            val result = expression()
            skipSpaces()
            if (position != source.length) throw IllegalArgumentException("лишние символы")
            return result
        }

        private fun skipSpaces() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                when {
                    position < source.length && source[position] == '+' -> {
                        position++
                        value += term()
                    }
                    position < source.length && source[position] == '-' -> {
                        position++
                        value -= term()
                    }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                when {
                    position < source.length && source[position] == '*' -> {
                        position++
                        value *= factor()
                    }
                    position < source.length && source[position] == '/' -> {
                        position++
                        val divisor = factor()
                        if (divisor == 0.0) throw IllegalArgumentException("деление на ноль")
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            skipSpaces()
            if (position < source.length && source[position] == '(') {
                position++
                val value = expression()
                skipSpaces()
                if (position >= source.length || source[position] != ')') throw IllegalArgumentException("нет скобки")
                position++
                return value
            }
            val start = position
            if (position < source.length && (source[position] == '-' || source[position] == '+')) position++
            while (position < source.length && (source[position].isDigit() || source[position] == '.')) position++
            if (start == position) throw IllegalArgumentException("ожидалось число")
            return source.substring(start, position).toDoubleOrNull()
                ?: throw IllegalArgumentException("неверное число")
        }
    }
}
