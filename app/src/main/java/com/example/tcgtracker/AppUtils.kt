package com.example.tcgtracker

import android.graphics.Color
import java.util.Locale

/**
 * Shared keys used when passing data between Activities via Intent extras.
 */
object IntentKeys {
    const val CARD_ID = "CARD_ID"
    const val CARD_NAME = "CARD_NAME"
    const val CARD_CODE = "CARD_CODE"
    const val PRINTING_ID = "PRINTING_ID"
    const val IMAGE_URL = "IMAGE_URL"
    const val EMBEDDED_PRICE = "EMBEDDED_PRICE"
    const val WATCHLIST_TOTAL_CAD = "WATCHLIST_TOTAL_CAD"
}

/**
 * Shared formatting and badge styling helpers used across all screens.
 */
object FormatUtils {

    fun formatCad(amount: Double): String {
        return String.format(Locale.CANADA, "$%.2f CAD", amount)
    }

    fun formatUsd(amount: Double): String {
        return String.format(Locale.CANADA, "$%.2f USD", amount)
    }

    fun formatDecimal(amount: Double, decimals: Int = 2): String {
        return String.format(Locale.CANADA, "%.${decimals}f", amount)
    }

    fun formatFxStatus(fx: ExchangeRateResult): String {
        return String.format(
            Locale.CANADA,
            "1 USD = %.4f CAD (%s • %s)",
            fx.rate,
            fx.sourceLabel,
            fx.rateDate
        )
    }

    fun getVersionBadgeColor(versionLabel: String): Int {
        return when (versionLabel) {
            "Signature" -> Color.parseColor("#D4A017")    // Gold
            "Overnumbered" -> Color.parseColor("#C084FC") // Soft lavender (readable in dark mode)
            else -> Color.parseColor("#8A8A8A")           // Neutral gray
        }
    }
}
