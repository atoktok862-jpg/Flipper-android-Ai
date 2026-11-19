package com.pocketmultitool.app.hardware.cards

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.Writer
import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.qrcode.QRCodeWriter

object BarcodeFactory {
    const val QR = "QR"
    const val EAN13 = "EAN-13"
    const val CODE128 = "CODE-128"

    val formats = listOf(QR, EAN13, CODE128)

    fun normalizeEan13(input: String): String {
        val digits = input.filter { it.isDigit() }
        if (digits.length == 13) {
            val expected = checksum(digits.substring(0, 12))
            require(digits[12] - '0' == expected) { "неверная контрольная цифра" }
            return digits
        }
        require(digits.length == 12) { "нужно 12 или 13 цифр" }
        return digits + checksum(digits)
    }

    fun make(value: String, format: String): Bitmap {
        val text = if (format == EAN13) normalizeEan13(value) else value
        require(text.isNotEmpty()) { "пустой код" }

        val writer: Writer
        val barcodeFormat: BarcodeFormat
        val width: Int
        val height: Int
        when (format) {
            EAN13 -> {
                writer = EAN13Writer()
                barcodeFormat = BarcodeFormat.EAN_13
                width = 600
                height = 280
            }
            CODE128 -> {
                writer = Code128Writer()
                barcodeFormat = BarcodeFormat.CODE_128
                width = 720
                height = 280
            }
            else -> {
                writer = QRCodeWriter()
                barcodeFormat = BarcodeFormat.QR_CODE
                width = 560
                height = 560
            }
        }

        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 2
        val matrix = writer.encode(text, barcodeFormat, width, height, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun checksum(twelve: String): Int {
        var sum = 0
        for (i in twelve.indices) {
            val digit = twelve[i] - '0'
            sum += if (i % 2 == 0) digit else digit * 3
        }
        return (10 - sum % 10) % 10
    }
}
