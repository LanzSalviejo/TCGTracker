package com.example.tcgtracker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExchangeRateResult(
    val rate: Double,
    val rateDate: String,
    val sourceLabel: String // "Live", "Cached", "Offline Cache", or "Fallback"
)

/**
 * Provides the USD -> CAD exchange rate using a 3-step strategy:
 * 1. Return the cached rate from SharedPreferences if it is less than 15 minutes old.
 * 2. Otherwise fetch the live rate from the Frankfurter API and save it locally.
 * 3. If offline, fall back to the last saved rate (or 1.40 if never fetched).
 */
object ExchangeRateRepository {

    private const val PREFS_NAME = "fx_rate_prefs"
    private const val KEY_RATE = "usd_to_cad_rate"
    private const val KEY_DATE = "usd_to_cad_date"
    private const val KEY_FETCHED_AT = "usd_to_cad_fetched_at"

    private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes
    private const val DEFAULT_FALLBACK_RATE = 1.40

    suspend fun getUsdToCadRate(
        context: Context,
        forceRefresh: Boolean = false
    ): ExchangeRateResult = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedRate = prefs.getFloat(KEY_RATE, -1f).toDouble()
        val cachedDate = prefs.getString(KEY_DATE, "Unknown") ?: "Unknown"
        val fetchedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        val isCacheFresh = cachedRate > 0 && (System.currentTimeMillis() - fetchedAt) < CACHE_TTL_MS

        // 1. Return fresh cache if we don't need to force-refresh
        if (!forceRefresh && isCacheFresh) {
            return@withContext ExchangeRateResult(
                rate = cachedRate,
                rateDate = cachedDate,
                sourceLabel = "Cached"
            )
        }

        // 2. Try fetching the live rate from Frankfurter API
        try {
            val response = FxApiClient.service.getUsdToCadRate()
            if (response.rate > 0.0) {
                prefs.edit()
                    .putFloat(KEY_RATE, response.rate.toFloat())
                    .putString(KEY_DATE, response.date)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()

                return@withContext ExchangeRateResult(
                    rate = response.rate,
                    rateDate = response.date,
                    sourceLabel = "Live"
                )
            }
        } catch (_: Exception) {
            // Network error or timeout — fall through to cached/fallback handling
        }

        // 3. Offline fallback: use last saved rate if available, otherwise default
        if (cachedRate > 0) {
            ExchangeRateResult(
                rate = cachedRate,
                rateDate = cachedDate,
                sourceLabel = "Offline Cache"
            )
        } else {
            ExchangeRateResult(
                rate = DEFAULT_FALLBACK_RATE,
                rateDate = "Default",
                sourceLabel = "Fallback"
            )
        }
    }

    fun convertUsdToCad(usdAmount: Double, rate: Double): Double {
        return usdAmount * rate
    }
}
