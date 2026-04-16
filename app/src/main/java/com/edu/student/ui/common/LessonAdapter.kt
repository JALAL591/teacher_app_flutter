package com.edu.student.ui.common

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Lesson

class LessonAdapter(
    private val lessons: List<Lesson>,
    private val repository: StudentRepository,
    private val onItemClick: (Lesson) -> Unit
) : RecyclerView.Adapter<LessonAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StudentItemLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(lessons[position], position)
    }
    
    override fun getItemCount() = lessons.size
    
    inner class ViewHolder(private val binding: StudentItemLessonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(lesson: Lesson, position: Int) {
            binding.lessonNumber.text = (position + 1).toString()
            binding.lessonTitle.text = lesson.title
            binding.lessonUnit.text = lesson.unit
            
            val isDone = repository.isActionCompleted(lesson.id, "read")
            if (isDone) {
                binding.checkIcon.visibility = View.VISIBLE
                binding.lessonNumber.visibility = View.GONE
                binding.root.alpha = 0.7f
            } else {
                binding.checkIcon.visibility = View.GONE
                binding.lessonNumber.visibility = View.VISIBLE
                binding.root.alpha = 1f
            }
            
            binding.root.setOnClickListener {
                onItemClick(lesson)
            }
        }
    }
}
