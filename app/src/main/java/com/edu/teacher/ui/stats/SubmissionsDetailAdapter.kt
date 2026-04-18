package com.edu.teacher.ui.stats

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

class SubmissionsDetailAdapter(
    private val onItemClick: (JSONObject, String) -> Unit
) : RecyclerView.Adapter<SubmissionsDetailAdapter.ViewHolder>() {
    
    private var submissions: List<JSONObject> = emptyList()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar"))
    
    fun submitList(list: List<JSONObject>) {
        submissions = list
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_submission_detail, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(submissions[position])
    }
    
    override fun getItemCount(): Int = submissions.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val studentNameText: TextView = itemView.findViewById(R.id.studentNameText)
        private val classInfoText: TextView = itemView.findViewById(R.id.classInfoText)
        private val scoreText: TextView = itemView.findViewById(R.id.scoreText)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        private val viewButton: TextView = itemView.findViewById(R.id.viewButton)
        private val gradeButton: TextView = itemView.findViewById(R.id.gradeButton)
        
        fun bind(submission: JSONObject) {
            val studentName = submission.optString("studentName", "طالب")
            val grade = submission.optString("studentGrade", "")
            val section = submission.optString("studentSection", "")
            val summary = submission.optJSONObject("summary")
            val score = summary?.optInt("percentage", 0) ?: 0
            val status = submission.optString("status", "pending")
            val timestamp = submission.optLong("timestamp", 0)
            val finalScore = submission.optInt("finalScore", 0)
            
            studentNameText.text = studentName
            classInfoText.text = "$grade - $section"
            dateText.text = if (timestamp > 0) dateFormat.format(Date(timestamp)) else ""
            
            val displayScore = if (status == "graded") finalScore else score
            scoreText.text = "$displayScore%"
            
            when (status) {
                "pending" -> {
                    statusText.text = "قيد المراجعة"
                    statusText.setTextColor(Color.parseColor("#F59E0B"))
                    gradeButton.visibility = View.VISIBLE
                }
                "graded" -> {
                    statusText.text = "تم التصحيح"
                    statusText.setTextColor(Color.parseColor("#10B981"))
                    gradeButton.visibility = View.GONE
                }
                else -> {
                    statusText.text = status
                    statusText.setTextColor(Color.GRAY)
                    gradeButton.visibility = View.GONE
                }
            }
            
            when {
                displayScore >= 80 -> scoreText.setTextColor(Color.parseColor("#10B981"))
                displayScore >= 50 -> scoreText.setTextColor(Color.parseColor("#F59E0B"))
                else -> scoreText.setTextColor(Color.parseColor("#EF4444"))
            }
            
            viewButton.setOnClickListener {
                onItemClick(submission, "view")
            }
            
            gradeButton.setOnClickListener {
                onItemClick(submission, "grade")
            }
        }
    }
}