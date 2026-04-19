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
import com.edu.teacher.ui.video.VideoPlayerActivity

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
                videoUri = lessonJson.optString("videoUri", "").ifEmpty { null }
                imageUri = lessonJson.optString("imageUri", "").ifEmpty { null }

                android.util.Log.e("LessonDetails", "parseIntentData - imageUri: $imageUri")
                android.util.Log.e("LessonDetails", "parseIntentData - videoUri: $videoUri")
                android.util.Log.e("LessonDetails", "parseIntentData - pdfUri: $pdfUri")
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
            videoUri = lesson.optString("videoUri", "").ifEmpty { null }
            imageUri = lesson.optString("imageUri", "").ifEmpty { null }

            android.util.Log.e("LessonDetails", "loadLessonFromStorage - imageUri: $imageUri")
            android.util.Log.e("LessonDetails", "loadLessonFromStorage - videoUri: $videoUri")
            android.util.Log.e("LessonDetails", "loadLessonFromStorage - pdfUri: $pdfUri")
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

        if (lessonContent.isNotEmpty()) {
            binding.lessonContent.text = lessonContent
            binding.lessonContent.visibility = View.VISIBLE
        } else {
            binding.lessonContent.visibility = View.GONE
        }

updatePublishStatus()
        
        displayAttachments()
        displayMedia()
    }

    private fun displayAttachments(lesson: org.json.JSONObject = lessonJson) {
        android.util.Log.e("LessonDetails", "=== displayAttachments CALLED ===")
        android.util.Log.e("LessonDetails", "imageUri: '$imageUri'")
        android.util.Log.e("LessonDetails", "imageUri exists: ${imageUri?.let { java.io.File(it).exists() }}")
        android.util.Log.e("LessonDetails", "videoUri: '$videoUri'")
        android.util.Log.e("LessonDetails", "videoUri exists: ${videoUri?.let { java.io.File(it).exists() }}")
        android.util.Log.e("LessonDetails", "pdfUri: '$pdfUri'")
        
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
            
            val currentVideoUri = videoUri
            if (currentVideoUri!!.startsWith("/") && File(currentVideoUri).exists()) {
                try {
                    val thumbnail = android.media.ThumbnailUtils.createVideoThumbnail(
                        currentVideoUri,
                        android.provider.MediaStore.Video.Thumbnails.MINI_KIND
                    )
                    if (thumbnail != null) {
                        binding.videoThumbnail.setImageBitmap(thumbnail)
                    } else {
                        binding.videoThumbnail.setImageResource(R.drawable.ic_play)
                    }
                } catch (e: Exception) {
                    binding.videoThumbnail.setImageResource(R.drawable.ic_play)
                }
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
            
            val imageList = mutableListOf<String>()
            val currentImageUri = imageUri
            if (currentImageUri != null) {
                if (!currentImageUri.startsWith("http")) {
                    val imageFile = File(currentImageUri)
                    if (imageFile.exists()) {
                        imageList.add(currentImageUri)
                    }
                } else {
                    imageList.add(currentImageUri)
                }
            }
            
            if (imageList.isNotEmpty()) {
                binding.imagesRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                binding.imagesRecyclerView.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<ImageViewHolder>() {
                    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ImageViewHolder {
                        val imageView = android.widget.ImageView(parent.context)
                        imageView.layoutParams = android.view.ViewGroup.LayoutParams(300, 300)
                        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        return ImageViewHolder(imageView)
                    }
                    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
                        val imagePath = imageList[position]
                        if (!imagePath.startsWith("http")) {
                            Glide.with(this@LessonDetailsActivity).load(File(imagePath)).into(holder.imageView)
                        } else {
                            Glide.with(this@LessonDetailsActivity).load(Uri.parse(imagePath)).into(holder.imageView)
                        }
                    }
                    override fun getItemCount() = imageList.size
                }
            }
        } else {
            binding.imagesCard.visibility = View.GONE
        }

        if (!hasAttachments) {
            binding.attachmentsSection.visibility = View.GONE
        }
    }
    
    private class ImageViewHolder(val imageView: android.widget.ImageView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(imageView)

    private fun displayMedia() {
        try {
        android.util.Log.e("LessonDetails", "=== displayMedia CALLED ===")
        
        val imagePath = lessonJson.optString("imageUri", "")
        android.util.Log.e("LessonDetails", "displayMedia - imagePath: '$imagePath'")
        android.util.Log.e("LessonDetails", "displayMedia - file exists: ${java.io.File(imagePath).exists()}")
        
        if (imagePath.isNotEmpty() && java.io.File(imagePath).exists()) {
            android.util.Log.e("LessonDetails", "displayMedia - showing image")
            binding.lessonImageCard?.visibility = View.VISIBLE
            binding.lessonImage?.let { 
                com.bumptech.glide.Glide.with(this).load(java.io.File(imagePath)).into(it)
            }
        } else {
            android.util.Log.e("LessonDetails", "displayMedia - hiding image")
            binding.lessonImageCard?.visibility = View.GONE
        }
        
        val videoPath = lessonJson.optString("videoUri", "")
        android.util.Log.e("LessonDetails", "displayMedia - videoPath: '$videoPath'")
        android.util.Log.e("LessonDetails", "displayMedia - video file exists: ${java.io.File(videoPath).exists()}")
        
        if (videoPath.isNotEmpty() && java.io.File(videoPath).exists()) {
            android.util.Log.e("LessonDetails", "displayMedia - showing video button")
            binding.videoButton?.visibility = View.VISIBLE
            binding.videoButton?.setOnClickListener {
                val intent = android.content.Intent(this, com.edu.teacher.ui.video.VideoPlayerActivity::class.java)
                intent.putExtra("video_path", videoPath)
                startActivity(intent)
            }
        } else {
            android.util.Log.e("LessonDetails", "displayMedia - hiding video button")
            binding.videoButton?.visibility = View.GONE
        }
    } catch (e: Exception) {
        android.util.Log.e("LessonDetails", "Error in displayMedia: ${e.message}", e)
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
            val currentVideoUri = videoUri
            val videoPath = when {
                currentVideoUri!!.startsWith("/") -> currentVideoUri
                else -> null
            }
            
            if (videoPath != null && File(videoPath).exists()) {
                val intent = Intent(this, com.edu.teacher.ui.video.VideoPlayerActivity::class.java)
                intent.putExtra(com.edu.teacher.ui.video.VideoPlayerActivity.EXTRA_VIDEO_PATH, videoPath)
                intent.putExtra(com.edu.teacher.ui.video.VideoPlayerActivity.EXTRA_VIDEO_TITLE, lessonTitle)
                startActivity(intent)
            } else {
                val uri = Uri.parse(currentVideoUri)
                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "video/*")
                }
                startActivity(Intent.createChooser(playIntent, getString(R.string.btn_play)))
            }
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
