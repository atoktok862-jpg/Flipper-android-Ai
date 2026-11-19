package com.pocketmultitool.app.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

abstract class JsonStore(context: Context, name: String) {
    protected val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    protected fun readArray(key: String): JSONArray {
        val raw = prefs.getString(key, "[]") ?: "[]"
        return try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    protected fun writeArray(key: String, array: JSONArray) {
        prefs.edit().putString(key, array.toString()).apply()
    }

    protected fun removeAt(key: String, uid: String, field: String) {
        val source = readArray(key)
        val kept = JSONArray()
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            if (item.optString(field, "") != uid) kept.put(item)
        }
        writeArray(key, kept)
    }

    protected fun replace(key: String, uid: String, field: String, value: JSONObject) {
        removeAt(key, uid, field)
        val source = readArray(key)
        source.put(value)
        writeArray(key, source)
    }

    protected fun find(key: String, uid: String, field: String): JSONObject? {
        val source = readArray(key)
        for (i in 0 until source.length()) {
            val item = source.optJSONObject(i) ?: continue
            if (item.optString(field, "") == uid) return item
        }
        return null
    }
}
