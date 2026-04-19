package com.edu.student.ui.common

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class ImageAdapter(
    private val images: List<String>,
    private val onImageClick: (String) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(images[position])
    }
    
    override fun getItemCount(): Int = images.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView
        
        init {
            itemView.layoutParams = ViewGroup.LayoutParams(120, 120)
            imageView = ImageView(itemView.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            (itemView as? FrameLayout)?.addView(imageView) ?: run {
                (itemView as? android.widget.LinearLayout)?.addView(imageView)
            }
        }
        
        fun bind(imagePath: String) {
            try {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                imageView.setImageBitmap(bitmap)
            } catch (e: Exception) {
                imageView.setImageResource(android.R.color.darker_gray)
            }
            
            itemView.setOnClickListener { onImageClick(imagePath) }
        }
    }
}