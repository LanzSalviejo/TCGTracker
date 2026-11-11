package com.example.tcgtracker

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import kotlinx.coroutines.launch

class CardDetailActivity : AppCompatActivity() {

    private var usdMarketPrice: Double = 0.0
    private var calculatedCadPrice: Double = 0.0

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
        val etRate = findViewById<EditText>(R.id.etExchangeRate)
        val etQuantity = findViewById<EditText>(R.id.etQuantity)
        val btnCalculate = findViewById<Button>(R.id.btnCalculateCAD)
        val tvCadResult = findViewById<TextView>(R.id.tvCalculatedCadPrice)
        val btnSave = findViewById<Button>(R.id.btnSaveToWatchlist)

        tvName.text = cardName
        ivImage.load(imageUrl)

        // Fetch live TCGplayer market price
        if (printingId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    val priceResponse = RiftApiService.create().getCardPrice(printingId)
                    usdMarketPrice = priceResponse.tcgplayer?.market ?: 0.0
                    tvUsd.text = "TCG Market: $${String.format("%.2f", usdMarketPrice)} USD"
                } catch (e: Exception) {
                    tvUsd.text = "Market Price: Not Available"
                }
            }
        }

        // Computation: USD * ExchangeRate
        btnCalculate.setOnClickListener {
            val rate = etRate.text.toString().toDoubleOrNull() ?: 1.38
            calculatedCadPrice = usdMarketPrice * rate
            tvCadResult.text = "Price in CAD: $${String.format("%.2f", calculatedCadPrice)}"
        }

        // Save card into Room DB
        btnSave.setOnClickListener {
            val quantity = etQuantity.text.toString().toIntOrNull() ?: 1
            val savedCard = SavedCard(
                id = cardId,
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
}