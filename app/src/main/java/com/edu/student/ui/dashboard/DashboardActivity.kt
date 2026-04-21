package com.edu.student.ui.dashboard

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.R
import com.edu.teacher.databinding.*
import com.edu.student.StudentApp
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Subject
import com.edu.student.services.TeacherClient
import com.edu.student.ai.SmartAssistant
import com.edu.student.ui.assistant.SmartAssistantBottomSheet
import com.edu.student.ui.common.SubjectAdapter
import com.edu.student.ui.settings.SettingsActivity
import com.edu.student.ui.stats.StatsActivity
import com.edu.student.ui.subject.SubjectActivity
import com.edu.student.utils.PermissionHelper
import kotlinx.coroutines.*
import java.io.File

class DashboardActivity : AppCompatActivity(), TeacherClient.ClientCallback {
    
    private lateinit var binding: StudentActivityDashboardBinding
    private lateinit var repository: StudentRepository
    private val teacherClient: TeacherClient by lazy { 
        StudentApp.getTeacherClient(this) 
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var smartAssistant: SmartAssistant
    private var refreshJob: Job? = null
    private var isSyncing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        checkRequiredPermissions()
        
        repository = StudentRepository(this)
        
        if (!repository.isLoggedIn()) {
            finish()
            return
        }
        
        teacherClient.setCallback(this)
        
setupViews()
        loadData()
        setupSmartAssistant()
    }
    
    private fun setupSmartAssistant() {
        smartAssistant = SmartAssistant(this)
        
        try {
            findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAssistant)?.setOnClickListener {
                SmartAssistantBottomSheet.show(
                    this,
                    smartAssistant,
                    "مساعدك الذكي",
                    "مرحباً! كيف يمكنني مساعدتك؟"
                )
            }
            smartAssistant.initialize()
        } catch (e: Exception) {
            // FAB not found or error
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        refreshJob?.cancel()
        scope.cancel()
        teacherClient.setCallback(null)
        smartAssistant.cleanup()
    }
    
    private fun setupViews() {
        val student = repository.getStudent()
        
        binding.welcomeText.text = "Welcome ${student?.name ?: "Student"}!"
        binding.studentIdText.text = student?.id ?: ""
        
        // Load avatar
        loadStudentAvatar()
        
        updateStats()
        
        binding.statsCard.setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        
        binding.bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                0 -> { }
                1 -> {
                    startActivity(Intent(this, StatsActivity::class.java))
                    finish()
                }
                2 -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }
            }
        }
    }
    
    private fun checkRequiredPermissions(): Boolean {
        return true
    }
    
    private fun updateStats() {
        val stats = repository.getStats()
        
        binding.starsCount.text = stats.stars.toString()
        binding.levelCount.text = stats.level.toString()
    }
    
    private fun loadStudentAvatar() {
        try {
            val prefs = StudentPreferences(this)
            val avatarPath = prefs.getAvatarPath()
            if (avatarPath != null) {
                val avatarFile = File(avatarPath)
                if (avatarFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(avatarPath)
                    binding.studentAvatar.setImageBitmap(bitmap)
                }
            }
        } catch (e: Exception) {
            // Use default avatar
        }
        
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
    
    override fun onConnected(ip: String) {
        scope.launch(Dispatchers.Main) {
            binding.statusBar.setBackgroundColor(Color.parseColor("#10B981"))
            binding.connectionStatus.text = "متصل بالرادار"
            binding.aiStatus.visibility = View.VISIBLE
            
            val student = repository.getStudent()
            if (student != null) {
                teacherClient.requestLessons(student.grade, student.section)
            }
        }
    }
    
    override fun onDisconnected() {
        scope.launch(Dispatchers.Main) {
            binding.statusBar.setBackgroundColor(Color.parseColor("#F59E0B"))
            binding.connectionStatus.text = "يبحث عن رادار المعلم..."
            isSyncing = false
        }
    }
    
    override fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>) {
        if (lessons.isNullOrEmpty()) return
        
        scope.launch {
            repository.saveLessons(lessons)
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    loadData()
                    Toast.makeText(this@DashboardActivity, "تم تحديث الدروس!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onError(error: String) {
        scope.launch(Dispatchers.Main) {
            isSyncing = false
        }
    }
}
