package com.example.tcgtracker

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class WatchlistActivity : AppCompatActivity() {

    private lateinit var rvWatchlist: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvFxInfo: TextView
    private lateinit var tvTotalItemCount: TextView
    private lateinit var tvTotalCad: TextView
    private lateinit var tvTotalMarketRef: TextView
    private lateinit var adapter: WatchlistAdapter

    private var currentFxRate: Double = 1.40
    private var fxSourceLabel: String = "Cached"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchlist)

        rvWatchlist = findViewById(R.id.rvWatchlist)
        tvEmpty = findViewById(R.id.tvEmptyWatchlist)
        tvFxInfo = findViewById(R.id.tvWatchlistFxInfo)
        tvTotalItemCount = findViewById(R.id.tvTotalItemCount)
        tvTotalCad = findViewById(R.id.tvWatchlistTotalCad)
        tvTotalMarketRef = findViewById(R.id.tvWatchlistTotalMarketRef)

        adapter = WatchlistAdapter(
            cards = emptyList(),
            liveFxRate = currentFxRate,
            onCardClick = { savedCard -> openCardDetail(savedCard) },
            onEditPriceClick = { savedCard -> showEditPriceDialog(savedCard) },
            onQuantityChange = { savedCard, newQty -> updateCardQuantity(savedCard, newQty) },
            onDeleteClick = { savedCard -> confirmDelete(savedCard) }
        )

        rvWatchlist.layoutManager = LinearLayoutManager(this)
        rvWatchlist.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadWatchlist()
    }

    private fun loadWatchlist() {
        lifecycleScope.launch {
            val fxResult = ExchangeRateRepository.getUsdToCadRate(
                context = this@WatchlistActivity,
                forceRefresh = false
            )
            currentFxRate = fxResult.rate
            fxSourceLabel = fxResult.sourceLabel

            tvFxInfo.text = String.format(
                Locale.US,
                "1 USD = %.4f CAD (%s • %s)",
                fxResult.rate,
                fxResult.sourceLabel,
                fxResult.rateDate
            )

            val savedCards = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).cardDao().getAllCards()
            }

            renderWatchlist(savedCards)
        }
    }

    private fun renderWatchlist(savedCards: List<SavedCard>) {
        adapter.updateData(savedCards, currentFxRate)

        if (savedCards.isEmpty()) {
            rvWatchlist.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE
            tvTotalItemCount.text = "0 cards"
            tvTotalCad.text = "$0.00 CAD"
            tvTotalMarketRef.text = "TCG Market Ref: $0.00 USD"
            return
        }

        rvWatchlist.visibility = View.VISIBLE
        tvEmpty.visibility = View.GONE

        val totalUnits = savedCards.sumOf { it.quantity }
        val totalCad = savedCards.sumOf { it.cadPrice * it.quantity }
        val totalMarketUsd = savedCards.sumOf { it.usdPrice * it.quantity }

        tvTotalItemCount.text = "${savedCards.size} unique • $totalUnits total"
        tvTotalCad.text = String.format(Locale.US, "$%.2f CAD", totalCad)
        tvTotalMarketRef.text = String.format(
            Locale.US,
            "TCG Market Ref: $%.2f USD",
            totalMarketUsd
        )
    }

    private fun showEditPriceDialog(card: SavedCard) {
        val marketCad = ExchangeRateRepository.convertUsdToCad(card.usdPrice, currentFxRate)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 24, 56, 8)
        }

        val tvHint = TextView(this).apply {
            text = if (card.usdPrice > 0.0) {
                String.format(
                    Locale.US,
                    "TCG Market: $%.2f USD (≈ $%.2f CAD)\nEnter custom unit price (CAD):",
                    card.usdPrice,
                    marketCad
                )
            } else {
                "Enter custom unit price (CAD):"
            }
            textSize = 14f
        }

        val etPrice = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", card.cadPrice))
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
        val updatedCard = card.copy(cadPrice = newUnitCad)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).cardDao().updateCard(updatedCard)
            }
            Toast.makeText(
                this@WatchlistActivity,
                String.format(Locale.US, "Updated %s to $%.2f CAD", card.name, newUnitCad),
                Toast.LENGTH_SHORT
            ).show()
            loadWatchlist()
        }
    }

    private fun updateCardQuantity(card: SavedCard, newQuantity: Int) {
        // Keep the user's current/adjusted cadPrice intact when changing quantity
        val updatedCard = card.copy(quantity = newQuantity)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(applicationContext).cardDao().updateCard(updatedCard)
            }
            loadWatchlist()
        }
    }

    private fun confirmDelete(card: SavedCard) {
        AlertDialog.Builder(this)
            .setTitle("Remove Card")
            .setMessage("Remove ${card.name} (${card.publicCode}) from your watchlist?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.getDatabase(applicationContext).cardDao().deleteCard(card)
                    }
                    Toast.makeText(this@WatchlistActivity, "Removed ${card.name}", Toast.LENGTH_SHORT).show()
                    loadWatchlist()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openCardDetail(card: SavedCard) {
        val intent = Intent(this, CardDetailActivity::class.java).apply {
            putExtra("CARD_ID", card.id)
            putExtra("CARD_NAME", card.name)
            putExtra("CARD_CODE", card.publicCode)
            putExtra("PRINTING_ID", card.id)
            putExtra("IMAGE_URL", card.imageUrl)
            if (card.usdPrice > 0.0) {
                putExtra("EMBEDDED_PRICE", card.usdPrice)
            }
        }
        startActivity(intent)
    }
}