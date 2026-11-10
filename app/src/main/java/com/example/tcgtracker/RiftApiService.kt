package com.example.tcgtracker

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class RiftSearchResponse(
    val results: List<RiftCard>?
)

data class RiftCard(
    val id: String,
    val name: String,
    val publicCode: String?,
    val defaultPrintingId: String?,
    val imageId: String?
) {
    fun getImageUrl(): String {
        return "https://openrift.app/media/cards/${imageId}-400w.webp"
    }
}

data class PriceResponse(
    val tcgplayer: MarketPrice?
)

data class MarketPrice(
    val market: Double?,
    val low: Double?
)

interface RiftApiService {
    @GET("api/v1/search")
    suspend fun searchCards(@Query("q") query: String): RiftSearchResponse

    @GET("api/v1/prices/{printingId}")
    suspend fun getCardPrice(@Path("printingId") printingId: String): PriceResponse

    companion object {
        fun create(): RiftApiService {
            return Retrofit.Builder()
                .baseUrl("https://openrift.app/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(RiftApiService::class.java)
        }
    }
}