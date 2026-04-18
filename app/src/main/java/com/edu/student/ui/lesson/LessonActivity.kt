package com.edu.student.ui.lesson

import android.content.Intent
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
import com.edu.student.StudentApp
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.AnswerSubmission
import com.edu.student.domain.model.Lesson
import com.edu.student.domain.model.Question
import com.edu.student.services.TeacherClient
import com.edu.student.ui.common.QuestionAdapter
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*

class LessonActivity : AppCompatActivity(), TextToSpeech.OnInitListener, TeacherClient.ClientCallback {
    
    private lateinit var binding: StudentActivityLessonBinding
    private lateinit var repository: StudentRepository
    private val teacherClient: TeacherClient by lazy { 
        StudentApp.getTeacherClient(this) 
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
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
    private var currentLesson: Lesson? = null
    private var homeworkSubmitted = false
    private var questionAnswers = mutableMapOf<String, String>()
    private var textAnswers = mutableMapOf<String, String>()
    private var imageAnswers = mutableMapOf<String, String>()
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleHomeworkImage(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        teacherClient.setCallback(this)
        tts = TextToSpeech(this, this)
        
        extractIntentData()
        setupViews()
        loadLessonData()
    }
    
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        teacherClient.setCallback(null)
        scope.cancel()
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
            if (questions.isNotEmpty()) {
                showHomeworkOptions()
            } else if (homeworkImageBase64 != null) {
                submitHomework()
            } else {
                Toast.makeText(this, "الرجاء التقاط صورة للحل أولاً", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.saveHomeworkButton.setOnClickListener {
            if (questions.isNotEmpty()) {
                saveHomeworkSolution()
            } else {
                Toast.makeText(this, "لا توجد أسئلة لحفظها", Toast.LENGTH_SHORT).show()
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
        scope.launch {
            binding.videoProgress.visibility = View.VISIBLE
            
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val url = URL(pdfUrl)
                    val connection = url.openConnection()
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val data = inputStream.readBytes()
                    inputStream.close()
                    data
                }
                
                val pdfFile = File(cacheDir, "lesson_${lessonId}.pdf")
                pdfFile.writeBytes(bytes)
                
                binding.videoProgress.visibility = View.GONE
                openPdfFile(pdfFile)
            } catch (e: Exception) {
                binding.videoProgress.visibility = View.GONE
                Toast.makeText(this@LessonActivity, "فشل في تحميل ملف PDF", Toast.LENGTH_SHORT).show()
            }
        }
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
            currentLesson = lesson
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
            binding.questionsRecycler.adapter = QuestionAdapter(questions, repository, lessonId) { questionId, answer ->
                saveAnswer(questionId, answer)
            }
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
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
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
        scope.launch(Dispatchers.Main) {
            if (homeworkSubmitted) {
                Toast.makeText(this@LessonActivity, "تم إرسال الواجب مسبقاً", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            binding.submitHomeworkButton.isEnabled = false
            binding.submitHomeworkButton.text = "جاري الإرسال..."
            
            val student = repository.getStudent()
            
            teacherClient.submitHomework(mapOf(
                "studentId" to (student?.id ?: ""),
                "studentName" to (student?.name ?: ""),
                "lessonId" to lessonId,
                "lessonTitle" to lessonTitle,
                "subjectTitle" to subjectTitle,
                "homeworkImage" to homeworkImageBase64,
                "homeworkNote" to "",
                "isCompleted" to true
            ))
            
            homeworkSubmitted = true
            repository.addPoints(lessonId, "homework_submit", 20)
            updatePoints()
            
            Toast.makeText(this@LessonActivity, "تم إرسال الواجب للمعلم!", Toast.LENGTH_LONG).show()
            binding.submitHomeworkButton.text = "تم الإرسال ✓"
        }
    }
    
    private fun showHomeworkOptions() {
        val options = arrayOf("إرسال للمعلم الآن", "حفظ لإرسال لاحقاً")
        
        AlertDialog.Builder(this)
            .setTitle("خيارات الواجب")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> submitHomeworkWithAnswers()
                    1 -> saveHomeworkSolution()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun submitHomeworkWithAnswers() {
        if (!teacherClient.isConnected) {
            Toast.makeText(this, "غير متصل بالمعلم، سيتم حفظ الواجب لإرساله لاحقاً", Toast.LENGTH_LONG).show()
            saveHomeworkSolution()
            return
        }
        
        scope.launch(Dispatchers.Main) {
            binding.submitHomeworkButton.isEnabled = false
            binding.submitHomeworkButton.text = "جاري الإرسال..."
            
            val answers = collectAnswers()
            val lesson = currentLesson ?: return@launch
            
            repository.saveHomeworkSolution(lesson.id, answers, lesson)
            
            val student = repository.getStudent()
            
            val answersJson = com.google.gson.Gson().toJson(answers)
            
            teacherClient.submitHomework(mapOf(
                "studentId" to (student?.id ?: ""),
                "studentName" to (student?.name ?: ""),
                "lessonId" to lessonId,
                "lessonTitle" to lessonTitle,
                "subjectTitle" to subjectTitle,
                "homeworkImage" to homeworkImageBase64,
                "homeworkNote" to "",
                "isCompleted" to true,
                "answers" to answersJson
            ))
            
            homeworkSubmitted = true
            repository.addPoints(lessonId, "homework_submit", 20)
            repository.removeHomeworkSolution(lessonId)
            updatePoints()
            
            Toast.makeText(this@LessonActivity, "تم إرسال الواجب للمعلم!", Toast.LENGTH_LONG).show()
            binding.submitHomeworkButton.text = "تم الإرسال ✓"
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
    
    override fun onConnected(ip: String) {}
    
    override fun onDisconnected() {}
    
    override fun onLessonsReceived(lessons: List<com.edu.student.domain.model.Lesson>) {}
    
    override fun onError(error: String) {}
    
    private fun saveHomeworkSolution() {
        val lesson = currentLesson ?: run {
            val lessons = repository.getSubjects().find { it.id == subjectId }?.lessons ?: emptyList()
            lessons.find { it.id == lessonId }?.also { currentLesson = it }
        } ?: return
        
        val answers = collectAnswers()
        
        repository.saveHomeworkSolution(lesson.id, answers, lesson)
        
        Toast.makeText(this, "تم حفظ الواجب. يمكنك إرساله لاحقاً من صفحة الإحصائيات", Toast.LENGTH_LONG).show()
    }
    
    private fun collectAnswers(): List<AnswerSubmission> {
        val answers = mutableListOf<AnswerSubmission>()
        
        for (question in questions) {
            val answer = AnswerSubmission(
                questionId = question.id,
                questionText = question.text,
                questionType = question.type,
                selectedAnswer = questionAnswers[question.id],
                correctAnswer = question.answer,
                isCorrect = (questionAnswers[question.id] == question.answer),
                textAnswer = textAnswers[question.id],
                imageAnswer = imageAnswers[question.id]
            )
            answers.add(answer)
        }
        
        return answers
    }
    
    fun saveAnswer(questionId: String, answer: String) {
        questionAnswers[questionId] = answer
    }
    
    fun saveTextAnswer(questionId: String, text: String) {
        textAnswers[questionId] = text
    }
    
    fun saveImageAnswer(questionId: String, imageBase64: String?) {
        if (imageBase64 != null) {
            imageAnswers[questionId] = imageBase64
        }
    }
}
