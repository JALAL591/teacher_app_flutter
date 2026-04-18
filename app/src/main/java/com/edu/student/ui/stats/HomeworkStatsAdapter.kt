package com.edu.student.ui.stats

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.R
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class HomeworkStatsAdapter(
    private val onItemClick: (JSONObject) -> Unit
) : RecyclerView.Adapter<HomeworkStatsAdapter.ViewHolder>() {
    
    private var solutions: List<JSONObject> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("ar"))
    
    fun submitList(list: List<JSONObject>) {
        solutions = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_homework_stats, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(solutions[position])
    }
    
    override fun getItemCount(): Int = solutions.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val lessonTitleText: TextView = itemView.findViewById(R.id.lessonTitleText)
        private val subjectTitleText: TextView = itemView.findViewById(R.id.subjectTitleText)
        private val scoreText: TextView = itemView.findViewById(R.id.scoreText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val sendButton: TextView = itemView.findViewById(R.id.sendButton)
        
        fun bind(solution: JSONObject) {
            val lessonTitle = solution.optString("lessonTitle", "درس")
            val subjectTitle = solution.optString("subjectTitle", "")
            val summary = solution.optJSONObject("summary")
            val percentage = summary?.optInt("percentage", 0) ?: 0
            val timestamp = solution.optLong("timestamp", 0)
            
            lessonTitleText.text = lessonTitle
            subjectTitleText.text = subjectTitle
            scoreText.text = "$percentage%"
            dateText.text = if (timestamp > 0) dateFormat.format(Date(timestamp)) else ""
            
            when {
                percentage >= 80 -> scoreText.setTextColor(Color.parseColor("#10B981"))
                percentage >= 50 -> scoreText.setTextColor(Color.parseColor("#F59E0B"))
                else -> scoreText.setTextColor(Color.parseColor("#EF4444"))
            }
            
            itemView.setOnClickListener {
                onItemClick(solution)
            }
            
            sendButton.setOnClickListener {
                onItemClick(solution)
            }
        }
    }
}