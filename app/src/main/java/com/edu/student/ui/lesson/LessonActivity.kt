package com.edu.student.ui.lesson

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Lesson
import com.edu.student.domain.model.Question
import com.edu.student.services.SyncService
import com.edu.student.ui.common.QuestionAdapter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*

class LessonActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    
    private lateinit var binding: StudentActivityLessonBinding
    private lateinit var repository: StudentRepository
    private lateinit var syncService: SyncService
    
    private var subjectId = ""
    private var subjectTitle = ""
    private var lessonId = ""
    private var lessonTitle = ""
    private var lessonContent = ""
    private var lessonUnit = ""
    private var videoUrl = ""
    private var pdfUrl = ""
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    private var homeworkImageBase64: String? = null
    private var questions: List<Question> = emptyList()
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleHomeworkImage(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        syncService = SyncService(this)
        tts = TextToSpeech(this, this)
        
        extractIntentData()
        setupViews()
        loadLessonData()
    }
    
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
    
    private fun extractIntentData() {
        subjectId = intent.getStringExtra("subject_id") ?: ""
        subjectTitle = intent.getStringExtra("subject_title") ?: ""
        lessonId = intent.getStringExtra("lesson_id") ?: ""
        lessonTitle = intent.getStringExtra("lesson_title") ?: ""
        lessonContent = intent.getStringExtra("lesson_content") ?: ""
        lessonUnit = intent.getStringExtra("lesson_unit") ?: "الوحدة الأولى"
        videoUrl = intent.getStringExtra("video_url") ?: ""
        pdfUrl = intent.getStringExtra("pdf_url") ?: ""
    }
    
    private fun setupViews() {
        binding.lessonTitle.text = lessonTitle
        binding.subjectName.text = subjectTitle
        
        val student = repository.getStudent()
        binding.pointsText.text = "${student?.points ?: 0}"
        
        binding.backButton.setOnClickListener { finish() }
        
        setupVideoPlayer()
        setupPdfViewer()
        
        binding.contentTab.setOnClickListener {
            binding.contentTab.isSelected = true
            binding.homeworkTab.isSelected = false
            binding.contentLayout.visibility = View.VISIBLE
            binding.homeworkLayout.visibility = View.GONE
        }
        
        binding.homeworkTab.setOnClickListener {
            binding.contentTab.isSelected = false
            binding.homeworkTab.isSelected = true
            binding.contentLayout.visibility = View.GONE
            binding.homeworkLayout.visibility = View.VISIBLE
        }
        
        binding.readButton.setOnClickListener {
            markAsRead()
        }
        
        binding.readAloudButton.setOnClickListener {
            speakContent()
        }
        
        binding.submitHomeworkButton.setOnClickListener {
            if (homeworkImageBase64 != null) {
                submitHomework()
            } else {
                Toast.makeText(this, "الرجاء التقاط صورة للحل أولاً", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.captureHomeworkButton.setOnClickListener {
            pickImage.launch("image/*")
        }
        
        updateReadButton()
    }
    
    private fun setupVideoPlayer() {
        if (videoUrl.isNotEmpty()) {
            binding.videoCard.visibility = View.VISIBLE
            
            try {
                val mediaController = MediaController(this)
                mediaController.setAnchorView(binding.videoView)
                binding.videoView.setMediaController(mediaController)
                
                binding.videoView.setVideoURI(Uri.parse(videoUrl))
                
                binding.videoView.setOnPreparedListener { mp ->
                    binding.videoProgress.visibility = View.GONE
                    mp.isLooping = false
                }
                
                binding.videoView.setOnErrorListener { _, _, _ ->
                    binding.videoProgress.visibility = View.GONE
                    Toast.makeText(this, "فشل في تحميل الفيديو", Toast.LENGTH_SHORT).show()
                    true
                }
                
                binding.videoProgress.visibility = View.VISIBLE
                binding.videoView.start()
                
            } catch (e: Exception) {
                binding.videoCard.visibility = View.GONE
                Toast.makeText(this, "لا يمكن تشغيل الفيديو", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.videoCard.visibility = View.GONE
        }
    }
    
    private fun setupPdfViewer() {
        if (pdfUrl.isNotEmpty()) {
            binding.pdfCard.visibility = View.VISIBLE
            
            binding.openPdfButton.setOnClickListener {
                loadAndOpenPdf()
            }
        } else {
            binding.pdfCard.visibility = View.GONE
        }
    }
    
    private fun loadAndOpenPdf() {
        binding.videoProgress.visibility = View.VISIBLE
        
        Thread {
            try {
                val url = URL(pdfUrl)
                val connection = url.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                val bytes = inputStream.readBytes()
                inputStream.close()
                
                val pdfFile = File(cacheDir, "lesson_${lessonId}.pdf")
                pdfFile.writeBytes(bytes)
                
                runOnUiThread {
                    binding.videoProgress.visibility = View.GONE
                    openPdfFile(pdfFile)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.videoProgress.visibility = View.GONE
                    Toast.makeText(this, "فشل في تحميل ملف PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun openPdfFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "لا يوجد تطبيق لفتح ملفات PDF", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في فتح الملف", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadLessonData() {
        val lessons = repository.getSubjects()
            .find { it.id == subjectId }?.lessons ?: emptyList()
        
        val lesson = lessons.find { it.id == lessonId }
        if (lesson != null) {
            if (lessonContent.isEmpty()) {
                lessonContent = lesson.content
            }
            if (videoUrl.isEmpty()) {
                videoUrl = lesson.videoUrl ?: ""
            }
            if (pdfUrl.isEmpty()) {
                pdfUrl = lesson.pdfUrl ?: ""
            }
            questions = lesson.questions
            
            if (videoUrl.isNotEmpty()) {
                binding.videoCard.visibility = View.VISIBLE
                setupVideoPlayer()
            }
            if (pdfUrl.isNotEmpty()) {
                binding.pdfCard.visibility = View.VISIBLE
            }
        }
        
        binding.lessonContent.text = lessonContent.ifEmpty { lessonContent }
        
        if (questions.isNotEmpty()) {
            binding.questionsRecycler.visibility = View.VISIBLE
            binding.questionsRecycler.layoutManager = LinearLayoutManager(this)
            binding.questionsRecycler.adapter = QuestionAdapter(questions, repository, lessonId)
        }
    }
    
    private fun updateReadButton() {
        val isRead = repository.isActionCompleted(lessonId, "read")
        if (isRead) {
            binding.readButton.text = "أحسنت! تمت القراءة ✓"
            binding.readButton.isEnabled = false
        } else {
            binding.readButton.text = "أنهيت القراءة +10 🌟"
            binding.readButton.isEnabled = true
        }
    }
    
    private fun markAsRead() {
        repository.addPoints(lessonId, "read", 10)
        updateReadButton()
        updatePoints()
        Toast.makeText(this, "+10 نجوم! 🌟", Toast.LENGTH_SHORT).show()
    }
    
    private fun updatePoints() {
        val student = repository.getStudent()
        binding.pointsText.text = "${student?.points ?: 0}"
    }
    
    private fun speakContent() {
        if (!isTtsReady) {
            Toast.makeText(this, "الذكاء الصوتي غير جاهز", Toast.LENGTH_SHORT).show()
            return
        }
        
        val text = lessonContent.take(1000)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lesson_content")
    }
    
    private fun handleHomeworkImage(uri: Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            homeworkImageBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT)
            
            binding.homeworkPreview.setImageBitmap(bitmap)
            binding.homeworkPreview.visibility = View.VISIBLE
            binding.captureHomeworkButton.text = "تغيير الصورة"
            
            Toast.makeText(this, "تم التقاط صورة الحل", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "فشل في معالجة الصورة", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun submitHomework() {
        binding.submitHomeworkButton.isEnabled = false
        binding.submitHomeworkButton.text = "جاري الإرسال..."
        
        syncService.submitHomework(
            lessonId = lessonId,
            lessonTitle = lessonTitle,
            subjectName = subjectTitle,
            homeworkImage = homeworkImageBase64,
            homeworkNote = "",
            isCompleted = true
        ) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "✅ تم إرسال الواجب بنجاح!", Toast.LENGTH_LONG).show()
                    repository.addPoints(lessonId, "homework_submit", 20)
                    updatePoints()
                } else {
                    Toast.makeText(this, "❌ فشل الإرسال", Toast.LENGTH_SHORT).show()
                }
                binding.submitHomeworkButton.isEnabled = true
                binding.submitHomeworkButton.text = "إرسال الواجب للمعلم 📤"
            }
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("ar", "SA"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }
}
