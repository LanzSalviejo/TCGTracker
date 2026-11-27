package com.example.tcgtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlin.math.abs

class WatchlistAdapter(
    private var cards: List<SavedCard>,
    private var liveFxRate: Double = 1.40,
    private val onCardClick: (SavedCard) -> Unit,
    private val onEditPriceClick: (SavedCard) -> Unit,
    private val onQuantityChange: (SavedCard, Int) -> Unit,
    private val onDeleteClick: (SavedCard) -> Unit
) : RecyclerView.Adapter<WatchlistAdapter.WatchlistViewHolder>() {

    class WatchlistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumb: ImageView = itemView.findViewById(R.id.ivWatchlistThumb)
        val tvName: TextView = itemView.findViewById(R.id.tvWatchlistName)
        val tvCode: TextView = itemView.findViewById(R.id.tvWatchlistCode)
        val tvPrice: TextView = itemView.findViewById(R.id.tvWatchlistPrice)
        val btnEditPrice: Button = itemView.findViewById(R.id.btnEditPrice)
        val tvSubtotal: TextView = itemView.findViewById(R.id.tvWatchlistSubtotal)
        val tvQty: TextView = itemView.findViewById(R.id.tvWatchlistQty)
        val btnMinus: Button = itemView.findViewById(R.id.btnQtyMinus)
        val btnPlus: Button = itemView.findViewById(R.id.btnQtyPlus)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteCard)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchlistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_watchlist_card, parent, false)
        return WatchlistViewHolder(view)
    }

    override fun onBindViewHolder(holder: WatchlistViewHolder, position: Int) {
        val card = cards[position]
        val versionLabel = RiftApiService.getVersionLabel(card.publicCode)

        holder.tvName.text = card.name
        holder.tvCode.text = if (card.publicCode.isNotBlank()) {
            "${card.publicCode} • $versionLabel"
        } else {
            versionLabel
        }
        holder.tvCode.setTextColor(FormatUtils.getVersionBadgeColor(versionLabel))

        val marketCad = ExchangeRateRepository.convertUsdToCad(card.usdPrice, liveFxRate)
        val unitCad = card.cadPrice
        val subtotalCad = unitCad * card.quantity
        val isCustomPrice = card.usdPrice > 0.0 && abs(unitCad - marketCad) > 0.02

        holder.tvPrice.text = if (isCustomPrice) {
            "Unit: ${FormatUtils.formatCad(unitCad)} (Custom)"
        } else {
            "Unit: ${FormatUtils.formatCad(unitCad)} (${FormatUtils.formatUsd(card.usdPrice)})"
        }

        holder.tvSubtotal.text = "Subtotal (×${card.quantity}): ${FormatUtils.formatCad(subtotalCad)}"
        holder.tvQty.text = card.quantity.toString()

        holder.ivThumb.load(card.imageUrl.ifEmpty { null }) {
            crossfade(true)
            fallback(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_gallery)
        }

        holder.itemView.setOnClickListener { onCardClick(card) }
        holder.btnEditPrice.setOnClickListener { onEditPriceClick(card) }
        holder.btnPlus.setOnClickListener { onQuantityChange(card, card.quantity + 1) }
        holder.btnMinus.setOnClickListener {
            if (card.quantity > 1) {
                onQuantityChange(card, card.quantity - 1)
            } else {
                onDeleteClick(card)
            }
        }
        holder.btnDelete.setOnClickListener { onDeleteClick(card) }
    }

    override fun getItemCount(): Int = cards.size

    fun updateData(newCards: List<SavedCard>, fxRate: Double) {
        cards = newCards
        liveFxRate = fxRate
        notifyDataSetChanged()
    }
}
