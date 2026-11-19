package com.pocketmultitool.app.hardware.cards

import android.content.Context
import com.pocketmultitool.app.core.JsonStore
import org.json.JSONObject

data class LoyaltyCard(
    val name: String,
    val number: String,
    val format: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("number", number)
        put("format", format)
    }

    companion object {
        fun fromJson(json: JSONObject) = LoyaltyCard(
            name = json.optString("name", ""),
            number = json.optString("number", ""),
            format = json.optString("format", "QR"),
        )
    }
}

class CardStorage(context: Context) : JsonStore(context, "loyalty_cards") {
    fun all(): List<LoyaltyCard> {
        val source = readArray("cards")
        val result = ArrayList<LoyaltyCard>(source.length())
        for (i in 0 until source.length()) {
            source.optJSONObject(i)?.let { result.add(LoyaltyCard.fromJson(it)) }
        }
        return result
    }

    fun save(card: LoyaltyCard) = replace("cards", card.number, "number", card.toJson())

    fun remove(number: String) = removeAt("cards", number, "number")

    fun clear() = writeArray("cards", org.json.JSONArray())
}
