package com.pocketmultitool.app.flipper

object FlipperTools {
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
