package com.edu.teacher.ui.stats

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.R
import org.json.JSONArray
import org.json.JSONObject

// ==================== StatsSubjectAdapter ====================
class StatsSubjectAdapter(
    private val subjectsArray: JSONArray,
    private val onLessonClick: (JSONObject, JSONObject, JSONObject) -> Unit
) : RecyclerView.Adapter<StatsSubjectAdapter.SubjectViewHolder>() {
    
    private val subjects = mutableListOf<JSONObject>()
    
    init {
        for (i in 0 until subjectsArray.length()) {
            subjects.add(subjectsArray.getJSONObject(i))
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stats_subject, parent, false)
        return SubjectViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(subjects[position])
    }
    
    override fun getItemCount(): Int = subjects.size
    
    inner class SubjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subjectNameText: TextView = itemView.findViewById(R.id.subjectNameText)
        private val classesRecycler: RecyclerView = itemView.findViewById(R.id.classesRecycler)
        
        fun bind(subject: JSONObject) {
            val subjectName = subject.optString("name", "مادة")
            subjectNameText.text = subjectName
            
            val classesArray = subject.optJSONArray("classes") ?: JSONArray()
            
            classesRecycler.layoutManager = LinearLayoutManager(itemView.context)
            classesRecycler.adapter = StatsClassAdapter(classesArray, subject, onLessonClick)
        }
    }
}

// ==================== StatsClassAdapter ====================
class StatsClassAdapter(
    private val classesArray: JSONArray,
    private val subject: JSONObject,
    private val onLessonClick: (JSONObject, JSONObject, JSONObject) -> Unit
) : RecyclerView.Adapter<StatsClassAdapter.ClassViewHolder>() {
    
    private val classes = mutableListOf<JSONObject>()
    
    init {
        for (i in 0 until classesArray.length()) {
            classes.add(classesArray.getJSONObject(i))
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stats_class, parent, false)
        return ClassViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        holder.bind(classes[position])
    }
    
    override fun getItemCount(): Int = classes.size
    
    inner class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val classNameText: TextView = itemView.findViewById(R.id.classNameText)
        private val lessonsRecycler: RecyclerView = itemView.findViewById(R.id.lessonsRecycler)
        
        fun bind(cls: JSONObject) {
            val grade = cls.optString("grade", "")
            val section = cls.optString("section", "")
            classNameText.text = "$grade - $section"
            
            val lessonsArray = cls.optJSONArray("lessons") ?: JSONArray()
            
            lessonsRecycler.layoutManager = LinearLayoutManager(itemView.context)
            lessonsRecycler.adapter = StatsLessonAdapter(lessonsArray, subject, cls, onLessonClick)
        }
    }
}