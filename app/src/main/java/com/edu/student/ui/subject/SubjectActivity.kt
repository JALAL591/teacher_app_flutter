package com.edu.student.ui.subject

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.R
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Lesson
import com.edu.student.ui.common.LessonAdapter
import com.edu.student.ui.lesson.LessonActivity

class SubjectActivity : AppCompatActivity() {
    
    private lateinit var binding: StudentActivitySubjectBinding
    private lateinit var repository: StudentRepository
    
    private var subjectId: String = ""
    private var subjectTitle: String = ""
    private var lessons: List<Lesson> = emptyList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivitySubjectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        
        subjectId = intent.getStringExtra("subject_id") ?: ""
        subjectTitle = intent.getStringExtra("subject_title") ?: ""
        
        setupViews()
        loadLessons()
    }
    
    override fun onResume() {
        super.onResume()
        loadLessons()
    }
    
    private fun setupViews() {
        binding.subjectTitle.text = subjectTitle
        
        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.lessonsCount.text = "0 درساً متاحاً"
    }
    
    private fun loadLessons() {
        val subjects = repository.getSubjects()
        val subject = subjects.find { it.id == subjectId }
        
        if (subject == null) {
            binding.emptyView.visibility = View.VISIBLE
            binding.lessonsRecycler.visibility = View.GONE
            return
        }
        
        lessons = subject.lessons
        binding.lessonsCount.text = "${lessons.size} درساً متاحاً"
        
        if (lessons.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.lessonsRecycler.visibility = View.GONE
            return
        }
        
        binding.emptyView.visibility = View.GONE
        binding.lessonsRecycler.visibility = View.VISIBLE
        
        binding.lessonsRecycler.layoutManager = LinearLayoutManager(this)
        binding.lessonsRecycler.adapter = LessonAdapter(lessons, repository) { lesson ->
            val intent = Intent(this, LessonActivity::class.java).apply {
                putExtra("subject_id", subjectId)
                putExtra("subject_title", subjectTitle)
                putExtra("lesson_id", lesson.id)
                putExtra("lesson_title", lesson.title)
                putExtra("lesson_content", lesson.content)
                putExtra("lesson_unit", lesson.unit)
                putExtra("video_url", lesson.videoUrl ?: "")
                putExtra("pdf_url", lesson.pdfUrl ?: "")
            }
            startActivity(intent)
        }
    }
}
