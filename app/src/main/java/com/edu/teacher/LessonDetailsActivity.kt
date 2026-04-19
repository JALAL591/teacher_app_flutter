package com.edu.teacher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.edu.teacher.databinding.ActivityLessonDetailsBinding
import org.json.JSONObject
import java.io.File
import android.util.Base64
import com.edu.teacher.TeacherServer.LessonAttachments

class LessonDetailsActivity : BaseActivity() {

    private lateinit var binding: ActivityLessonDetailsBinding

    private var lessonId: String = ""
    private var lessonTitle: String = ""
    private var lessonContent: String = ""
    private var lessonUnit: String = ""
    private var classId: String = ""
    private var subjectId: String = ""
    private var teacherId: String = ""
    private var isPublished: Boolean = false
    private var lessonJson: JSONObject = JSONObject()

    private var pdfUri: String? = null
    private var videoUri: String? = null
    private var imageUri: String? = null

    companion object {
        const val EXTRA_LESSON_JSON = "lesson_json"
        const val EXTRA_LESSON_ID = "lesson_id"
        const val EXTRA_CLASS_ID = "class_id"
        const val EXTRA_TEACHER_ID = "teacher_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLessonDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, R.string.error_teacher_not_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        parseIntentData()
        initViews()
        displayLessonDetails()
    }

