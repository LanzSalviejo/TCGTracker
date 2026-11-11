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
    private lateinit var btnGoToWatchlist: Button
    private lateinit var rvCards: RecyclerView
    private lateinit var adapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSearch = findViewById(R.id.etSearchQuery)
        btnSearch = findViewById(R.id.btnSearch)
        btnGoToWatchlist = findViewById(R.id.btnGoToWatchlist)
        rvCards = findViewById(R.id.rvCards)

        rvCards.layoutManager = LinearLayoutManager(this)
        adapter = CardAdapter(emptyList()) { selectedCard ->
            val intent = Intent(this, CardDetailActivity::class.java).apply {
                putExtra("CARD_ID", selectedCard.id)
                putExtra("CARD_NAME", selectedCard.name)
                putExtra("CARD_CODE", selectedCard.publicCode ?: "N/A")
                putExtra("PRINTING_ID", selectedCard.defaultPrintingId ?: "")
                putExtra("IMAGE_URL", selectedCard.getImageUrl())
                putExtra("EMBEDDED_PRICE", selectedCard.embeddedUsdPrice ?: -1.0)
            }
            startActivity(intent)
        }
        rvCards.adapter = adapter

        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            } else {
                Toast.makeText(this, "Please enter a card name", Toast.LENGTH_SHORT).show()
            }
        }

        btnGoToWatchlist.setOnClickListener {
            Toast.makeText(this, "Watchlist coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSearch(query: String) {
        lifecycleScope.launch {
            try {
                val api = RiftApiService.create()
                val rawResponse = api.searchCardsRaw(query)
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