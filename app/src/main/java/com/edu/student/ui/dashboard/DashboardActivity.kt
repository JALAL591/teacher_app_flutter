package com.edu.student.ui.dashboard

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Subject
import com.edu.student.services.TeacherClient
import com.edu.student.ui.common.SubjectAdapter
import com.edu.student.ui.settings.SettingsActivity
import com.edu.student.ui.stats.StatsActivity
import com.edu.student.ui.subject.SubjectActivity
import com.edu.student.utils.PermissionHelper
import kotlinx.coroutines.*

class DashboardActivity : AppCompatActivity(), TeacherClient.ClientCallback {
    
    private lateinit var binding: StudentActivityDashboardBinding
    private lateinit var repository: StudentRepository
    private lateinit var teacherClient: TeacherClient
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var refreshJob: Job? = null
    private var isSyncing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        checkPermissions()
        
        repository = StudentRepository(this)
        
        if (!repository.isLoggedIn()) {
            finish()
            return
        }
        
        teacherClient = TeacherClient(this)
        teacherClient.setCallback(this)
        
        setupViews()
        loadData()
        initTeacherConnection()
    }
    
    private fun checkPermissions() {
        if (!PermissionHelper.hasAllPermissions(this)) {
            PermissionHelper.requestPermissions(this)
        }
        
        if (!PermissionHelper.hasNotificationPermission(this)) {
            PermissionHelper.requestNotificationPermission(this)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        PermissionHelper.onRequestPermissionsResult(
            requestCode,
            grantResults,
            onGranted = {
                Toast.makeText(this, "تم منح جميع الصلاحيات", Toast.LENGTH_SHORT).show()
            },
            onDenied = { denied ->
                Toast.makeText(
                    this,
                    "بعض الصلاحيات مرفوضة: ${denied.joinToString(", ")}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    
    override fun onResume() {
        super.onResume()
        loadData()
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
