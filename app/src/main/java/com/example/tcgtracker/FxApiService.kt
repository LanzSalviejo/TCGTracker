package com.example.tcgtracker

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class FxRateResponse(
    val date: String,
    val base: String,
    val quote: String,
    val rate: Double
)

interface FxApiService {
    @GET("v2/rate/usd/cad")
    suspend fun getUsdToCadRate(): FxRateResponse
}

object FxApiClient {
    private const val BASE_URL = "https://api.frankfurter.dev/"

    val service: FxApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FxApiService::class.java)
    }
}