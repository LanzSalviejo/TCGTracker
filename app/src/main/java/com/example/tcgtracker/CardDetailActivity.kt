package com.example.tcgtracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch
import java.util.Locale

class CardDetailActivity : AppCompatActivity() {

    private var usdMarketPrice: Double = 0.0
    private var calculatedCadPrice: Double = 0.0
    private var currentFxRate: Double = 1.40
    private var directTcgPlayerUrl: String? = null

    private lateinit var etRate: EditText
    private lateinit var etQuantity: EditText
    private lateinit var tvCadResult: TextView
    private lateinit var tvFxStatus: TextView
    private lateinit var btnRefreshRate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_detail)

        val cardId = intent.getStringExtra("CARD_ID") ?: ""
        val cardName = intent.getStringExtra("CARD_NAME") ?: ""
        val cardCode = intent.getStringExtra("CARD_CODE") ?: ""
        val printingId = intent.getStringExtra("PRINTING_ID") ?: ""
        val imageUrl = intent.getStringExtra("IMAGE_URL") ?: ""

        val ivImage = findViewById<ImageView>(R.id.ivDetailCardImage)
        val tvName = findViewById<TextView>(R.id.tvDetailCardName)
        val tvUsd = findViewById<TextView>(R.id.tvDetailUsdPrice)
        etRate = findViewById(R.id.etExchangeRate)
        etQuantity = findViewById(R.id.etQuantity)
        tvFxStatus = findViewById(R.id.tvFxStatus)
        btnRefreshRate = findViewById(R.id.btnRefreshRate)
        val btnCalculate = findViewById<Button>(R.id.btnCalculateCAD)
        tvCadResult = findViewById(R.id.tvCalculatedCadPrice)
        val btnSave = findViewById<Button>(R.id.btnSaveToWatchlist)
        val btnViewTcgSales = findViewById<Button>(R.id.btnViewTcgPlayerSales)

        tvName.text = cardName
        ivImage.load(imageUrl)

        // 1. Fetch live USD -> CAD exchange rate
        loadLiveExchangeRate(forceRefresh = false)

        // 2. Display embedded USD price if present, and fetch OpenRift price/link details
        val embeddedPrice = intent.getDoubleExtra("EMBEDDED_PRICE", -1.0)
        if (embeddedPrice >= 0.0) {
            usdMarketPrice = embeddedPrice
            tvUsd.text = String.format(Locale.US, "TCG Market: $%.2f USD", usdMarketPrice)
            recalculateCadPrice()
        }

        if (printingId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val rawPrice = RiftApiService.create().getCardPriceRaw(printingId)
                    directTcgPlayerUrl = extractTcgPlayerUrl(rawPrice.toString())

                    if (embeddedPrice < 0.0) {
                        usdMarketPrice = RiftApiService.extractUsdPrice(rawPrice) ?: 0.0
                        tvUsd.text = String.format(Locale.US, "TCG Market: $%.2f USD", usdMarketPrice)
                        recalculateCadPrice()
                    }
                } catch (e: Exception) {
                    if (embeddedPrice < 0.0) {
                        tvUsd.text = "Market Price: Not Available"
                    }
                }
            }
        }

        // Manual live FX refresh
        btnRefreshRate.setOnClickListener {
            loadLiveExchangeRate(forceRefresh = true)
        }

        // Manual CAD recalculation
        btnCalculate.setOnClickListener {
            recalculateCadPrice()
        }

        // Open TCGplayer product page (or pre-filtered Riftbound search) for Last Sold history
        btnViewTcgSales.setOnClickListener {
            val targetUrl = directTcgPlayerUrl ?: buildTcgPlayerSearchUrl(cardName, cardCode)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No browser app found to open TCGplayer.", Toast.LENGTH_SHORT).show()
            }
        }

        // Save card into Room DB
        btnSave.setOnClickListener {
            recalculateCadPrice()

            val quantity = etQuantity.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val uniquePrintingKey = printingId.ifBlank { "${cardId}_${cardCode}" }
            val savedCard = SavedCard(
                id = uniquePrintingKey,
                name = cardName,
                publicCode = cardCode,
                imageUrl = imageUrl,
                usdPrice = usdMarketPrice,
                cadPrice = calculatedCadPrice,
                quantity = quantity
            )

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                db.cardDao().insertCard(savedCard)
                Toast.makeText(this@CardDetailActivity, "Saved to collection!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun extractTcgPlayerUrl(rawJson: String): String? {
        val urlRegex = Regex("""https?://(?:www\.)?tcgplayer\.com/product/(\d+)""", RegexOption.IGNORE_CASE)
        urlRegex.find(rawJson)?.groupValues?.get(1)?.let { productId ->
            return "https://www.tcgplayer.com/product/$productId"
        }

        val idRegex = Regex(""""tcgplayer(?:ProductId|_id)"\s*:\s*"?(\d+)"?""", RegexOption.IGNORE_CASE)
        idRegex.find(rawJson)?.groupValues?.get(1)?.let { productId ->
            return "https://www.tcgplayer.com/product/$productId"
        }

        return null
    }

    private fun buildTcgPlayerSearchUrl(cardName: String, cardCode: String): String {
        val query = cardName.ifBlank { cardCode }.trim()
        val encodedQuery = Uri.encode(query)
        return "https://www.tcgplayer.com/search/riftbound-league-of-legends-trading-card-game/product?q=$encodedQuery&view=grid"
    }

    private fun loadLiveExchangeRate(forceRefresh: Boolean) {
        btnRefreshRate.isEnabled = false
        tvFxStatus.text = "Updating USD/CAD rate..."

        lifecycleScope.launch {
            val fxResult = ExchangeRateRepository.getUsdToCadRate(
                context = this@CardDetailActivity,
                forceRefresh = forceRefresh
            )

            currentFxRate = fxResult.rate
            etRate.setText(String.format(Locale.US, "%.4f", currentFxRate))
            tvFxStatus.text = String.format(
                Locale.US,
                "1 USD = %.4f CAD (%s • %s)",
                fxResult.rate,
                fxResult.sourceLabel,
                fxResult.rateDate
            )

            recalculateCadPrice()
            btnRefreshRate.isEnabled = true
        }
    }

    private fun recalculateCadPrice() {
        val rate = etRate.text.toString().toDoubleOrNull() ?: currentFxRate
        val quantity = etQuantity.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

        calculatedCadPrice = ExchangeRateRepository.convertUsdToCad(usdMarketPrice, rate)
        val totalCad = calculatedCadPrice * quantity

        tvCadResult.text = if (quantity > 1) {
            String.format(
                Locale.US,
                "Unit: $%.2f CAD  |  Total (×%d): $%.2f CAD",
                calculatedCadPrice,
                quantity,
                totalCad
            )
        } else {
            String.format(Locale.US, "Price in CAD: $%.2f", calculatedCadPrice)
        }
    }
}