package com.edu.student.ui.subject

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.R
import com.edu.teacher.databinding.*
import com.edu.student.StudentApp
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Lesson
import com.edu.student.services.TeacherClient
import com.edu.student.ui.common.LessonAdapter
import com.edu.student.ui.lesson.LessonActivity
import kotlinx.coroutines.*

class SubjectActivity : AppCompatActivity() {
    
    private lateinit var binding: StudentActivitySubjectBinding
    private lateinit var repository: StudentRepository
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val teacherClient: TeacherClient by lazy { StudentApp.getTeacherClient(this) }
    
    private var subjectId: String = ""
    private var subjectTitle: String = ""
    private var lessons: List<Lesson> = emptyList()
    private var lessonBroadcastListener: (() -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivitySubjectBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        
        subjectId = intent.getStringExtra("subject_id") ?: ""
        subjectTitle = intent.getStringExtra("subject_title") ?: ""
        
        setupViews()
        setupBroadcastListener()
        loadLessons()
    }
    
    private fun setupBroadcastListener() {
        lessonBroadcastListener = teacherClient.on("lesson_broadcast") { data ->
            try {
                val map = data as? Map<*, *> ?: return@on
                val lesson = map["lesson"] as? Lesson ?: return@on
                
                if (lesson.subjectId == subjectId) {
                    scope.launch(Dispatchers.Main) {
                        loadLessons()
                        Toast.makeText(this@SubjectActivity, "درس جديد: ${lesson.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        lessonBroadcastListener?.invoke()
        scope.cancel()
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
        scope.launch {
            val subjects = repository.getSubjects()
            val subject = subjects.find { it.id == subjectId }
            
            if (subject == null) {
                withContext(Dispatchers.Main) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.lessonsRecycler.visibility = View.GONE
                }
                return@launch
            }
            
            lessons = subject.lessons
            
            withContext(Dispatchers.Main) {
                binding.lessonsCount.text = "${lessons.size} درساً متاحاً"
                
                if (lessons.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.lessonsRecycler.visibility = View.GONE
                    return@withContext
                }
                
                binding.emptyView.visibility = View.GONE
                binding.lessonsRecycler.visibility = View.VISIBLE
                
                binding.lessonsRecycler.layoutManager = LinearLayoutManager(this@SubjectActivity)
                binding.lessonsRecycler.adapter = LessonAdapter(lessons, repository) { lesson ->
                    val intent = Intent(this@SubjectActivity, LessonActivity::class.java).apply {
                        putExtra("subject_id", subjectId)
                        putExtra("subject_title", subjectTitle)
                        putExtra("lesson_id", lesson.id)
                        putExtra("lesson_title", lesson.title)
                        putExtra("lesson_content", lesson.content)
                        putExtra("lesson_unit", lesson.unit)
                        putExtra("video_url", lesson.videoUrl ?: "")
                        putExtra("pdf_url", lesson.pdfUrl ?: "")
                        putExtra("lesson_images", ArrayList(lesson.images ?: emptyList()))
                        putExtra("lesson_pdf_path", lesson.pdfPath ?: "")
                        putExtra("lesson_video_path", lesson.videoPath ?: "")
                    }
                    startActivity(intent)
                }
            }
        }
    }
}
