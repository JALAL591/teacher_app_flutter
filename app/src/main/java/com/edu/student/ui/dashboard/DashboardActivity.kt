package com.edu.student.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Subject
import com.edu.student.domain.model.StudentStats
import com.edu.student.services.TeacherClient
import com.edu.student.services.SyncService
import com.edu.student.ui.common.SubjectAdapter
import com.edu.student.ui.lesson.LessonActivity
import com.edu.student.ui.settings.SettingsActivity
import com.edu.student.ui.stats.StatsActivity
import com.edu.student.ui.subject.SubjectActivity
import kotlinx.coroutines.*

class DashboardActivity : AppCompatActivity(), TeacherClient.ClientCallback {
    
    private lateinit var binding: StudentActivityDashboardBinding
    private lateinit var repository: StudentRepository
    private lateinit var teacherClient: TeacherClient
    private lateinit var syncService: SyncService
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var refreshJob: Job? = null
    private var isSyncing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        
        if (!repository.isLoggedIn()) {
            finish()
            return
        }
        
        teacherClient = TeacherClient(this)
        teacherClient.setCallback(this)
        syncService = SyncService(this)
        
        setupViews()
        loadData()
        initTeacherConnection()
    }
    
    override fun onResume() {
        super.onResume()
        loadData()
    }
    
    private fun syncWithTeacher() {
        if (isSyncing) return
        isSyncing = true
        
        syncService.syncLessons(object : SyncService.SyncCallback {
            override fun onSuccess(lessons: List<com.edu.student.domain.model.Lesson>) {
                repository.saveLessons(lessons)
                runOnUiThread {
                    isSyncing = false
                    loadData()
                    Toast.makeText(this@DashboardActivity, "Sync complete!", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onError(error: String) {
                isSyncing = false
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        refreshJob?.cancel()
        scope.cancel()
        teacherClient.destroy()
    }
    
    private fun setupViews() {
        val student = repository.getStudent()
        
        binding.welcomeText.text = "Welcome ${student?.name ?: "Student"}!"
        binding.studentIdText.text = student?.id ?: ""
        
        updateStats()
        
        binding.statsCard.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                com.edu.teacher.R.id.nav_home -> { }
                com.edu.teacher.R.id.nav_stats -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    finish()
                }
                com.edu.teacher.R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }
            }
            true
        }
    }
    
    private fun updateStats() {
        val stats = repository.getStats()
        
        binding.starsCount.text = stats.stars.toString()
        binding.levelCount.text = stats.level.toString()
        
        val subjects = repository.getSubjects()
        binding.subjectsCount.text = subjects.size.toString()
    }
    
    private fun loadData() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            try {
                val subjects = repository.getSubjects()
                updateSubjectsList(subjects)
                updateStats()
            } catch (e: Exception) {
                Toast.makeText(this@DashboardActivity, "Error loading data", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateSubjectsList(subjects: List<Subject>) {
        if (subjects.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.subjectsRecycler.visibility = View.GONE
            return
        }
        
        binding.emptyView.visibility = View.GONE
        binding.subjectsRecycler.visibility = View.VISIBLE
        
        binding.subjectsRecycler.layoutManager = LinearLayoutManager(this)
        binding.subjectsRecycler.adapter = SubjectAdapter(subjects) { subject ->
            val intent = Intent(this, SubjectActivity::class.java).apply {
                putExtra("subject_id", subject.id)
                putExtra("subject_title", subject.title)
            }
            startActivity(intent)
        }
    }
    
    private fun initTeacherConnection() {
        teacherClient.init()
    }
    
    override fun onConnected(ip: String) {
        runOnUiThread {
            // Connected - green background
            binding.statusBar.setBackgroundColor(Color.parseColor("#10B981"))
            binding.connectionStatus.text = "Connected to Teacher Radar"
            
            binding.aiStatus.visibility = View.VISIBLE
            
            // Request lessons from teacher
            val student = repository.getStudent()
            if (student != null) {
                teacherClient.requestLessons(student.grade, student.section)
            }
            
            // One-time sync when connected
            syncWithTeacher()
        }
    }
    
    override fun onDisconnected() {
        runOnUiThread {
            // Searching - orange background
            binding.statusBar.setBackgroundColor(Color.parseColor("#F59E0B"))
            binding.connectionStatus.text = "Searching for Teacher Radar..."
            // Reset sync flag to allow sync on next connection
            isSyncing = false
        }
    }
    
    override fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>) {
        repository.saveLessons(lessons)
        runOnUiThread {
            loadData()
            Toast.makeText(this, "Lessons updated!", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onError(error: String) {
        runOnUiThread {
            isSyncing = false
        }
    }
}
