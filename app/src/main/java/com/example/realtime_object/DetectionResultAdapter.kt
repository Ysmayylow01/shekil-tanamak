package com.example.realtime_object

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class DetectionResultAdapter(
    private val results: List<DetectionResult>
) : RecyclerView.Adapter<DetectionResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: CardView = view.findViewById(R.id.cardResult)
        val tvLabel: TextView = view.findViewById(R.id.tvLabel)
        val tvConfidence: TextView = view.findViewById(R.id.tvConfidence)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]

        holder.tvLabel.text = result.label
        holder.tvConfidence.text = "${"%.1f".format(result.confidence * 100)}% ynanyklik"
        holder.cardView.setCardBackgroundColor(result.color)
    }

    override fun getItemCount() = results.size
}