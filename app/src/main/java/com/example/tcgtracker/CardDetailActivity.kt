package com.example.tcgtracker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch

class CardDetailActivity : AppCompatActivity() {

    private var cardId: String = ""
    private var cardName: String = ""
    private var cardCode: String = ""
    private var printingId: String = ""
    private var imageUrl: String = ""
    private var embeddedPrice: Double = -1.0

    private var usdMarketPrice: Double = 0.0
    private var calculatedCadPrice: Double = 0.0
    private var currentFxRate: Double = 1.40
    private var directTcgPlayerUrl: String? = null

    private lateinit var ivImage: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvUsd: TextView
    private lateinit var etRate: EditText
    private lateinit var etQuantity: EditText
    private lateinit var tvCadResult: TextView
    private lateinit var tvFxStatus: TextView
    private lateinit var btnRefreshRate: Button
    private lateinit var btnCalculate: Button
    private lateinit var btnSave: Button
    private lateinit var btnViewTcgSales: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_detail)

        readIntentExtras()
        setupViews()
        setupListeners()
        loadLiveExchangeRate(forceRefresh = false)
        loadCardPrice()
    }

    private fun readIntentExtras() {
        cardId = intent.getStringExtra(IntentKeys.CARD_ID) ?: ""
        cardName = intent.getStringExtra(IntentKeys.CARD_NAME) ?: ""
        cardCode = intent.getStringExtra(IntentKeys.CARD_CODE) ?: ""
        printingId = intent.getStringExtra(IntentKeys.PRINTING_ID) ?: ""
        imageUrl = intent.getStringExtra(IntentKeys.IMAGE_URL) ?: ""
        embeddedPrice = intent.getDoubleExtra(IntentKeys.EMBEDDED_PRICE, -1.0)
    }

    private fun setupViews() {
        ivImage = findViewById(R.id.ivDetailCardImage)
        tvName = findViewById(R.id.tvDetailCardName)
        tvVersion = findViewById(R.id.tvDetailCardVersion)
        tvUsd = findViewById(R.id.tvDetailUsdPrice)
        etRate = findViewById(R.id.etExchangeRate)
        etQuantity = findViewById(R.id.etQuantity)
        tvFxStatus = findViewById(R.id.tvFxStatus)
        btnRefreshRate = findViewById(R.id.btnRefreshRate)
        btnCalculate = findViewById(R.id.btnCalculateCAD)
        tvCadResult = findViewById(R.id.tvCalculatedCadPrice)
        btnSave = findViewById(R.id.btnSaveToWatchlist)
        btnViewTcgSales = findViewById(R.id.btnViewTcgPlayerSales)

        tvName.text = cardName

        val versionLabel = RiftApiService.getVersionLabel(cardCode)
        tvVersion.text = if (cardCode.isNotBlank()) "$cardCode • $versionLabel" else versionLabel
        tvVersion.setTextColor(FormatUtils.getVersionBadgeColor(versionLabel))

        ivImage.load(imageUrl.ifEmpty { null }) {
            crossfade(true)
            fallback(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun setupListeners() {
        btnRefreshRate.setOnClickListener { loadLiveExchangeRate(forceRefresh = true) }
        btnCalculate.setOnClickListener { recalculateCadPrice() }
        btnViewTcgSales.setOnClickListener { openTcgPlayerPage() }
        btnSave.setOnClickListener { saveCardToWatchlist() }
    }

    private fun loadCardPrice() {
        if (embeddedPrice >= 0.0) {
            usdMarketPrice = embeddedPrice
            tvUsd.text = "TCG Market: ${FormatUtils.formatUsd(usdMarketPrice)}"
            recalculateCadPrice()
        }

        if (printingId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val rawPrice = RiftApiService.api.getCardPriceRaw(printingId)
                directTcgPlayerUrl = extractTcgPlayerUrl(rawPrice.toString())

                if (embeddedPrice < 0.0) {
                    usdMarketPrice = RiftApiService.extractUsdPrice(rawPrice) ?: 0.0
                    tvUsd.text = "TCG Market: ${FormatUtils.formatUsd(usdMarketPrice)}"
                    recalculateCadPrice()
                }
            } catch (e: Exception) {
                if (embeddedPrice < 0.0) {
                    tvUsd.text = "Market Price: Not Available"
                }
            }
        }
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
            etRate.setText(FormatUtils.formatDecimal(currentFxRate, decimals = 4))
            tvFxStatus.text = FormatUtils.formatFxStatus(fxResult)

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
            "Unit: ${FormatUtils.formatCad(calculatedCadPrice)}  |  Total (×$quantity): ${FormatUtils.formatCad(totalCad)}"
        } else {
            "Price in CAD: ${FormatUtils.formatCad(calculatedCadPrice)}"
        }
    }

    private fun saveCardToWatchlist() {
        recalculateCadPrice()

        val quantity = etQuantity.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
        val uniqueId = printingId.ifBlank { "${cardId}_${cardCode}" }

        val savedCard = SavedCard(
            id = uniqueId,
            name = cardName,
            publicCode = cardCode,
            imageUrl = imageUrl,
            usdPrice = usdMarketPrice,
            cadPrice = calculatedCadPrice,
            quantity = quantity
        )

        lifecycleScope.launch {
            AppDatabase.getDatabase(applicationContext).cardDao().insertCard(savedCard)
            Toast.makeText(this@CardDetailActivity, "Saved to collection!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun openTcgPlayerPage() {
        val targetUrl = directTcgPlayerUrl ?: buildTcgPlayerSearchUrl(cardName, cardCode)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No browser app found to open TCGplayer.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Checks OpenRift's JSON response for a direct TCGplayer product ID link.
     * Falls back to a pre-filled TCGplayer Riftbound search if no direct link is included.
     */
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
}
