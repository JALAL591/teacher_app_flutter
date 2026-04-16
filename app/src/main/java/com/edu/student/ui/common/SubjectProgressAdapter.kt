package com.edu.student.ui.common

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Subject

class SubjectProgressAdapter(
    private val subjects: List<Subject>,
    private val repository: StudentRepository
) : RecyclerView.Adapter<SubjectProgressAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StudentItemSubjectProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(subjects[position])
    }
    
    override fun getItemCount() = subjects.size
    
    inner class ViewHolder(private val binding: StudentItemSubjectProgressBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subject: Subject) {
            binding.subjectTitle.text = subject.title
            
            val lessons = subject.lessons
            val completedCount = lessons.count { lesson ->
                repository.isActionCompleted(lesson.id, "read") ||
                repository.isActionCompleted(lesson.id, "final_submit")
            }
            
            binding.lessonsProgress.text = "$completedCount من ${lessons.size} دروس"
            
            val progress = if (lessons.isNotEmpty()) {
                (completedCount * 100) / lessons.size
            } else 0
            
            binding.progressBar.progress = progress
            binding.progressPercent.text = "$progress%"
        }
    }
}
