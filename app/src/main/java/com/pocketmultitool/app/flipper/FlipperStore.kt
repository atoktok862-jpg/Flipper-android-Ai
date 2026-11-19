package com.pocketmultitool.app.flipper

import android.content.Context
import com.pocketmultitool.app.core.JsonStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlipperStore(context: Context) : JsonStore(context, "flipper_zero_store") {
    private val stamp = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    fun signals(): List<FlipperSignal> = readArray("subghz").mapNotNull { item ->
        val obj = item as? JSONObject ?: return@mapNotNull null
        FlipperSignal(
            obj.optString("name", ""),
            obj.optString("frequency", ""),
            obj.optString("protocol", ""),
            obj.optString("data", ""),
            obj.optString("time", ""),
        )
    }

    fun saveSignal(name: String, frequency: String, protocol: String, data: String) {
        val obj = JSONObject()
            .put("name", name)
            .put("frequency", frequency)
            .put("protocol", protocol)
            .put("data", data)
            .put("time", stamp.format(Date()))
        replace("subghz", name, "name", obj)
        addXp(5)
    }

    fun removeSignal(name: String) = removeAt("subghz", name, "name")

    fun rfidCards(): List<FlipperRfidCard> = readArray("rfid").mapNotNull { item ->
        val obj = item as? JSONObject ?: return@mapNotNull null
        FlipperRfidCard(
            obj.optString("name", ""),
            obj.optString("uid", ""),
            obj.optString("type", ""),
            obj.optString("time", ""),
        )
    }

    fun saveRfidCard(name: String, uid: String, type: String) {
        val obj = JSONObject()
            .put("name", name)
            .put("uid", uid)
            .put("type", type)
            .put("time", stamp.format(Date()))
        replace("rfid", name, "name", obj)
        addXp(5)
    }

    fun removeRfidCard(name: String) = removeAt("rfid", name, "name")

    fun ibuttons(): List<FlipperIButton> = readArray("ibutton").mapNotNull { item ->
        val obj = item as? JSONObject ?: return@mapNotNull null
        FlipperIButton(
            obj.optString("name", ""),
            obj.optString("rom", ""),
            obj.optString("time", ""),
        )
    }

    fun saveIButton(name: String, rom: String) {
        val obj = JSONObject()
            .put("name", name)
            .put("rom", rom)
            .put("time", stamp.format(Date()))
        replace("ibutton", name, "name", obj)
        addXp(5)
    }

    fun removeIButton(name: String) = removeAt("ibutton", name, "name")

    fun scripts(): List<FlipperScript> = readArray("badusb").mapNotNull { item ->
        val obj = item as? JSONObject ?: return@mapNotNull null
        FlipperScript(
            obj.optString("name", ""),
            obj.optString("body", ""),
            obj.optString("time", ""),
        )
    }

    fun saveScript(name: String, body: String) {
        val obj = JSONObject()
            .put("name", name)
            .put("body", body)
            .put("time", stamp.format(Date()))
        replace("badusb", name, "name", obj)
        addXp(5)
    }

    fun removeScript(name: String) = removeAt("badusb", name, "name")

    fun xp(): Int = prefs.getInt("xp", 0)

    fun level(): Int = 1 + xp() / 100

    fun xpToNext(): Int = 100 - xp() % 100

    fun addXp(value: Int) {
        val next = xp() + value
        prefs.edit().putInt("xp", next).apply()
        if (next / 100 > (next - value) / 100) {
            addNotification("ДОЛФИН: УРОВЕНЬ ${1 + next / 100}")
        }
    }

    fun resetProgress() {
        prefs.edit().putInt("xp", 0).apply()
        setSetting("best_jump", "0")
    }

    fun setting(key: String, default: String): String = prefs.getString(key, default) ?: default

    fun setSetting(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun flag(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setFlag(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun notifications(): List<String> = readArray("notifications").map { it.toString().trim('"') }

    fun addNotification(text: String) {
        val array = readArray("notifications")
        array.put(text)
        writeArray("notifications", array)
    }

    fun clearNotifications() = writeArray("notifications", org.json.JSONArray())

    fun totalRecords(): Int = signals().size + rfidCards().size + ibuttons().size + scripts().size

    fun clearAll() {
        listOf("subghz", "rfid", "ibutton", "badusb", "notifications").forEach { writeArray(it, org.json.JSONArray()) }
    }
}
