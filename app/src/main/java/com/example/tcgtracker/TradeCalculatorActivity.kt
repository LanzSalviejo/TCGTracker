package com.example.tcgtracker

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
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

        setupViews()
        prefillWatchlistTotal()
        setupListeners()
        updateCalculations()
    }

    private fun setupViews() {
        etMarketValueCad = findViewById(R.id.etMarketValueCad)
        etTradePercent = findViewById(R.id.etTradePercent)
        etYourCardsCad = findViewById(R.id.etYourCardsCad)
        tvTradeOfferCad = findViewById(R.id.tvTradeOfferCad)
        tvMarginCad = findViewById(R.id.tvMarginCad)
        tvSettlementResult = findViewById(R.id.tvSettlementResult)
    }

    private fun prefillWatchlistTotal() {
        val incomingTotalCad = intent.getDoubleExtra(IntentKeys.WATCHLIST_TOTAL_CAD, 0.0)
        if (incomingTotalCad > 0.0) {
            etMarketValueCad.setText(FormatUtils.formatDecimal(incomingTotalCad))
        }
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnPct70).setOnClickListener { setPercentage(70) }
        findViewById<Button>(R.id.btnPct75).setOnClickListener { setPercentage(75) }
        findViewById<Button>(R.id.btnPct80).setOnClickListener { setPercentage(80) }
        findViewById<Button>(R.id.btnPct85).setOnClickListener { setPercentage(85) }

        etMarketValueCad.doAfterTextChanged { updateCalculations() }
        etTradePercent.doAfterTextChanged { updateCalculations() }
        etYourCardsCad.doAfterTextChanged { updateCalculations() }

        findViewById<Button>(R.id.btnClearTrade).setOnClickListener {
            etMarketValueCad.text.clear()
            etYourCardsCad.text.clear()
            etTradePercent.setText("80")
            updateCalculations()
        }

        findViewById<Button>(R.id.btnBackFromTrade).setOnClickListener { finish() }
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

        tvTradeOfferCad.text = "Offer / Trade Credit: ${FormatUtils.formatCad(tradeOffer)}"
        tvMarginCad.text = "Spread / Margin: ${FormatUtils.formatCad(margin)}"

        if (marketValue <= 0.0 && yourCardsValue <= 0.0) {
            tvSettlementResult.text = "Enter a market value to calculate"
            tvSettlementResult.setTextColor(Color.parseColor("#94A3B8"))
            return
        }

        // Mode 1: Straight Cash Buyout (Your Cards left blank)
        if (yourCardsValue <= 0.0) {
            tvSettlementResult.text = "Cash Buyout Payout: ${FormatUtils.formatCad(tradeOffer)}"
            tvSettlementResult.setTextColor(Color.parseColor("#10B981"))
            return
        }

        // Mode 2: Trade-Up / Cash Difference
        val difference = yourCardsValue - tradeOffer
        when {
            difference > 0.005 -> {
                tvSettlementResult.text = "Customer Pays You: ${FormatUtils.formatCad(difference)}"
                tvSettlementResult.setTextColor(Color.parseColor("#10B981"))
            }
            difference < -0.005 -> {
                tvSettlementResult.text = "You Pay Customer: ${FormatUtils.formatCad(abs(difference))}"
                tvSettlementResult.setTextColor(Color.parseColor("#F59E0B"))
            }
            else -> {
                tvSettlementResult.text = "Even Trade! ($0.00 difference)"
                tvSettlementResult.setTextColor(Color.parseColor("#818CF8"))
            }
        }
    }
}