    private fun parseIntentData() {
        val jsonString = intent.getStringExtra(EXTRA_LESSON_JSON)
        if (!jsonString.isNullOrEmpty()) {
            try {
                lessonJson = JSONObject(jsonString)
                lessonId = lessonJson.optString("id", "")
                lessonTitle = lessonJson.optString("title", getString(R.string.default_lesson_title))
                lessonContent = lessonJson.optString("content", "")
                lessonUnit = lessonJson.optString("unit", "")
                classId = lessonJson.optString("classId", "")
                subjectId = intent.getStringExtra("subject_id") ?: ""
                isPublished = lessonJson.optBoolean("isPublished", false)
                pdfUri = lessonJson.optString("pdfUri", "").ifEmpty { null }
                videoUri = lessonJson.optString("video", "").ifEmpty { null }
                imageUri = lessonJson.optString("image", "").ifEmpty { null }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, R.string.error_loading_lesson, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        } else {
            lessonId = intent.getStringExtra(EXTRA_LESSON_ID) ?: ""
            classId = intent.getStringExtra(EXTRA_CLASS_ID) ?: ""
            teacherId = intent.getStringExtra(EXTRA_TEACHER_ID) ?: teacherId
            if (lessonId.isNotEmpty() && classId.isNotEmpty()) {
                loadLessonFromStorage()
            } else {
                Toast.makeText(this, R.string.error_loading_lesson, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }
    }

    private fun loadLessonFromStorage() {
        val lessons = DataManager.getLessons(this, teacherId, classId)
        val lesson = lessons.find { it.optString("id") == lessonId }
        if (lesson != null) {
            lessonJson = lesson
            lessonTitle = lesson.optString("title", getString(R.string.default_lesson_title))
            lessonContent = lesson.optString("content", "")
            lessonUnit = lesson.optString("unit", "")
            isPublished = lesson.optBoolean("isPublished", false)
            pdfUri = lesson.optString("pdfUri", "").ifEmpty { null }
            videoUri = lesson.optString("video", "").ifEmpty { null }
            imageUri = lesson.optString("image", "").ifEmpty { null }
        } else {
            Toast.makeText(this, R.string.error_loading_lesson, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        loadLessonFromStorage()
        displayAttachments()
    }

    private fun initViews() {
        binding.backButton.setOnClickListener { finish() }

        binding.broadcastButton.setOnClickListener { showBroadcastConfirmation() }
        binding.editButton.setOnClickListener { openEditLesson() }
        binding.deleteButton.setOnClickListener { showDeleteConfirmation() }
        binding.pdfPrevPage.setOnClickListener { prevPdfPage() }
        binding.pdfNextPage.setOnClickListener { nextPdfPage() }
        binding.playVideoButton.setOnClickListener { playVideo() }

        binding.bottomNav.setOnItemSelectedListener { position ->
            navigateTo(position)
        }
        binding.bottomNav.setActiveTab(2)
    }

    private fun displayLessonDetails() {
        binding.lessonTitle.text = lessonTitle

        if (lessonUnit.isNotEmpty()) {
            binding.lessonUnit.text = lessonUnit
            binding.lessonUnit.visibility = View.VISIBLE
        } else {
            binding.lessonUnit.visibility = View.GONE
        }

        updatePublishStatus()

        if (lessonContent.isNotEmpty()) {
            binding.lessonContent.text = lessonContent
            binding.lessonContent.movementMethod = LinkMovementMethod.getInstance()
            binding.emptyContentText.visibility = View.GONE
        } else {
            binding.lessonContent.visibility = View.GONE
            binding.emptyContentText.visibility = View.VISIBLE
        }

        binding.lessonEmoji.text = getLessonEmoji()
        updateBroadcastButton()
        displayAttachments()
    }

    private fun displayAttachments() {
        var hasAttachments = false

        if (!pdfUri.isNullOrEmpty()) {
            hasAttachments = true
            binding.attachmentsSection.visibility = View.VISIBLE
            binding.pdfCard.visibility = View.VISIBLE
            loadPdf()
        } else {
            binding.pdfCard.visibility = View.GONE
        }

        if (!videoUri.isNullOrEmpty()) {
            hasAttachments = true
            binding.attachmentsSection.visibility = View.VISIBLE
            binding.videoCard.visibility = View.VISIBLE
            if (!videoUri!!.startsWith("http")) {
                Glide.with(this)
                    .load(Uri.parse(videoUri))
                    .centerCrop()
                    .into(binding.videoThumbnail)
            } else {
                binding.videoThumbnail.setImageResource(R.drawable.ic_play)
            }
        } else {
            binding.videoCard.visibility = View.GONE
        }

        if (!imageUri.isNullOrEmpty()) {
            hasAttachments = true
            binding.attachmentsSection.visibility = View.VISIBLE
            binding.imagesCard.visibility = View.VISIBLE
        } else {
            binding.imagesCard.visibility = View.GONE
        }

        if (!hasAttachments) {
            binding.attachmentsSection.visibility = View.GONE
        }
    }

    private fun loadPdf() {
        if (pdfUri.isNullOrEmpty()) return

        try {
            val uriString = pdfUri!!

            when {
                uriString.startsWith("/") -> {
                    val file = File(uriString)
                    if (file.exists()) {
                        displayLocalPdf(file)
                    }
                }
                else -> {
                    val uri = Uri.parse(uriString)
                    displayPdfFromUri(uri)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            binding.pdfCard.visibility = View.GONE
        }
    }

    private fun displayLocalPdf(file: File) {
        try {
            binding.pdfView.fromFile(file)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .enableAnnotationRendering(false)
                .password(null)
                .scrollHandle(null)
                .enableAntialiasing(true)
                .spacing(0)
                .autoSpacing(false)
                .pageFitPolicy(com.github.barteksc.pdfviewer.util.FitPolicy.WIDTH)
                .defaultPage(0)
                .load()
            updatePdfPageIndicator()
        } catch (e: Exception) {
            e.printStackTrace()
            binding.pdfCard.visibility = View.GONE
        }
    }

    private fun displayPdfFromUri(uri: Uri) {
        try {
            binding.pdfView.fromUri(uri)
                .enableSwipe(true)
                .swipeHorizontal(false)
                .enableDoubletap(true)
                .enableAnnotationRendering(false)
                .password(null)
                .scrollHandle(null)
                .enableAntialiasing(true)
                .spacing(0)
                .autoSpacing(false)
                .pageFitPolicy(com.github.barteksc.pdfviewer.util.FitPolicy.WIDTH)
                .defaultPage(0)
                .load()
            updatePdfPageIndicator()
        } catch (e: Exception) {
            e.printStackTrace()
            binding.pdfCard.visibility = View.GONE
        }
    }

    private fun prevPdfPage() {
        val currentPage = binding.pdfView.currentPage
        if (currentPage > 0) {
            binding.pdfView.jumpTo(currentPage - 1)
            updatePdfPageIndicator()
        }
    }

    private fun nextPdfPage() {
        val totalPages = binding.pdfView.pageCount
        val currentPage = binding.pdfView.currentPage
        if (currentPage < totalPages - 1) {
            binding.pdfView.jumpTo(currentPage + 1)
            updatePdfPageIndicator()
        }
    }

    private fun updatePdfPageIndicator() {
        val currentPage = binding.pdfView.currentPage + 1
        val totalPages = binding.pdfView.pageCount
        binding.pdfPageIndicator.text = "$currentPage/$totalPages"
    }

    private fun playVideo() {
        if (videoUri.isNullOrEmpty()) {
            Toast.makeText(this, R.string.msg_no_video, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = Uri.parse(videoUri)
            val playIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
            }
            startActivity(Intent.createChooser(playIntent, getString(R.string.btn_play)))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, R.string.error_loading_video, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePublishStatus() {
        if (isPublished) {
            binding.publishStatusText.text = getString(R.string.label_published_check)
            binding.publishStatusText.setTextColor(getColor(R.color.success))
        } else {
            binding.publishStatusText.text = getString(R.string.label_draft)
            binding.publishStatusText.setTextColor(getColor(R.color.warning))
        }
    }

    private fun updateBroadcastButton() {
        if (isPublished) {
            binding.broadcastButton.setImageResource(R.drawable.ic_share)
            binding.broadcastButton.contentDescription = getString(R.string.btn_share)
        } else {
            binding.broadcastButton.setImageResource(R.drawable.ic_broadcast)
            binding.broadcastButton.contentDescription = getString(R.string.btn_broadcast)
        }
    }

    private fun getLessonEmoji(): String {
        return if (isPublished) "\u2713" else "\uD83D\uDCDD"
    }

    private fun showBroadcastConfirmation() {
        if (isPublished) {
            shareLesson()
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_publish_lesson)
                .setMessage(getString(R.string.msg_confirm_publish, lessonTitle))
                .setPositiveButton(R.string.btn_publish) { _, _ -> broadcastLesson() }
                .setNegativeButton(R.string.btn_no, null)
                .show()
        }
    }

    private fun broadcastLesson() {
        android.util.Log.d("LessonDetails", "broadcastLesson called for: $lessonTitle")
        Toast.makeText(this, getString(R.string.status_publishing, lessonTitle), Toast.LENGTH_SHORT).show()

        val publishedLesson = JSONObject(lessonJson.toString())
        publishedLesson.put("isPublished", true)
        publishedLesson.put("publishedAt", DataManager.getSyncTimestamp())

        DataManager.updateLesson(this, teacherId, classId, lessonId, publishedLesson)

        isPublished = true
        lessonJson = publishedLesson

        val server = (application as? TeacherApp)?.teacherServer
        android.util.Log.d("LessonDetails", "Server running: ${server?.isRunning == true}")
        
        if (server != null && server.isRunning) {
            // Read attachments from lesson
            val imagePath = publishedLesson.optString("imageUri", "")
            val videoPath = publishedLesson.optString("videoUri", "")
            val pdfPath = publishedLesson.optString("pdfUri", "")
            
            android.util.Log.d("LessonDetails", "Reading attachments - image: $imagePath, video: $videoPath, pdf: $pdfPath")
            
            val images = mutableListOf<String>()
            
            // Read image to Base64
            if (imagePath.isNotEmpty()) {
                try {
                    val file = File(imagePath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        images.add(base64)
                        android.util.Log.d("LessonDetails", "Image converted to Base64")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonDetails", "Error reading image: ${e.message}")
                }
            }
            
            var pdfData: String? = null
            var pdfFileName: String? = null
            
            // Read PDF to Base64
            if (pdfPath.isNotEmpty()) {
                try {
                    val file = File(pdfPath)
                    if (file.exists()) {
                        pdfData = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        pdfFileName = file.name
                        android.util.Log.d("LessonDetails", "PDF converted to Base64")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonDetails", "Error reading PDF: ${e.message}")
                }
            }
            
            var videoData: String? = null
            var videoFileName: String? = null
            
            // Read video to Base64
            if (videoPath.isNotEmpty()) {
                try {
                    val file = File(videoPath)
                    if (file.exists()) {
                        videoData = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                        videoFileName = file.name
                        android.util.Log.d("LessonDetails", "Video converted to Base64")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LessonDetails", "Error reading video: ${e.message}")
                }
            }
            
            // Create attachments
            val attachments = LessonAttachments(
                images = images,
                pdfData = pdfData,
                pdfFileName = pdfFileName,
                videoData = videoData,
                videoFileName = videoFileName
            )
            
            android.util.Log.d("LessonDetails", "Calling server.broadcastLesson()")
            server.broadcastLesson(publishedLesson, attachments)
            android.util.Log.d("LessonDetails", "Lesson broadcast completed")
            Toast.makeText(this, "تم إرسال الدرس للطلاب", Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.w("LessonDetails", "Server not running - lesson saved only")
            Toast.makeText(this, "الرادار غير نشط - الدرس محفوظ", Toast.LENGTH_SHORT).show()
        }

        updatePublishStatus()
        updateBroadcastButton()
        binding.lessonEmoji.text = getLessonEmoji()

        Toast.makeText(this, R.string.toast_lesson_published, Toast.LENGTH_SHORT).show()
    }

    private fun shareLesson() {
        val shareText = buildString {
            append(getString(R.string.share_lesson_title, lessonTitle))
            append("\n\n")
            if (lessonUnit.isNotEmpty()) {
                append(getString(R.string.share_lesson_unit, lessonUnit))
                append("\n")
            }
            if (lessonContent.isNotEmpty()) {
                append("\n")
                append(lessonContent)
            }
            append("\n\n")
            append(getString(R.string.share_lesson_footer))
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject, lessonTitle))
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
    }

    private fun openEditLesson() {
        val editIntent = Intent(this, EditLessonActivity::class.java).apply {
            putExtra("lesson_id", lessonId)
            putExtra("lesson_json", lessonJson.toString())
            putExtra("class_id", classId)
            putExtra("teacher_id", teacherId)
        }
        startActivity(editIntent)
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_lesson)
            .setMessage(getString(R.string.msg_confirm_delete_lesson, lessonTitle))
            .setPositiveButton(R.string.btn_delete) { _, _ -> deleteLesson() }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun deleteLesson() {
        if (lessonId.isEmpty() || classId.isEmpty()) {
            Toast.makeText(this, R.string.error_deleting_lesson, Toast.LENGTH_SHORT).show()
            return
        }

        DataManager.removeLesson(this, teacherId, classId, lessonId)
        Toast.makeText(this, R.string.toast_lesson_deleted, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun navigateTo(position: Int) {
        when (position) {
            0 -> startActivity(android.content.Intent(this, DashboardActivity::class.java))
            1 -> startActivity(android.content.Intent(this, StatsActivity::class.java))
            2 -> { }
            3 -> startActivity(android.content.Intent(this, StudentsActivity::class.java))
            4 -> startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }
}
