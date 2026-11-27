package com.example.tcgtracker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var btnOpenWatchlist: Button
    private lateinit var rvCards: RecyclerView
    private lateinit var adapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupViews()
        setupRecyclerView()
        setupListeners()
    }

    private fun setupViews() {
        etSearch = findViewById(R.id.etSearchQuery)
        btnSearch = findViewById(R.id.btnSearch)
        btnOpenWatchlist = findViewById(R.id.btnOpenWatchlist)
        rvCards = findViewById(R.id.rvCards)
    }

    private fun setupRecyclerView() {
        adapter = CardAdapter(emptyList()) { selectedCard ->
            openCardDetail(selectedCard)
        }
        rvCards.layoutManager = LinearLayoutManager(this)
        rvCards.adapter = adapter
    }

    private fun setupListeners() {
        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                Toast.makeText(this, "Please enter a card name", Toast.LENGTH_SHORT).show()
            }
        }

        btnOpenWatchlist.setOnClickListener {
            startActivity(Intent(this, WatchlistActivity::class.java))
        }
    }

    private fun openCardDetail(card: RiftCard) {
        val intent = Intent(this, CardDetailActivity::class.java).apply {
            putExtra(IntentKeys.CARD_ID, card.id)
            putExtra(IntentKeys.CARD_NAME, card.name)
            putExtra(IntentKeys.CARD_CODE, card.publicCode ?: "N/A")
            putExtra(IntentKeys.PRINTING_ID, card.defaultPrintingId ?: "")
            putExtra(IntentKeys.IMAGE_URL, card.getImageUrl())
            putExtra(IntentKeys.EMBEDDED_PRICE, card.embeddedUsdPrice ?: -1.0)
        }
        startActivity(intent)
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            try {
                val rawResponse = RiftApiService.api.searchCards(query)
                val cardList = RiftApiService.parseSearchResponse(rawResponse)

                if (cardList.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No cards found", Toast.LENGTH_SHORT).show()
                }
                adapter.updateData(cardList)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    // OpenRift returns HTTP 404 when a search query matches 0 cards
                    adapter.updateData(emptyList())
                    Toast.makeText(this@MainActivity, "No cards found for '$query'", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Server error: HTTP ${e.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Search error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
