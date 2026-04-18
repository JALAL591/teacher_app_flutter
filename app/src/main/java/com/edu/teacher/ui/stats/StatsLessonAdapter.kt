package com.edu.teacher.ui.stats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.R
import org.json.JSONArray
import org.json.JSONObject

class StatsLessonAdapter(
    private val lessonsArray: JSONArray,
    private val subject: JSONObject,
    private val cls: JSONObject,
    private val onLessonClick: (JSONObject, JSONObject, JSONObject) -> Unit
) : RecyclerView.Adapter<StatsLessonAdapter.LessonViewHolder>() {
    
    private val lessons = mutableListOf<JSONObject>()
    
    init {
        for (i in 0 until lessonsArray.length()) {
            val lesson = lessonsArray.getJSONObject(i)
            lesson.put("subjectId", subject.optString("id", ""))
            lesson.put("subjectName", subject.optString("name", ""))
            lesson.put("classId", cls.optString("id", ""))
            lesson.put("className", "${cls.optString("grade", "")} - ${cls.optString("section", "")}")
            lessons.add(lesson)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LessonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stats_lesson, parent, false)
        return LessonViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LessonViewHolder, position: Int) {
        holder.bind(lessons[position])
    }
    
    override fun getItemCount(): Int = lessons.size
    
    inner class LessonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val lessonTitleText: TextView = itemView.findViewById(R.id.lessonTitleText)
        private val submissionsCountText: TextView = itemView.findViewById(R.id.submissionsCountText)
        private val averageScoreText: TextView = itemView.findViewById(R.id.averageScoreText)
        
        fun bind(lesson: JSONObject) {
            val title = lesson.optString("title", "درس")
            val stats = lesson.optJSONObject("stats") ?: JSONObject()
            val submissionsCount = stats.optInt("totalSubmissions", 0)
            val avgScore = stats.optInt("averageScore", 0)
            
            lessonTitleText.text = title
            submissionsCountText.text = "$submissionsCount تسليم"
            averageScoreText.text = "$avgScore%"
            
            itemView.setOnClickListener {
                onLessonClick(subject, cls, lesson)
            }
        }
    }
}