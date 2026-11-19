package com.pocketmultitool.app.hardware.ir

data class IrKeySet(
    val power: Long,
    val volumeUp: Long,
    val volumeDown: Long,
    val channelUp: Long,
    val channelDown: Long,
    val mute: Long,
)

object IrDatabase {
    val brands: LinkedHashMap<String, IrKeySet> = linkedMapOf(
        "SAMSUNG" to IrKeySet(0xE0E040BF, 0xE0E048B7, 0xE0E008B7, 0xE0E006F9, 0xE0E08679, 0xE0E0F00F),
        "LG" to IrKeySet(0x20DF10EF, 0x20DF4EB1, 0x20DF8E71, 0x20DF00FF, 0x20DF807F, 0x20DF906F),
        "XIAOMI" to IrKeySet(0xE0E040BF, 0xE0E0E01F, 0xE0E0D02F, 0xE0E006F9, 0xE0E08679, 0xE0E0F00F),
        "SONY" to IrKeySet(0xA90C0BF4, 0xA90C02FD, 0xA90C22DD, 0xA90C48B7, 0xA90CC837, 0xA90C18E7),
        "PHILIPS" to IrKeySet(0x20DF10EF, 0x20DF4EB1, 0x20DF8E71, 0x20DF00FF, 0x20DF807F, 0x20DF7887),
        "BBK" to IrKeySet(0x61D66019, 0x61D620DF, 0x61D640BF, 0x61D600FF, 0x61D6807F, 0x61D610EF),
    )

    fun necPattern(code: Long): IntArray {
        val value = code and 0xFFFFFFFFL
        val pattern = ArrayList<Int>(67)
        pattern.add(9000)
        pattern.add(4500)
        for (bit in 0 until 32) {
            pattern.add(560)
            pattern.add(if ((value shr bit) and 1L == 1L) 1690 else 560)
        }
        pattern.add(560)
        return pattern.toIntArray()
    }
}
