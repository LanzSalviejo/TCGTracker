package com.example.tcgtracker

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class WatchlistActivity : AppCompatActivity() {

    private lateinit var rvWatchlist: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvFxInfo: TextView
    private lateinit var tvTotalItemCount: TextView
    private lateinit var tvTotalCad: TextView
    private lateinit var tvTotalMarketRef: TextView
    private lateinit var btnOpenTradeCalc: Button
    private lateinit var adapter: WatchlistAdapter

    private var currentFxRate: Double = 1.40
    private var currentTotalCad: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchlist)

        setupViews()
        setupRecyclerView()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        loadWatchlist()
    }

    private fun setupViews() {
        rvWatchlist = findViewById(R.id.rvWatchlist)
        tvEmpty = findViewById(R.id.tvEmptyWatchlist)
        tvFxInfo = findViewById(R.id.tvWatchlistFxInfo)
        tvTotalItemCount = findViewById(R.id.tvTotalItemCount)
        tvTotalCad = findViewById(R.id.tvWatchlistTotalCad)
        tvTotalMarketRef = findViewById(R.id.tvWatchlistTotalMarketRef)
        btnOpenTradeCalc = findViewById(R.id.btnOpenTradeCalc)
    }

    private fun setupRecyclerView() {
        adapter = WatchlistAdapter(
            cards = emptyList(),
            liveFxRate = currentFxRate,
            onCardClick = { openCardDetail(it) },
            onEditPriceClick = { showEditPriceDialog(it) },
            onQuantityChange = { card, newQty -> updateCardQuantity(card, newQty) },
            onDeleteClick = { confirmDelete(it) }
        )
        rvWatchlist.layoutManager = LinearLayoutManager(this)
        rvWatchlist.adapter = adapter
    }

    private fun setupListeners() {
        btnOpenTradeCalc.setOnClickListener {
            val intent = Intent(this, TradeCalculatorActivity::class.java).apply {
                putExtra(IntentKeys.WATCHLIST_TOTAL_CAD, currentTotalCad)
            }
            startActivity(intent)
        }
    }

    private fun loadWatchlist() {
        lifecycleScope.launch {
            val fxResult = ExchangeRateRepository.getUsdToCadRate(
                context = this@WatchlistActivity,
                forceRefresh = false
            )
            currentFxRate = fxResult.rate
            tvFxInfo.text = FormatUtils.formatFxStatus(fxResult)

            // Room suspend functions automatically run off the main thread
            val savedCards = AppDatabase.getDatabase(applicationContext).cardDao().getAllCards()
            renderWatchlist(savedCards)
        }
    }

    private fun renderWatchlist(savedCards: List<SavedCard>) {
        adapter.updateData(savedCards, currentFxRate)

        if (savedCards.isEmpty()) {
            currentTotalCad = 0.0
            rvWatchlist.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvTotalItemCount.text = "0 cards"
            tvTotalCad.text = FormatUtils.formatCad(0.0)
            tvTotalMarketRef.text = "TCG Market Ref: ${FormatUtils.formatUsd(0.0)}"
            return
        }

        rvWatchlist.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val totalUnits = savedCards.sumOf { it.quantity }
        currentTotalCad = savedCards.sumOf { it.cadPrice * it.quantity }
        val totalMarketUsd = savedCards.sumOf { it.usdPrice * it.quantity }

        tvTotalItemCount.text = "${savedCards.size} unique • $totalUnits total"
        tvTotalCad.text = FormatUtils.formatCad(currentTotalCad)
        tvTotalMarketRef.text = "TCG Market Ref: ${FormatUtils.formatUsd(totalMarketUsd)}"
    }

    private fun showEditPriceDialog(card: SavedCard) {
        val marketCad = ExchangeRateRepository.convertUsdToCad(card.usdPrice, currentFxRate)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 24, 56, 8)
        }

        val tvHint = TextView(this).apply {
            text = if (card.usdPrice > 0.0) {
                "TCG Market: ${FormatUtils.formatUsd(card.usdPrice)} (≈ ${FormatUtils.formatCad(marketCad)}) Enter custom unit price (CAD):"
            } else {
                "Enter custom unit price (CAD):"
            }
            textSize = 14f
        }

        val etPrice = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(FormatUtils.formatDecimal(card.cadPrice))
            setSelection(text.length)
        }

        container.addView(tvHint)
        container.addView(etPrice)

        val builder = AlertDialog.Builder(this)
            .setTitle("Adjust Price: ${card.name}")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val newCadPrice = etPrice.text.toString().toDoubleOrNull()
                if (newCadPrice == null || newCadPrice < 0.0) {
                    Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show()
                } else {
                    updateCardPrice(card, newCadPrice)
                }
            }
            .setNegativeButton("Cancel", null)

        if (card.usdPrice > 0.0) {
            builder.setNeutralButton("Reset to Market") { _, _ ->
                updateCardPrice(card, marketCad)
            }
        }

        builder.show()
    }

    private fun updateCardPrice(card: SavedCard, newUnitCad: Double) {
        lifecycleScope.launch {
            val updatedCard = card.copy(cadPrice = newUnitCad)
            AppDatabase.getDatabase(applicationContext).cardDao().updateCard(updatedCard)
            Toast.makeText(
                this@WatchlistActivity,
                "Updated ${card.name} to ${FormatUtils.formatCad(newUnitCad)}",
                Toast.LENGTH_SHORT
            ).show()
            loadWatchlist()
        }
    }

    private fun updateCardQuantity(card: SavedCard, newQuantity: Int) {
        lifecycleScope.launch {
            val updatedCard = card.copy(quantity = newQuantity)
            AppDatabase.getDatabase(applicationContext).cardDao().updateCard(updatedCard)
            loadWatchlist()
        }
    }

    private fun confirmDelete(card: SavedCard) {
        AlertDialog.Builder(this)
            .setTitle("Remove Card")
            .setMessage("Remove ${card.name} (${card.publicCode}) from your watchlist?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getDatabase(applicationContext).cardDao().deleteCard(card)
                    Toast.makeText(this@WatchlistActivity, "Removed ${card.name}", Toast.LENGTH_SHORT).show()
                    loadWatchlist()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCardDetail(card: SavedCard) {
        val intent = Intent(this, CardDetailActivity::class.java).apply {
            putExtra(IntentKeys.CARD_ID, card.id)
            putExtra(IntentKeys.CARD_NAME, card.name)
            putExtra(IntentKeys.CARD_CODE, card.publicCode)
            putExtra(IntentKeys.PRINTING_ID, card.id)
            putExtra(IntentKeys.IMAGE_URL, card.imageUrl)
            if (card.usdPrice > 0.0) {
                putExtra(IntentKeys.EMBEDDED_PRICE, card.usdPrice)
            }
        }
        startActivity(intent)
    }
}
