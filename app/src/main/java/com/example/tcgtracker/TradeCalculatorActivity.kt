package com.example.tcgtracker

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.util.Locale
import kotlin.math.abs

class TradeCalculatorActivity : AppCompatActivity() {

    private lateinit var etMarketValueCad: EditText
    private lateinit var etTradePercent: EditText
    private lateinit var etYourCardsCad: EditText
    private lateinit var tvTradeOfferCad: TextView
    private lateinit var tvMarginCad: TextView
    private lateinit var tvSettlementResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trade_calculator)

        etMarketValueCad = findViewById(R.id.etMarketValueCad)
        etTradePercent = findViewById(R.id.etTradePercent)
        etYourCardsCad = findViewById(R.id.etYourCardsCad)
        tvTradeOfferCad = findViewById(R.id.tvTradeOfferCad)
        tvMarginCad = findViewById(R.id.tvMarginCad)
        tvSettlementResult = findViewById(R.id.tvSettlementResult)

        // If launched from WatchlistActivity, auto-fill the watchlist total
        val incomingTotalCad = intent.getDoubleExtra("WATCHLIST_TOTAL_CAD", 0.0)
        if (incomingTotalCad > 0.0) {
            etMarketValueCad.setText(String.format(Locale.CANADA, "%.2f", incomingTotalCad))
        }

        // Preset percentage buttons
        findViewById<Button>(R.id.btnPct70).setOnClickListener { setPercentage(70) }
        findViewById<Button>(R.id.btnPct75).setOnClickListener { setPercentage(75) }
        findViewById<Button>(R.id.btnPct80).setOnClickListener { setPercentage(80) }
        findViewById<Button>(R.id.btnPct85).setOnClickListener { setPercentage(85) }

        // Recalculate automatically whenever any input changes
        etMarketValueCad.doAfterTextChanged { updateCalculations() }
        etTradePercent.doAfterTextChanged { updateCalculations() }
        etYourCardsCad.doAfterTextChanged { updateCalculations() }

        findViewById<Button>(R.id.btnClearTrade).setOnClickListener {
            etMarketValueCad.text.clear()
            etYourCardsCad.text.clear()
            etTradePercent.setText("80")
            updateCalculations()
        }

        findViewById<Button>(R.id.btnBackFromTrade).setOnClickListener {
            finish()
        }

        // Run once on startup (handles pre-filled watchlist total)
        updateCalculations()
    }

    private fun setPercentage(percent: Int) {
        etTradePercent.setText(percent.toString())
        etTradePercent.setSelection(etTradePercent.text.length)
        updateCalculations()
    }

    private fun updateCalculations() {
        val marketValue = etMarketValueCad.text.toString().toDoubleOrNull() ?: 0.0
        val percent = etTradePercent.text.toString().toDoubleOrNull() ?: 0.0
        val yourCardsValue = etYourCardsCad.text.toString().toDoubleOrNull() ?: 0.0

        val tradeOffer = marketValue * (percent / 100.0)
        val margin = marketValue - tradeOffer

        tvTradeOfferCad.text = String.format(
            Locale.CANADA,
            "Offer / Trade Credit: $%.2f CAD",
            tradeOffer
        )
        tvMarginCad.text = String.format(
            Locale.CANADA,
            "Spread / Margin: $%.2f CAD",
            margin
        )

        if (marketValue <= 0.0 && yourCardsValue <= 0.0) {
            tvSettlementResult.text = "Enter a market value to calculate"
            tvSettlementResult.setTextColor(Color.parseColor("#94A3B8"))
            return
        }

        // Mode 1: Straight Cash Buyout (Your Cards left blank)
        if (yourCardsValue <= 0.0) {
            tvSettlementResult.text = String.format(
                Locale.CANADA,
                "Cash Buyout Payout: $%.2f CAD",
                tradeOffer
            )
            tvSettlementResult.setTextColor(Color.parseColor("#10B981"))
            return
        }

        // Mode 2: Trade-Up / Cash Difference
        val difference = yourCardsValue - tradeOffer
        when {
            difference > 0.005 -> {
                tvSettlementResult.text = String.format(
                    Locale.CANADA,
                    "Customer Pays You: $%.2f CAD",
                    difference
                )
                tvSettlementResult.setTextColor(Color.parseColor("#10B981"))
            }
            difference < -0.005 -> {
                tvSettlementResult.text = String.format(
                    Locale.CANADA,
                    "You Pay Customer: $%.2f CAD",
                    abs(difference)
                )
                tvSettlementResult.setTextColor(Color.parseColor("#F59E0B"))
            }
            else -> {
                tvSettlementResult.text = "Even Trade! ($0.00 difference)"
                tvSettlementResult.setTextColor(Color.parseColor("#818CF8"))
            }
        }
    }
}