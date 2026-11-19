package com.pocketmultitool.app.hardware.nfc

import android.content.Context
import com.pocketmultitool.app.core.JsonStore
import org.json.JSONObject

data class TagRecord(
    val uid: String,
    val techs: String,
    val ndef: String,
    val time: String,
    val name: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uid", uid)
        put("techs", techs)
        put("ndef", ndef)
        put("time", time)
        put("name", name)
    }

    companion object {
        fun fromJson(json: JSONObject): TagRecord = TagRecord(
            uid = json.optString("uid", ""),
            techs = json.optString("techs", ""),
            ndef = json.optString("ndef", ""),
            time = json.optString("time", ""),
            name = json.optString("name", ""),
        )
    }
}

class TagStorage(context: Context) : JsonStore(context, "nfc_tags") {
    fun all(): List<TagRecord> {
        val source = readArray("tags")
        val result = ArrayList<TagRecord>(source.length())
        for (i in 0 until source.length()) {
            source.optJSONObject(i)?.let { result.add(TagRecord.fromJson(it)) }
        }
        return result
    }

    fun save(record: TagRecord) {
        replace("tags", record.uid, "uid", record.toJson())
    }

    fun remove(uid: String) = removeAt("tags", uid, "uid")

    fun clear() = writeArray("tags", org.json.JSONArray())
}
