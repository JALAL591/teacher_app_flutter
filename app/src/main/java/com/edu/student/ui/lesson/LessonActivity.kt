package com.edu.student.ui.lesson

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.app.Dialog
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.databinding.*
import com.edu.teacher.R
import com.edu.student.StudentApp
import com.edu.student.data.repository.StudentRepository
import com.edu.student.data.LessonStorage
import com.edu.student.domain.model.AnswerSubmission
import org.json.JSONObject
import com.edu.student.domain.model.Lesson
import com.edu.student.domain.model.Question
import com.edu.student.services.TeacherClient
import com.edu.student.services.TeacherClient.SavedAttachments
import com.edu.student.ui.common.ImageAdapter
import com.edu.student.ui.common.QuestionAdapter
import com.edu.student.ai.SmartAssistant
import com.edu.student.ui.assistant.SmartAssistantBottomSheet
import com.edu.student.utils.PdfTextExtractor
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*

class LessonActivity : AppCompatActivity(), TextToSpeech.OnInitListener, TeacherClient.ClientCallback {
    
    private val TAG = "LessonActivity"
    private val gson = Gson()

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
    private var lessonImages: List<String> = emptyList()
    private var lessonPdfPath: String? = null
    private var lessonVideoPath: String? = null
    private var isBroadcastLesson = false
    private lateinit var smartAssistant: SmartAssistant
    private lateinit var fabAssistant: FloatingActionButton
    private var pdfView: PDFView? = null
    
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
        
