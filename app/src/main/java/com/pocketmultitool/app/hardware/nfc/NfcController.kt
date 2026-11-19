package com.pocketmultitool.app.hardware.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.pocketmultitool.app.hardware.toHex
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TagInfo(
    val uid: String,
    val techs: List<String>,
    val details: List<String>,
    val time: String,
)

class NfcController(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingMessage: NdefMessage? = null

    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true
    var lastInfo: TagInfo? = null
        private set
    val waitingForWrite: Boolean get() = pendingMessage != null

    fun enableForeground() {
        val intent = Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(activity, 77, intent, flags)
        adapter?.enableForegroundDispatch(activity, pendingIntent, null, null)
    }

    fun disableForeground() {
        adapter?.disableForegroundDispatch(activity)
    }

    fun queueText(text: String) {
        pendingMessage = NdefMessage(arrayOf(NdefRecord.createTextRecord("ru", text)))
    }

    fun queueUri(uri: String) {
        pendingMessage = NdefMessage(arrayOf(NdefRecord.createUri(uri)))
    }

    fun cancelWrite() {
        pendingMessage = null
    }

    fun handleTag(tag: Tag, callback: (String) -> Unit) {
        Thread {
            val result = process(tag)
            handler.post { callback(result) }
        }.start()
    }

    private fun process(tag: Tag): String {
        val message = pendingMessage
        return if (message != null) {
            pendingMessage = null
            write(tag, message)
        } else {
            val info = read(tag)
            lastInfo = info
            buildString {
                appendLine("UID: ${info.uid}")
                appendLine("ТЕХ: ${info.techs.joinToString(", ")}")
                info.details.forEach { appendLine(it) }
            }.trimEnd()
        }
    }

    private fun read(tag: Tag): TagInfo {
        val details = ArrayList<String>()
        val uid = tag.id?.toHex(":") ?: "НЕИЗВЕСТНО"
        val techs = tag.techList.map { it.substringAfterLast('.') }

        NfcA.get(tag)?.let { nfcA ->
            try {
                details.add("NFC-A ATQA: ${nfcA.atqa.toHex()} / SAK: ${nfcA.sak.toHex4()}")
            } catch (_: Exception) {
                details.add("NFC-A: параметры недоступны")
            }
        }

        Ndef.get(tag)?.let { ndef ->
            try {
                ndef.connect()
                details.add("NDEF: ${ndef.type ?: "-"}, ${ndef.maxSize} байт")
                val message = ndef.ndefMessage
                if (message == null) {
                    details.add("NDEF: пусто")
                } else {
                    message.records.forEachIndexed { index, record ->
                        details.add("ЗАПИСЬ ${index + 1}: ${describeRecord(record)}")
                    }
                }
            } catch (e: IOException) {
                details.add("NDEF: ${e.message ?: "ошибка чтения"}")
            } finally {
                try { ndef.close() } catch (_: Exception) {}
            }
        }

        MifareClassic.get(tag)?.let { mifare ->
            try {
                mifare.connect()
                val type = when (mifare.type) {
                    MifareClassic.TYPE_CLASSIC -> "Classic"
                    MifareClassic.TYPE_PLUS -> "Plus"
                    MifareClassic.TYPE_PRO -> "Pro"
                    else -> "неизвестно"
                }
                details.add("MIFARE: $type, ${mifare.sectorCount} секторов, ${mifare.blockCount} блоков")
                val block = mifare.readBlock(0)
                details.add("БЛОК 0: ${block.toHex(" ")}")
            } catch (e: Exception) {
                details.add("MIFARE: ${e.message ?: "нет доступа"}")
            } finally {
                try { mifare.close() } catch (_: Exception) {}
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            IsoDep.get(tag)?.let { iso ->
                try {
                    iso.connect()
                    val historical = iso.historicalBytes
                    details.add("ISO-DEP: ${if (historical == null || historical.isEmpty()) "ATR пуст" else historical.toHex()}")
                } catch (e: Exception) {
                    details.add("ISO-DEP: ${e.message ?: "нет ответа"}")
                } finally {
                    try { iso.close() } catch (_: Exception) {}
                }
            }
        }

        val stamp = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())
        return TagInfo(uid, techs, details, stamp)
    }

    private fun write(tag: Tag, message: NdefMessage): String {
        NdefFormatable.get(tag)?.let { formatable ->
            try {
                formatable.connect()
                formatable.format(message)
                return "OK: метка отформатирована и записана"
            } catch (e: Exception) {
                return "ОШИБКА форматирования: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                try { formatable.close() } catch (_: Exception) {}
            }
        }

        val ndef = Ndef.get(tag) ?: return "ОШИБКА: метка не поддерживает NDEF"
        return try {
            ndef.connect()
            if (!ndef.isWritable) return "ОШИБКА: метка защищена от записи"
            val bytes = message.toByteArray().size
            if (bytes > ndef.maxSize) return "ОШИБКА: не хватает места ($bytes > ${ndef.maxSize})"
            ndef.writeNdefMessage(message)
            "OK: записано $bytes байт"
        } catch (e: Exception) {
            "ОШИБКА записи: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun describeRecord(record: NdefRecord): String {
        val payload = record.payload
        return when {
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) -> "TEKST: ${decodeText(payload)}"
            record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) -> "URI: ${record.toUri() ?: decodeUri(payload)}"
            record.tnf == NdefRecord.TNF_ABSOLUTE_URI -> "URI: ${String(payload, Charsets.UTF_8)}"
            record.tnf == NdefRecord.TNF_MIME_MEDIA -> "MIME ${record.toMimeType()}: ${String(payload, Charsets.UTF_8)}"
            else -> "RAW ${payload.toHex()}"
        }
    }

    private fun decodeText(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt() and 0xFF
        val utf16 = status and 0x80 != 0
        val length = status and 0x3F
        val start = 1 + length
        if (start > payload.size) return ""
        return String(payload, start, payload.size - start, if (utf16) Charsets.UTF_16BE else Charsets.UTF_8)
    }

    private fun decodeUri(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val prefixes = arrayOf("", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://", "sms:")
        val code = payload[0].toInt() and 0xFF
        return (prefixes.getOrNull(code) ?: "") + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }
}

private fun Short.toHex4(): String = "%04X".format(this.toInt() and 0xFFFF)
