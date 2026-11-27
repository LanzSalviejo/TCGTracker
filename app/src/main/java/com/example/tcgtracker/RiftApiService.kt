package com.example.tcgtracker

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class RiftCard(
    val id: String,
    val name: String,
    val publicCode: String?,
    val defaultPrintingId: String?,
    val imageId: String?,
    val embeddedUsdPrice: Double? = null
) {
    fun getImageUrl(): String {
        if (imageId.isNullOrEmpty()) return ""
        return "https://openrift.app/media/cards/${imageId}-400w.webp"
    }
}

/**
 * Retrofit client and JSON parser for the OpenRift Riftbound card and pricing API.
 */
interface RiftApiService {

    @GET("api/v1/search")
    suspend fun searchCards(
        @Query("q") query: String,
        @Query("unique") unique: String = "prints"
    ): JsonElement

    @GET("api/v1/prices/{printingId}")
    suspend fun getCardPriceRaw(@Path("printingId") printingId: String): JsonElement

    companion object {
        private const val BASE_URL = "https://openrift.app/"
        private val CODE_PATTERN = Regex("""(\d+)(\*?)\s*/\s*(\d+)""")
        private val PRICE_KEYS = listOf("market", "price", "usd", "normal", "foil", "low", "value")

        // Single reusable Retrofit instance (avoids recreating OkHttpClient on every search)
        val api: RiftApiService by lazy {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "TCGTrackerSchoolProject/1.0")
                        .build()
                    chain.proceed(request)
                }
                .build()

            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RiftApiService::class.java)
        }

        // Identifies whether a card code is Standard, Overnumbered (number > set total)
        fun getVersionLabel(publicCode: String): String {
            val match = CODE_PATTERN.find(publicCode) ?: return "Standard"
            val cardNumber = match.groupValues[1].toIntOrNull() ?: 0
            val hasStar = match.groupValues[2] == "*"
            val setTotal = match.groupValues[3].toIntOrNull() ?: Int.MAX_VALUE

            return when {
                hasStar -> "Signature"
                cardNumber > setTotal -> "Overnumbered"
                else -> "Standard"
            }
        }

        /**
         * Reads OpenRift's "printings" map into a clean, deduplicated list of RiftCard items.
         */
        fun parseSearchResponse(root: JsonElement): List<RiftCard> {
            if (!root.isJsonObject) return emptyList()
            val rootObj = root.asJsonObject

            val printingsObj = rootObj.getObjectOrNull("printings") ?: return emptyList()
            val cardsObj = rootObj.getObjectOrNull("cards")

            val parsedCards = printingsObj.entrySet().mapNotNull { (printingId, element) ->
                if (element.isJsonObject) {
                    parseSinglePrinting(printingId, element.asJsonObject, cardsObj)
                } else {
                    null
                }
            }

            return deduplicateCards(parsedCards)
        }

        private fun parseSinglePrinting(
            printingId: String,
            printingObj: JsonObject,
            cardsObj: JsonObject?
        ): RiftCard {
            // Grab the first image ID from the printing's "images" array if available
            val imageId = printingObj.get("images")
                ?.takeIf { it.isJsonArray && it.asJsonArray.size() > 0 }
                ?.asJsonArray?.get(0)
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject?.getStringOrNull("imageId")

            // Prefer "printedName"; fall back to the matching entry in "cards"
            val cardId = printingObj.getStringOrNull("cardId")
            val fallbackName = cardId?.let { cardsObj?.getObjectOrNull(it)?.getStringOrNull("name") }
            val name = printingObj.getStringOrNull("printedName") ?: fallbackName ?: "Unknown Card"

            val publicCode = printingObj.getStringOrNull("publicCode")
                ?: printingObj.getStringOrNull("shortCode")
                ?: "N/A"

            return RiftCard(
                id = printingId,
                name = name,
                publicCode = publicCode,
                defaultPrintingId = printingId,
                imageId = imageId
            )
        }

        /**
         * Removes duplicate printings that share the same name and code,
         * keeping whichever copy actually has a card image.
         */
        private fun deduplicateCards(cards: List<RiftCard>): List<RiftCard> {
            val uniqueMap = linkedMapOf<String, RiftCard>()
            for (card in cards) {
                val key = "${card.name}|${card.publicCode}".lowercase()
                val existing = uniqueMap[key]

                if (existing == null || (existing.imageId.isNullOrEmpty() && !card.imageId.isNullOrEmpty())) {
                    uniqueMap[key] = card
                }
            }
            return uniqueMap.values.toList()
        }

        /**
         * Extracts the TCGplayer market price from /api/v1/prices/{printingId}.
         * OpenRift stores prices in cents, so the result is divided by 100.0 to get USD.
         */
        fun extractUsdPrice(element: JsonElement?): Double? {
            val priceInCents = findTcgPriceInCents(element) ?: return null
            return priceInCents / 100.0
        }

        private fun findTcgPriceInCents(element: JsonElement?): Double? {
            if (element == null || !element.isJsonObject) return null
            val obj = element.asJsonObject

            // 1. If this object has a "tcgplayer" block, read its price value directly
            if (obj.has("tcgplayer")) {
                readFirstPriceNumber(obj.get("tcgplayer"))?.let { return it }
            }

            // 2. Otherwise check nested wrapper objects (skipping "links" and "history")
            for ((key, value) in obj.entrySet()) {
                if (key != "links" && key != "history" && value.isJsonObject) {
                    findTcgPriceInCents(value)?.let { return it }
                }
            }
            return null
        }

        private fun readFirstPriceNumber(element: JsonElement?): Double? {
            if (element == null || element.isJsonNull) return null
            if (element.isJsonPrimitive) {
                return element.asString.toDoubleOrNull()
            }
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                for (key in PRICE_KEYS) {
                    obj.getDoubleOrNull(key)?.let { return it }
                }
                for ((_, value) in obj.entrySet()) {
                    readFirstPriceNumber(value)?.let { return it }
                }
            }
            return null
        }

        private fun JsonObject.getObjectOrNull(key: String): JsonObject? {
            return if (has(key) && get(key).isJsonObject) getAsJsonObject(key) else null
        }

        private fun JsonObject.getStringOrNull(key: String): String? {
            return if (has(key) && get(key).isJsonPrimitive) get(key).asString else null
        }

        private fun JsonObject.getDoubleOrNull(key: String): Double? {
            return if (has(key) && get(key).isJsonPrimitive) get(key).asString.toDoubleOrNull() else null
        }
    }
}
