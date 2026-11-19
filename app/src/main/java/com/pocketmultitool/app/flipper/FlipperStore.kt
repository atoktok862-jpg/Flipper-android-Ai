package com.pocketmultitool.app.flipper

import android.content.Context
import com.pocketmultitool.app.core.JsonStore

class FlipperStore(context: Context) : JsonStore(context, "flipper_zero_store") {
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

    fun notifications(): List<String> {
        val array = readArray("notifications")
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.opt(i).toString().trim('"'))
        }
        return result
    }

    fun addNotification(text: String) {
        val array = readArray("notifications")
        array.put(text)
        writeArray("notifications", array)
    }

    fun clearNotifications() = writeArray("notifications", org.json.JSONArray())
}
