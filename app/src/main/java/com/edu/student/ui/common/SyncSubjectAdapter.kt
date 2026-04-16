package com.edu.student.ui.common

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.StudentItemSubjectBinding
import com.edu.student.domain.model.JoinedClass

class SyncSubjectAdapter(
    private val joinedClasses: List<JoinedClass>,
    private val onSyncClick: (JoinedClass) -> Unit
) : RecyclerView.Adapter<SyncSubjectAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = StudentItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(joinedClasses[position])
    }
    
    override fun getItemCount() = joinedClasses.size
    
    inner class ViewHolder(private val binding: StudentItemSubjectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(joinedClass: JoinedClass) {
            binding.subjectTitle.text = joinedClass.subject
            binding.lessonsCount.text = "آخر مزامنة: ${joinedClass.lastSync}"
            
            val statusColor = when (joinedClass.status) {
                "syncing" -> "#F59E0B"
                "active" -> "#10B981"
                "pending" -> "#6B7280"
                else -> "#6B7280"
            }
            
            try {
                binding.colorIndicator.setBackgroundColor(Color.parseColor(statusColor))
            } catch (e: Exception) {
                binding.colorIndicator.setBackgroundColor(Color.parseColor("#6366f1"))
            }
            
            binding.root.setOnClickListener {
                onSyncClick(joinedClass)
            }
            
            binding.pointsText.text = when (joinedClass.status) {
                "syncing" -> "جاري المزامنة..."
                "active" -> "متصل ✓"
                else -> "غير متصل"
            }
        }
    }
}
