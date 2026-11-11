package com.example.tcgtracker

import android.util.Log
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

interface RiftApiService {
    @GET("api/v1/search")
    suspend fun searchCardsRaw(@Query("q") query: String): JsonElement

    @GET("api/v1/prices/{printingId}")
    suspend fun getCardPriceRaw(@Path("printingId") printingId: String): JsonElement

    companion object {
        fun create(): RiftApiService {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "TCGTrackerSchoolProject/1.0")
                        .build()
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl("https://openrift.app/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RiftApiService::class.java)
        }

        // Parses OpenRift's "printings" dictionary and "images" array
        fun parseSearchResponse(root: JsonElement): List<RiftCard> {
            Log.d("RiftAPI", "Search JSON: $root")
            if (!root.isJsonObject) return emptyList()

            val rootObj = root.asJsonObject
            if (!rootObj.has("printings") || !rootObj.get("printings").isJsonObject) {
                return emptyList()
            }

            val printingsObj = rootObj.getAsJsonObject("printings")
            val cardsObj = if (rootObj.has("cards") && rootObj.get("cards").isJsonObject) {
                rootObj.getAsJsonObject("cards")
            } else null

            val results = mutableListOf<RiftCard>()

            // Loop through each printing UUID in the "printings" object
            for ((printingId, printingElement) in printingsObj.entrySet()) {
                if (!printingElement.isJsonObject) continue
                val pObj = printingElement.asJsonObject

                // 1. Extract imageId from images[0].imageId
                var imageId: String? = null
                if (pObj.has("images") && pObj.get("images").isJsonArray) {
                    val imagesArray = pObj.getAsJsonArray("images")
                    if (imagesArray.size() > 0 && imagesArray.get(0).isJsonObject) {
                        imageId = imagesArray.get(0).asJsonObject.getString("imageId")
                    }
                }

                // 2. Extract card name from "printedName" (or fallback to "cards" map)
                val cardId = pObj.getString("cardId")
                val fallbackName = if (cardId != null && cardsObj != null && cardsObj.has(cardId)) {
                    cardsObj.getAsJsonObject(cardId)?.getString("name")
                } else null

                val name = pObj.getString("printedName")
                    ?: fallbackName
                    ?: "Unknown Card"

                // 3. Extract card set/number code
                val publicCode = pObj.getString("publicCode")
                    ?: pObj.getString("shortCode")
                    ?: "N/A"

                results.add(
                    RiftCard(
                        id = printingId,
                        name = name,
                        publicCode = publicCode,
                        defaultPrintingId = printingId,
                        imageId = imageId
                    )
                )
            }

            return results
        }

        // Extracts TCGplayer USD price from /api/v1/prices/{printingId}
        fun extractUsdPrice(element: JsonElement?): Double? {
            if (element == null || element.isJsonNull) return null
            Log.d("RiftAPI", "Price JSON: $element")
            return findTcgPriceRecursive(element)
        }

        private fun findTcgPriceRecursive(element: JsonElement?): Double? {
            if (element == null || element.isJsonNull) return null

            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isNumber) return prim.asDouble
                if (prim.isString) return prim.asString.toDoubleOrNull()
                return null
            }

            if (element.isJsonObject) {
                val obj = element.asJsonObject

                // Check for a "tcgplayer" key first
                if (obj.has("tcgplayer") && !obj.get("tcgplayer").isJsonNull) {
                    val price = extractFirstDouble(obj.get("tcgplayer"))
                    if (price != null) return price
                }

                // Search nested objects (skip "links" and "history" so we only get current price)
                for ((key, value) in obj.entrySet()) {
                    if (key != "links" && key != "history" && value.isJsonObject) {
                        val nested = findTcgPriceRecursive(value)
                        if (nested != null) return nested
                    }
                }
            }
            return null
        }

        private fun extractFirstDouble(element: JsonElement?): Double? {
            if (element == null || element.isJsonNull) return null
            if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                if (prim.isNumber) return prim.asDouble
                if (prim.isString) return prim.asString.toDoubleOrNull()
            }
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                for (k in listOf("market", "price", "usd", "normal", "foil", "low", "value")) {
                    obj.getDouble(k)?.let { return it }
                }
                for ((_, v) in obj.entrySet()) {
                    extractFirstDouble(v)?.let { return it }
                }
            }
            return null
        }

        private fun JsonObject.getString(key: String): String? {
            return if (has(key) && get(key).isJsonPrimitive) get(key).asString else null
        }

        private fun JsonObject.getDouble(key: String): Double? {
            return if (has(key) && get(key).isJsonPrimitive) get(key).asString.toDoubleOrNull() else null
        }
    }
}