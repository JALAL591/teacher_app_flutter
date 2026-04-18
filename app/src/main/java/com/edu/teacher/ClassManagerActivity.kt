package com.edu.teacher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edu.teacher.databinding.ActivityClassManagerBinding
import com.edu.teacher.databinding.ItemLocalLessonBinding
import com.edu.teacher.databinding.ItemPublishedLessonBinding
import org.json.JSONObject

class ClassManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClassManagerBinding

    private var classId: String = ""
    private var className: String = ""
    private var subjectId: String = ""
    private var subjectName: String = ""
    private var teacherId: String = ""
    private var localLessons = mutableListOf<JSONObject>()
    private var publishedLessons = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run {
            Toast.makeText(this, getString(R.string.error_teacher_not_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        classId = intent.getStringExtra("class_id") ?: ""
        className = intent.getStringExtra("class_name") ?: getString(R.string.default_class)
        subjectId = intent.getStringExtra("subject_id") ?: ""
        subjectName = intent.getStringExtra("subject_name") ?: getString(R.string.default_subject)

        initViews()
        loadAllData()
    }

    override fun onResume() {
        super.onResume()
        loadAllData()
    }

    private fun initViews() {
        binding.subjectTitle.text = subjectName
        binding.gradeSectionText.text = className

        binding.localDraftsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.publishedLessonsRecyclerView.layoutManager = LinearLayoutManager(this)

        // إضافة فاصل بين عناصر المسودات
        val draftDivider = object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.bottom = 1
            }
        }
        binding.localDraftsRecyclerView.addItemDecoration(draftDivider)

        // إضافة فاصل بين عناصر المنشورات
        val publishedDivider = object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                outRect.bottom = 1
            }
        }
        binding.publishedLessonsRecyclerView.addItemDecoration(publishedDivider)

        binding.backButton.setOnClickListener { finish() }
        binding.addLessonButton.setOnClickListener {
            val intent = Intent(this, AddLessonActivity::class.java).apply {
                putExtra("class_id", classId)
                putExtra("subject_id", subjectId)
                putExtra("teacher_id", teacherId)
            }
            startActivity(intent)
        }
        binding.editLessonsButton.setOnClickListener {
            val intent = Intent(this, EditLessonActivity::class.java).apply {
                putExtra("class_id", classId)
                putExtra("subject_id", subjectId)
                putExtra("teacher_id", teacherId)
            }
            startActivity(intent)
        }
    }

    private fun loadAllData() {
        // تحميل الدروس المحلية
        localLessons.clear()
        val allLessons = DataManager.getLessons(this, teacherId, classId)
        localLessons.addAll(allLessons.filter { !it.optBoolean("isPublished", false) })

        // تحميل الدروس المنشورة
        publishedLessons.clear()
        publishedLessons.addAll(allLessons.filter { it.optBoolean("isPublished", false) })

        binding.localDraftsRecyclerView.adapter = LocalLessonsAdapter(localLessons) { lesson, position ->
            showPublishDialog(lesson, position)
        }
        binding.publishedLessonsRecyclerView.adapter = PublishedLessonsAdapter(
            publishedLessons,
            onDelete = { lesson, position -> showDeletePublishedDialog(lesson, position) },
            onBroadcast = { lesson, position -> broadcastLesson(lesson, position) },
            onEdit = { lesson, position -> editLesson(lesson, position) }
        )
    }

    private fun showPublishDialog(lesson: JSONObject, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_publish_lesson)
            .setMessage(getString(R.string.msg_confirm_publish, lesson.optString("title")))
            .setPositiveButton(R.string.btn_publish) { _, _ -> publishLesson(lesson, position) }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun publishLesson(lesson: JSONObject, position: Int) {
        Toast.makeText(this, getString(R.string.status_publishing, lesson.optString("title")), Toast.LENGTH_SHORT).show()

        val publishedLesson = JSONObject(lesson.toString())
        publishedLesson.put("isPublished", true as Any)
        publishedLesson.put("publishedAt", DataManager.getSyncTimestamp() as Any)

        publishedLessons.add(0, publishedLesson)
        binding.publishedLessonsRecyclerView.adapter?.notifyItemInserted(0)

        localLessons.removeAt(position)
        binding.localDraftsRecyclerView.adapter?.notifyItemRemoved(position)

        DataManager.updateLesson(this, teacherId, classId, lesson.optString("id"), publishedLesson)

        Toast.makeText(this, getString(R.string.toast_lesson_published), Toast.LENGTH_SHORT).show()
    }

    private fun showDeletePublishedDialog(lesson: JSONObject, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_lesson)
            .setMessage(getString(R.string.msg_confirm_delete_lesson, lesson.optString("title")))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                DataManager.removeLesson(this, teacherId, classId, lesson.optString("id"))
                publishedLessons.removeAt(position)
                binding.publishedLessonsRecyclerView.adapter?.notifyItemRemoved(position)
                Toast.makeText(this, getString(R.string.toast_lesson_deleted), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.btn_no, null)
            .show()
    }

    private fun broadcastLesson(lesson: JSONObject, @Suppress("UNUSED_PARAMETER") position: Int) {
        android.util.Log.d("ClassManager", "broadcastLesson called for: ${lesson.optString("title")}")
        Toast.makeText(this, "جاري بث الدرس: ${lesson.optString("title")}", Toast.LENGTH_SHORT).show()
        
        val server = (application as? TeacherApp)?.teacherServer ?: TeacherServer(this).also {
            (application as? TeacherApp)?.teacherServer = it
        }
        
        if (!server.isRunning) {
            android.util.Log.d("ClassManager", "Server not running, starting...")
            server.start()
            (application as? TeacherApp)?.teacherServer = server
            Toast.makeText(this, "تم تشغيل الرادار تلقائياً", Toast.LENGTH_SHORT).show()
        }
        
        android.util.Log.d("ClassManager", "Calling server.broadcastLesson()")
        server.broadcastLesson(lesson)
        android.util.Log.d("ClassManager", "broadcastLesson completed")
        Toast.makeText(this, "تم بث الدرس للطلاب المتصلين", Toast.LENGTH_SHORT).show()
    }

    private fun editLesson(lesson: JSONObject, @Suppress("UNUSED_PARAMETER") position: Int) {
        val intent = Intent(this, EditLessonActivity::class.java).apply {
            putExtra("class_id", classId)
            putExtra("subject_id", subjectId)
            putExtra("teacher_id", teacherId)
            putExtra("lesson_json", lesson.toString())
        }
        startActivity(intent)
    }

    inner class LocalLessonsAdapter(
        private val lessons: List<JSONObject>,
        private val onPublish: (JSONObject, Int) -> Unit
    ) : RecyclerView.Adapter<LocalLessonsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemLocalLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lesson = lessons[position]
            holder.binding.lessonTitle.text = lesson.optString("title", getString(R.string.default_lesson_title))
            
            val unit = lesson.optString("unit", "")
            holder.binding.unitText.text = if (unit.isNotEmpty()) unit else getString(R.string.label_unit_name)
            
            holder.binding.root.setOnClickListener {
                val intent = Intent(this@ClassManagerActivity, LessonDetailsActivity::class.java).apply {
                    putExtra(LessonDetailsActivity.EXTRA_LESSON_JSON, lesson.toString())
                    putExtra("class_id", classId)
                    putExtra("teacher_id", teacherId)
                }
                startActivity(intent)
            }
            
            holder.binding.publishButton.setOnClickListener { onPublish(lesson, position) }
        }

        override fun getItemCount(): Int = lessons.size

        inner class ViewHolder(val binding: ItemLocalLessonBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class PublishedLessonsAdapter(
        private val lessons: List<JSONObject>,
        private val onDelete: (JSONObject, Int) -> Unit,
        private val onBroadcast: (JSONObject, Int) -> Unit,
        private val onEdit: (JSONObject, Int) -> Unit
    ) : RecyclerView.Adapter<PublishedLessonsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPublishedLessonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lesson = lessons[position]
            holder.binding.lessonTitle.text = lesson.optString("title", getString(R.string.default_lesson_title))
            
            holder.binding.publishedItemRoot.setOnClickListener {
                val intent = Intent(this@ClassManagerActivity, LessonDetailsActivity::class.java).apply {
                    putExtra(LessonDetailsActivity.EXTRA_LESSON_JSON, lesson.toString())
                    putExtra("class_id", classId)
                    putExtra("teacher_id", teacherId)
                }
                startActivity(intent)
            }
            
            holder.binding.broadcastButton.setOnClickListener { onBroadcast(lesson, position) }
            holder.binding.editButton.setOnClickListener { onEdit(lesson, position) }
            holder.binding.deleteButton.setOnClickListener { onDelete(lesson, position) }
        }

        override fun getItemCount(): Int = lessons.size

        inner class ViewHolder(val binding: ItemPublishedLessonBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
