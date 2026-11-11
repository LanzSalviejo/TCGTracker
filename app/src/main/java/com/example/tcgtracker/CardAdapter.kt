package com.example.tcgtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class CardAdapter(
    private var cards: List<RiftCard>,
    private val onItemClick: (RiftCard) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivCardThumbnail)
        val tvName: TextView = itemView.findViewById(R.id.tvCardName)
        val tvCode: TextView = itemView.findViewById(R.id.tvCardCode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.tvName.text = card.name
        holder.tvCode.text = card.publicCode ?: "N/A"

        // Load image using Coil
        holder.ivThumbnail.load(card.getImageUrl())

        holder.itemView.setOnClickListener { onItemClick(card) }
    }

    override fun getItemCount(): Int = cards.size

    fun updateData(newCards: List<RiftCard>) {
        cards = newCards
        notifyDataSetChanged()
    }
}