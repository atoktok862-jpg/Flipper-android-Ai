package com.pocketmultitool.app.hardware

fun ByteArray.toHex(separator: String = ""): String = joinToString(separator) { "%02X".format(it.toInt() and 0xFF) }

fun Int.toHex32(): String = "%08X".format(this)

fun Long.toHex32(): String = "%08X".format(this and 0xFFFFFFFFL)
