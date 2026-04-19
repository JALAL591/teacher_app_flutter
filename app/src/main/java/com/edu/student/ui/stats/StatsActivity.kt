package com.edu.student.ui.stats

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.R
import com.edu.teacher.databinding.*
import com.edu.student.StudentApp
import com.edu.student.data.repository.StudentRepository
import com.edu.student.services.TeacherClient
import com.edu.student.ai.SmartAssistant
import com.edu.student.ui.assistant.SmartAssistantBottomSheet
import com.edu.student.ui.common.SubjectProgressAdapter
import com.edu.student.ui.dashboard.DashboardActivity
import com.edu.student.ui.settings.SettingsActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class StatsActivity : AppCompatActivity() {
    
    private lateinit var binding: StudentActivityStatsBinding
    private lateinit var repository: StudentRepository
    private lateinit var teacherClient: TeacherClient
    private val homeworkSolutions = mutableListOf<JSONObject>()
    private lateinit var adapter: HomeworkStatsAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var smartAssistant: SmartAssistant
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        teacherClient = StudentApp.getTeacherClient(this)
        
        setupViews()
        loadHomeworkSolutions()
        setupTeacherCallbacks()
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
                    "مساعد الواجبات",
                    "إحصائيات واجباتك:"
                )
            }
            smartAssistant.initialize()
        } catch (e: Exception) {
            // FAB not found
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        smartAssistant.cleanup()
        scope.cancel()
    }
    
    override fun onResume() {
        super.onResume()
        loadHomeworkSolutions()
    }
    
    private fun setupViews() {
        binding.backButton.setOnClickListener { finish() }
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
                R.id.nav_stats -> { }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    finish()
                }
            }
            true
        }
        binding.bottomNav.selectedItemId = R.id.nav_stats
        
        binding.homeworkRecycler.layoutManager = LinearLayoutManager(this)
        
        adapter = HomeworkStatsAdapter { solution ->
            showHomeworkDetails(solution)
        }
        binding.homeworkRecycler.adapter = adapter
        
        binding.submitAllButton.setOnClickListener {
            submitAllHomework()
        }
    }
    
    private fun setupTeacherCallbacks() {
        teacherClient.setCallback(object : TeacherClient.ClientCallback {
            override fun onConnected(ip: String) {
                runOnUiThread {
                    binding.connectionStatus.text = "متصل بالمعلم"
                    binding.connectionStatus.setTextColor(getColor(R.color.success))
                    binding.submitAllButton.isEnabled = true
                }
            }
            
            override fun onDisconnected() {
                runOnUiThread {
                    binding.connectionStatus.text = "غير متصل بالمعلم"
                    binding.connectionStatus.setTextColor(getColor(R.color.text_secondary))
                    binding.submitAllButton.isEnabled = false
                }
            }
            
            override fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>) {
            }
            
            override fun onError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@StatsActivity, error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    private fun loadHomeworkSolutions() {
        homeworkSolutions.clear()
        homeworkSolutions.addAll(repository.getAllHomeworkSolutions())
        
        if (homeworkSolutions.isEmpty()) {
            binding.homeworkEmptyView.visibility = View.VISIBLE
            binding.homeworkRecycler.visibility = View.GONE
            binding.submitAllButton.visibility = View.GONE
        } else {
            binding.homeworkEmptyView.visibility = View.GONE
            binding.homeworkRecycler.visibility = View.VISIBLE
            binding.submitAllButton.visibility = View.VISIBLE
            
            adapter.submitList(homeworkSolutions)
            
            val count = homeworkSolutions.size
            binding.submitAllButton.text = "إرسال الكل ($count)"
        }
    }
    
    private fun loadData() {
        scope.launch {
            val student = repository.getStudent()
            val stats = repository.getStats()
            val subjects = repository.getSubjects()
            
            withContext(Dispatchers.Main) {
                binding.studentName.text = student?.name ?: "طالب"
                
                binding.starsCount.text = stats.stars.toString()
                binding.levelCount.text = stats.level.toString()
                
                val progressPercent = stats.stars % 100
                binding.progressBar.progress = progressPercent
                binding.progressText.text = "التقدم: $progressPercent%"
                
                binding.completedCount.text = stats.completedCount.toString()
                
                if (subjects.isEmpty()) {
                    binding.subjectsList.visibility = View.GONE
                    binding.emptyText.visibility = View.VISIBLE
                } else {
                    binding.subjectsList.visibility = View.VISIBLE
                    binding.emptyText.visibility = View.GONE
                    
                    binding.subjectsList.layoutManager = LinearLayoutManager(this@StatsActivity)
                    binding.subjectsList.adapter = SubjectProgressAdapter(subjects, repository)
                }
            }
        }
    }
    
    private fun showHomeworkDetails(solution: JSONObject) {
        val lessonTitle = solution.optString("lessonTitle", "درس")
        val subjectTitle = solution.optString("subjectTitle", "")
        val summary = solution.optJSONObject("summary")
        val percentage = summary?.optInt("percentage", 0) ?: 0
        val timestamp = solution.optLong("timestamp", 0)
        val dateStr = if (timestamp > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar")).format(Date(timestamp))
        } else ""
        
        val message = buildString {
            append("المادة: $subjectTitle\n")
            append("الدرس: $lessonTitle\n")
            append("التاريخ: $dateStr\n")
            append("النتيجة: $percentage%\n")
            append("الإجابات الصحيحة: ${summary?.optInt("correctAnswers", 0) ?: 0}/${summary?.optInt("totalQuestions", 0) ?: 0}")
        }
        
        AlertDialog.Builder(this)
            .setTitle("تفاصيل الواجب")
            .setMessage(message)
            .setPositiveButton("إرسال للمعلم") { _, _ ->
                submitSingleHomework(solution)
            }
            .setNegativeButton("عرض الإجابات") { _, _ ->
                showAnswersDialog(solution.optJSONArray("answers"))
            }
            .setNeutralButton("إلغاء", null)
            .show()
    }
    
    private fun showAnswersDialog(answersArray: JSONArray?) {
        if (answersArray == null) return
        
        val answersList = mutableListOf<String>()
        for (i in 0 until answersArray.length()) {
            val answer = answersArray.getJSONObject(i)
            val qText = answer.optString("questionText", "سؤال ${i+1}")
            val selected = answer.optString("selectedAnswer", "")
            val isCorrect = answer.optBoolean("isCorrect", false)
            val textAnswer = answer.optString("textAnswer", "")
            val hasImage = answer.optString("imageAnswer", "").isNotEmpty()
            
            val answerText = when {
                textAnswer.isNotEmpty() -> "إجابة: $textAnswer ${if (hasImage) "📷" else ""}"
                selected.isNotEmpty() -> "إجابة: $selected ${if (isCorrect) "✅" else "❌"}"
                else -> "لم يجب"
            }
            answersList.add("${i+1}. $qText\n$answerText")
        }
        
        AlertDialog.Builder(this)
            .setTitle("إجابات الطالب")
            .setMessage(answersList.joinToString("\n\n"))
            .setPositiveButton("إغلاق", null)
            .show()
    }
    
    private fun submitSingleHomework(solution: JSONObject) {
        if (!teacherClient.isConnected) {
            Toast.makeText(this, "غير متصل بالمعلم", Toast.LENGTH_SHORT).show()
            return
        }
        
        val lessonId = solution.optString("lessonId", "")
        val lessonTitle = solution.optString("lessonTitle", "")
        
        AlertDialog.Builder(this)
            .setTitle("تأكيد الإرسال")
            .setMessage("هل تريد إرسال حل واجب '$lessonTitle' إلى المعلم؟")
            .setPositiveButton("إرسال") { _, _ ->
                showLoading(true)
                
                teacherClient.submitSavedHomework(lessonId,
                    onSuccess = {
                        runOnUiThread {
                            showLoading(false)
                            repository.removeHomeworkSolution(lessonId)
                            loadHomeworkSolutions()
                            Toast.makeText(this, "تم إرسال الواجب بنجاح ✅", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            showLoading(false)
                            Toast.makeText(this, "فشل الإرسال: $error", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun submitAllHomework() {
        if (!teacherClient.isConnected) {
            Toast.makeText(this, "غير متصل بالمعلم", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (homeworkSolutions.isEmpty()) {
            Toast.makeText(this, "لا توجد واجبات للإرسال", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("تأكيد الإرسال")
            .setMessage("هل تريد إرسال جميع الواجبات (${homeworkSolutions.size}) إلى المعلم؟")
            .setPositiveButton("إرسال الكل") { _, _ ->
                submitAllHomeworkSequentially()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun submitAllHomeworkSequentially() {
        showLoading(true)
        
        var successCount = 0
        var failCount = 0
        
        fun submitNext(index: Int) {
            if (index >= homeworkSolutions.size) {
                runOnUiThread {
                    showLoading(false)
                    loadHomeworkSolutions()
                    Toast.makeText(this, "تم إرسال $successCount واجب بنجاح ${if (failCount > 0) "، فشل $failCount" else ""}", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            val solution = homeworkSolutions[index]
            val lessonId = solution.optString("lessonId", "")
            
            teacherClient.submitSavedHomework(lessonId,
                onSuccess = {
                    successCount++
                    repository.removeHomeworkSolution(lessonId)
                    submitNext(index + 1)
                },
                onError = {
                    failCount++
                    submitNext(index + 1)
                }
            )
        }
        
        submitNext(0)
    }
    
    private fun showLoading(show: Boolean) {
        binding.homeworkProgressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.submitAllButton.isEnabled = !show
    }
}