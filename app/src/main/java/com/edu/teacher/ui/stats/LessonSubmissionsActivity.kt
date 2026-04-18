package com.edu.teacher.ui.stats 

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.DataManager
import com.edu.teacher.R
import com.edu.teacher.databinding.ActivityLessonSubmissionsBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.* 

class LessonSubmissionsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLessonSubmissionsBinding
    private lateinit var lesson: JSONObject
    private lateinit var submissions: JSONArray
    private lateinit var stats: JSONObject
    private lateinit var adapter: SubmissionsDetailAdapter
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar"))
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonSubmissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        val lessonStr = intent.getStringExtra("lesson") ?: "{}"
        val submissionsStr = intent.getStringExtra("submissions") ?: "[]"
        val statsStr = intent.getStringExtra("stats") ?: "{}"
        
        lesson = JSONObject(lessonStr)
        submissions = JSONArray(submissionsStr)
        stats = JSONObject(statsStr)
        
        setupViews()
        loadData()
    }
    
    private fun setupViews() {
        binding.backButton.setOnClickListener { finish() }
        binding.submissionsRecycler.layoutManager = LinearLayoutManager(this)
        
        adapter = SubmissionsDetailAdapter { submission, action ->
            when (action) {
                "view" -> showSubmissionDetails(submission)
                "grade" -> showGradeDialog(submission)
            }
        }
        binding.submissionsRecycler.adapter = adapter
    }
    
    private fun loadData() {
        val lessonTitle = lesson.optString("title", "درس")
        val className = lesson.optString("className", "")
        val unit = lesson.optString("unit", "")
        
        binding.lessonTitleText.text = lessonTitle
        binding.classInfoText.text = className
        binding.unitText.text = if (unit.isNotEmpty()) "الوحدة: $unit" else ""
        
        binding.totalSubmissionsText.text = "${stats.optInt("totalSubmissions", 0)}"
        binding.completedCountText.text = "${stats.optInt("completedCount", 0)}"
        binding.completionRateText.text = "${stats.optInt("completionRate", 0)}%"
        binding.averageScoreText.text = "${stats.optInt("averageScore", 0)}%"
        
        val submissionsList = mutableListOf<JSONObject>()
        for (i in 0 until submissions.length()) {
            submissionsList.add(submissions.getJSONObject(i))
        }
        
        if (submissionsList.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.submissionsRecycler.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.submissionsRecycler.visibility = View.VISIBLE
            adapter.submitList(submissionsList)
        }
    }
    
    private fun showSubmissionDetails(submission: JSONObject) {
        val studentName = submission.optString("studentName", "طالب")
        val grade = submission.optString("studentGrade", "")
        val section = submission.optString("studentSection", "")
        val timestamp = submission.optLong("timestamp", 0)
        val summary = submission.optJSONObject("summary") ?: JSONObject()
        val answers = submission.optJSONArray("answers") ?: JSONArray()
        val status = submission.optString("status", "pending")
        val finalScore = submission.optInt("finalScore", 0)
        val feedback = submission.optString("teacherFeedback", "")
        
        val dateStr = if (timestamp > 0) dateFormat.format(Date(timestamp)) else ""
        val autoScore = summary.optInt("percentage", 0)
        
        val message = buildString {
            append("الطالب: $studentName\n")
            append("الصف: $grade - $section\n")
            append("التاريخ: $dateStr\n\n")
            append("الدرجة التلقائية: $autoScore%\n")
            append("عدد الأسئلة: ${summary.optInt("totalQuestions", 0)}\n")
            append("الإجابات الصحيحة: ${summary.optInt("correctAnswers", 0)}\n")
            append("الإجابات الخاطئة: ${summary.optInt("wrongAnswers", 0)}\n")
            append("الأسئلة المقالية: ${summary.optInt("essayQuestions", 0)}\n")
            
            if (status == "graded") {
                append("\nالدرجة النهائية: $finalScore%\n")
                if (feedback.isNotEmpty()) {
                    append("ملاحظات المعلم: $feedback\n")
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("تفاصيل التسليم")
            .setMessage(message)
            .setPositiveButton("عرض الإجابات") { _, _ ->
                showAnswersDialog(answers)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }
    
    private fun showAnswersDialog(answers: JSONArray) {
        val answersList = mutableListOf<String>()
        for (i in 0 until answers.length()) {
            val answer = answers.getJSONObject(i)
            val qText = answer.optString("questionText", "سؤال ${i+1}")
            val qType = answer.optString("questionType", "")
            val selected = answer.optString("selectedAnswer", "")
            val correct = answer.optString("correctAnswer", "")
            val isCorrect = answer.optBoolean("isCorrect", false)
            val textAnswer = answer.optString("textAnswer", "")
            
            val answerText = when (qType) {
                "essay" -> "إجابة مقالية: ${textAnswer.ifEmpty { "لا يوجد" }}"
                else -> "إجابة الطالب: $selected | الصحيحة: $correct | ${if (isCorrect) "✅" else "❌"}"
            }
            answersList.add("${i+1}. $qText\n$answerText")
        }
        
        AlertDialog.Builder(this)
            .setTitle("إجابات الطالب")
            .setMessage(answersList.joinToString("\n\n"))
            .setPositiveButton("إغلاق", null)
            .show()
    }
    
    private fun showGradeDialog(submission: JSONObject) {
        val submissionId = submission.optString("submissionId", "")
        val summary = submission.optJSONObject("summary") ?: JSONObject()
        val autoScore = summary.optInt("percentage", 0)
        
        val editText = android.widget.EditText(this)
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editText.setText(autoScore.toString())
        
        AlertDialog.Builder(this)
            .setTitle("تصحيح الواجب")
            .setMessage("أدخل الدرجة (0-100)")
            .setView(editText)
            .setPositiveButton("حفظ") { _, _ ->
                val score = editText.text.toString().toIntOrNull() ?: autoScore
                val finalScore = score.coerceIn(0, 100)
                
                val feedbackEdit = android.widget.EditText(this)
                feedbackEdit.hint = "ملاحظات (اختياري)"
                
                AlertDialog.Builder(this)
                    .setTitle("ملاحظات")
                    .setView(feedbackEdit)
                    .setPositiveButton("حفظ") { _, _ ->
                        val teacherId = DataManager.getTeacherId(this) ?: ""
                        DataManager.gradeSubmission(this, teacherId, submissionId, finalScore, feedbackEdit.text.toString())
                        Toast.makeText(this, "تم حفظ التصحيح", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .setNegativeButton("تخطي") { _, _ ->
                        val teacherId = DataManager.getTeacherId(this) ?: ""
                        DataManager.gradeSubmission(this, teacherId, submissionId, finalScore, "")
                        Toast.makeText(this, "تم حفظ التصحيح", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}