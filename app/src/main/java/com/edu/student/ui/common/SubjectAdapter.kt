package com.edu.student.ui.common

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.*
import com.edu.student.domain.model.Subject

class SubjectAdapter(
    private val subjects: List<Subject>,
    private val onItemClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StudentItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(subjects[position])
    }
    
    override fun getItemCount() = subjects.size
    
    inner class ViewHolder(private val binding: StudentItemSubjectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subject: Subject) {
            binding.subjectTitle.text = subject.title
            binding.lessonsCount.text = "${subject.lessons.size} دروس ذكية"
            binding.pointsText.text = subject.points.toString()
            
            try {
                binding.colorIndicator.setBackgroundColor(Color.parseColor(subject.color))
            } catch (e: Exception) {
                binding.colorIndicator.setBackgroundColor(Color.parseColor("#6366f1"))
            }
            
            binding.root.setOnClickListener {
                onItemClick(subject)
            }
        }
    }
}