        initSmartAssistant()
        PdfTextExtractor.initTesseract(this)
        extractIntentData()
        setupViews()
        loadBroadcastAttachments()
        setupBroadcastListener()
        displayAttachments()
        loadLessonData()
    }
    
    private var broadcastListenerCleanup: (() -> Unit)? = null
    
    private fun setupBroadcastListener() {
        broadcastListenerCleanup = teacherClient.on("lesson_broadcast") { data ->
            try {
                val map = data as? Map<*, *>
                val lesson = map?.get("lesson") as? Lesson
                val attachments = map?.get("attachments") as? SavedAttachments
                
                lesson?.let {
                    Log.e(TAG, "lesson_broadcast received")
                    Log.e(TAG, "Images: ${it.images?.size ?: 0}")
                    Log.e(TAG, "PDF: ${it.pdfPath}")
                    Log.e(TAG, "Video: ${it.videoPath}")
                    
                    runOnUiThread {
                        updateLessonFromBroadcast(it, attachments)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling lesson_broadcast: ${e.message}")
            }
        }
    }
    
    private fun updateLessonFromBroadcast(lesson: Lesson, attachments: SavedAttachments?) {
        // ✅ ربط الوسائط بالدرس المحدد فقط
        if (lesson.id != lessonId) {
            Log.d(TAG, "Ignoring lesson ${lesson.id} - not current lesson $lessonId")
            return
        }
        
        currentLesson = lesson
        
        if (attachments != null) {
            if (attachments.images.isNotEmpty()) {
                lessonImages = attachments.images
            }
            if (!attachments.pdfPath.isNullOrEmpty()) {
                lessonPdfPath = attachments.pdfPath
            }
            if (!attachments.videoPath.isNullOrEmpty()) {
                lessonVideoPath = attachments.videoPath
            }
        } else {
            val images = lesson.images
            if (!images.isNullOrEmpty()) {
                lessonImages = images.filterNotNull()
            }
            val pdfPath = lesson.pdfPath
            if (!pdfPath.isNullOrEmpty()) {
                lessonPdfPath = pdfPath
            }
            val videoPath = lesson.videoPath
            if (!videoPath.isNullOrEmpty()) {
                lessonVideoPath = videoPath
            }
        }
        
        Log.e(TAG, "Updating lesson ${lesson.id} with media:")
        Log.e(TAG, "lessonImages: ${lessonImages.size}")
        Log.e(TAG, "lessonPdfPath: $lessonPdfPath")
        Log.e(TAG, "lessonVideoPath: $lessonVideoPath")
        
        displayAttachments()
    }
    
    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        teacherClient.setCallback(null)
        smartAssistant.cleanup()
        scope.cancel()
        broadcastListenerCleanup?.invoke()
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
        
        val imagesList = intent.getStringArrayListExtra("lesson_images")
        if (imagesList != null) {
            lessonImages = imagesList
        }
        lessonPdfPath = intent.getStringExtra("lesson_pdf_path")
        lessonVideoPath = intent.getStringExtra("lesson_video_path")
        
        isBroadcastLesson = intent.getBooleanExtra("is_broadcast_lesson", false)
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
    
    private fun initSmartAssistant() {
        smartAssistant = SmartAssistant(this)
        
        fabAssistant = findViewById(R.id.fabAssistant)
        fabAssistant.setOnClickListener {
            SmartAssistantBottomSheet.show(
                this,
                smartAssistant,
                lessonTitle,
                lessonContent
            )
        }
        
        smartAssistant.initialize()
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
        
        if (!lessonPdfPath.isNullOrEmpty()) {
            binding.extractTextButton.visibility = View.VISIBLE
            setupExtractTextButton()
        }
    }
    
    private fun setupExtractTextButton() {
        binding.extractTextButton.setOnClickListener {
            val pdfPath = lessonPdfPath
            if (pdfPath.isNullOrEmpty()) {
                Toast.makeText(this, "لا يوجد PDF مرفق", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding.extractTextButton.isEnabled = false
            binding.extractTextButton.text = "جاري الاستخراج..."
            
            scope.launch(Dispatchers.Main) {
                try {
                    val pdfFile = File(pdfPath)
                    val extractedText = PdfTextExtractor.extractTextFromPdf(pdfFile) { current, total ->
                        binding.extractTextButton.text = "جاري الاستخراج... $current/$total"
                    }
                    
                    if (extractedText.isNotEmpty()) {
                        smartAssistant.setPdfExtractedText(extractedText)
                        Toast.makeText(this@LessonActivity, 
                            "تم استخراج ${extractedText.length} حرف", 
                            Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@LessonActivity, 
                            "لم يتم استخراج أي نص", 
                            Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LessonActivity, 
                        "فشل الاستخراج: ${e.message}", 
                        Toast.LENGTH_SHORT).show()
                } finally {
                    binding.extractTextButton.isEnabled = true
                    binding.extractTextButton.text = "استخراج النص"
                }
            }
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
                
                val extractedText = smartAssistant.loadLessonFromPdf(pdfFile, lessonTitle)
                Log.e(TAG, "PDF extracted: ${extractedText.length} chars")
                
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
    
    private fun loadBroadcastAttachments() {
        val attachments = teacherClient.getCurrentAttachments()
        if (attachments != null) {
            Log.e(TAG, "Loading broadcast attachments")
            Log.e(TAG, "Images: ${attachments.images.size}")
            Log.e(TAG, "PDF: ${attachments.pdfPath}")
            Log.e(TAG, "Video: ${attachments.videoPath}")
            
            if (attachments.images.isNotEmpty()) {
                lessonImages = attachments.images
            }
            if (attachments.pdfPath != null) {
                lessonPdfPath = attachments.pdfPath
            }
            if (attachments.videoPath != null) {
                lessonVideoPath = attachments.videoPath
            }
        }
    }
    
    private fun displayAttachments() {
        Log.d(TAG, "displayAttachments - images: ${lessonImages.size}, pdf: ${lessonPdfPath}, video: ${lessonVideoPath}")
        
        // Show lesson image (single image)
        if (lessonImages.isNotEmpty()) {
            val firstImage = lessonImages.first()
            if (lessonImages.size == 1 && firstImage.contains("/")) {
                Log.d(TAG, "Showing single lesson image")
                binding.lessonImageCard.visibility = View.VISIBLE
                try {
                    binding.lessonImage.setImageURI(android.net.Uri.fromFile(File(firstImage)))
                    binding.lessonImage.setOnClickListener { showFullScreenImage(firstImage) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading lesson image: ${e.message}")
                }
            }
        }
        
        // Show images carousel (multiple images)
        if (lessonImages.size > 1) {
            Log.d(TAG, "Showing ${lessonImages.size} images in carousel")
            binding.imagesCard.visibility = View.VISIBLE
            binding.imagesRecycler.visibility = View.VISIBLE
            binding.imagesRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            binding.imagesRecycler.adapter = ImageAdapter(lessonImages) { imagePath ->
                showFullScreenImage(imagePath)
            }
        }
        
        // Show PDF viewer
        if (!lessonPdfPath.isNullOrEmpty()) {
            Log.d(TAG, "Showing PDF: $lessonPdfPath")
            binding.pdfCard.visibility = View.VISIBLE
            binding.openPdfButton.setOnClickListener {
                lessonPdfPath?.let { openPdfViewer(it) }
            }
        }
        
        // Show video with thumbnail
        if (!lessonVideoPath.isNullOrEmpty()) {
            Log.d(TAG, "Showing video: $lessonVideoPath")
            binding.videoCard.visibility = View.VISIBLE
            displayVideoWithThumbnail(lessonVideoPath!!)
        }
    }
    
    private fun displayVideoWithThumbnail(videoPath: String) {
        try {
            val file = File(videoPath)
            if (!file.exists()) {
                Log.w(TAG, "Video file not found: $videoPath")
                return
            }
            
            // Try to create thumbnail
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(videoPath)
                val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                
                if (bitmap != null) {
                    binding.videoThumbnail.setImageBitmap(bitmap)
                    binding.videoThumbnail.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating thumbnail: ${e.message}")
            }
            
            binding.playVideoButton.visibility = View.VISIBLE
            binding.playVideoButton.setOnClickListener { openVideoPlayer(videoPath) }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying video: ${e.message}")
            binding.videoCard.visibility = View.VISIBLE
            binding.openVideoButton.visibility = View.VISIBLE
            binding.openVideoButton.setOnClickListener { openVideoPlayer(videoPath) }
        }
    }
    
    private fun openPdfViewer(pdfPath: String) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                Toast.makeText(this, "الملف غير موجود", Toast.LENGTH_SHORT).show()
                return
            }

            val extractedText = savedLesson?.optString("extractedText", "")
            if (!extractedText.isNullOrEmpty()) {
                smartAssistant.setPdfExtractedText(extractedText)
                Log.d(TAG, "Using cached PDF text: ${extractedText.length} chars")
            } else {
                Log.d(TAG, "No cached PDF text found")
            }

            val container = binding.root.findViewById<android.widget.FrameLayout>(R.id.pdfViewContainer)
            container.visibility = View.VISIBLE
            binding.pdfPrevPage?.visibility = View.VISIBLE
            binding.pdfNextPage?.visibility = View.VISIBLE
            binding.pdfPageIndicator?.visibility = View.VISIBLE

            pdfView = PDFView(this, null)
            container.addView(pdfView!!)

            pdfView?.fromFile(file)
                ?.enableSwipe(true)
                ?.swipeHorizontal(false)
                ?.enableDoubletap(true)
                ?.enableAnnotationRendering(false)
                ?.password(null)
                ?.scrollHandle(DefaultScrollHandle(this))
                ?.enableAntialiasing(true)
                ?.spacing(10)
                ?.autoSpacing(false)
                ?.pageFitPolicy(FitPolicy.WIDTH)
                ?.defaultPage(0)
                ?.onPageChange(object : OnPageChangeListener {
                    override fun onPageChanged(page: Int, pageCount: Int) {
                        binding.pdfPageIndicator?.text = "${page + 1}/$pageCount"
                    }
                })
                ?.load()

            binding.pdfPageIndicator?.text = "1/${pdfView?.pageCount ?: 1}"

            binding.pdfPrevPage?.setOnClickListener { prevPdfPage() }
            binding.pdfNextPage?.setOnClickListener { nextPdfPage() }

            Log.d(TAG, "PDF loaded successfully: ${pdfView?.pageCount ?: 0} pages")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening PDF: ${e.message}")
            tryExternalPdfViewer(pdfPath)
        }
    }

    private var savedLesson: JSONObject? = null

    private fun prevPdfPage() {
        pdfView?.let { pdf ->
            val currentPage = pdf.currentPage
            if (currentPage > 0) {
                pdf.jumpTo(currentPage - 1)
            }
        }
    }

    private fun nextPdfPage() {
        pdfView?.let { pdf ->
            val totalPages = pdf.pageCount
            val currentPage = pdf.currentPage
            if (currentPage < totalPages - 1) {
                pdf.jumpTo(currentPage + 1)
            }
        }
    }
    
    private fun tryExternalPdfViewer(pdfPath: String) {
        try {
            val file = File(pdfPath)
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
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
            Toast.makeText(this, "خطأ في فتح الملف: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openVideoPlayer(videoPath: String) {
        try {
            val file = File(videoPath)
            if (!file.exists()) {
                Toast.makeText(this, "الفيديو غير موجود", Toast.LENGTH_SHORT).show()
                return
            }
            
            val intent = Intent(this, com.edu.student.ui.video.VideoPlayerActivity::class.java)
            intent.putExtra(com.edu.student.ui.video.VideoPlayerActivity.EXTRA_VIDEO_PATH, videoPath)
            intent.putExtra(com.edu.student.ui.video.VideoPlayerActivity.EXTRA_VIDEO_TITLE, lessonTitle)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing video: ${e.message}")
            Toast.makeText(this, "فشل تشغيل الفيديو: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showFullScreenImage(imagePath: String) {
        try {
            val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setContentView(R.layout.dialog_fullscreen_image)
            val imageView = dialog.findViewById<android.widget.ImageView>(R.id.fullscreenImage)
            
            val bitmap = BitmapFactory.decodeFile(imagePath)
            imageView?.setImageBitmap(bitmap)
            
            imageView?.setOnClickListener { dialog.dismiss() }
            dialog.show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في عرض الصورة: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadLessonData() {
        var lesson: com.edu.student.domain.model.Lesson? = null

        val savedLessons = LessonStorage.loadAllLessons(this)
        val savedLesson = savedLessons.find { it.optString("id") == lessonId }

        if (savedLesson != null) {
            currentLesson = gson.fromJson(savedLesson.toString(), com.edu.student.domain.model.Lesson::class.java)

            val imagesArray = savedLesson.optJSONArray("images")
            if (imagesArray != null && lessonImages.isEmpty()) {
                lessonImages = (0 until imagesArray.length()).map { imagesArray.getString(it) }
            }
            if (lessonPdfPath.isNullOrEmpty()) {
                lessonPdfPath = savedLesson.optString("localPdfPath", "")
            }
            if (lessonVideoPath.isNullOrEmpty()) {
                lessonVideoPath = savedLesson.optString("localVideoPath", "")
            }

            Log.d(TAG, "Loaded lesson from storage - images: ${lessonImages.size}")
            displayAttachments()
        }

        // Try to get lesson from repository (cached lessons)
        val lessons = repository.getSubjects()
            .find { it.id == subjectId }?.lessons ?: emptyList()
        lesson = lessons.find { it.id == lessonId }
        
        // If not found, try to get from TeacherClient's cached lessons
        if (lesson == null) {
            try {
                val prefs = com.edu.student.data.preferences.StudentPreferences(this)
                val teacherId = prefs.getAssignedTeacherId() ?: ""
                val cachedJson = prefs.getCachedLessons(teacherId)
                if (cachedJson != null) {
                    val type = object : com.google.gson.reflect.TypeToken<List<com.edu.student.domain.model.Lesson>>() {}.type
                    val cachedLessons: List<com.edu.student.domain.model.Lesson> = com.google.gson.Gson().fromJson(cachedJson, type)
                    lesson = cachedLessons.find { it.id == lessonId }
                    Log.d(TAG, "Loaded lesson from SharedPreferences cache")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading lesson from cache: ${e.message}")
            }
        }
        
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
            
            // Load media paths from cached lesson
            if (lessonImages.isEmpty()) {
                val images = lesson.images
                if (!images.isNullOrEmpty()) {
                    lessonImages = images.filterNotNull()
                    Log.d(TAG, "Loaded images from cache: ${lessonImages.size}")
                } else {
                    val image = lesson.image
                    if (!image.isNullOrEmpty()) {
                        lessonImages = listOf(image)
                    }
                }
            }
            if (lessonPdfPath.isNullOrEmpty()) {
                val pdfPath = lesson.pdfPath
                if (!pdfPath.isNullOrEmpty()) {
                    lessonPdfPath = pdfPath
                    Log.d(TAG, "Loaded PDF from cache: $lessonPdfPath")
                }
            }
            if (lessonVideoPath.isNullOrEmpty()) {
                val videoPath = lesson.videoPath
                if (!videoPath.isNullOrEmpty()) {
                    lessonVideoPath = videoPath
                    Log.d(TAG, "Loaded video from cache: $lessonVideoPath")
                }
            }
            
            displayAttachments()
            
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
            val classId = currentLesson?.classId ?: ""
            
            teacherClient.submitHomework(mapOf(
                "studentId" to (student?.id ?: ""),
                "studentName" to (student?.name ?: ""),
                "classId" to classId,
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
            val classId = lesson.classId
            
            val answersJson = com.google.gson.Gson().toJson(answers)
            
            teacherClient.submitHomework(mapOf(
                "studentId" to (student?.id ?: ""),
                "studentName" to (student?.name ?: ""),
                "classId" to classId,
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
