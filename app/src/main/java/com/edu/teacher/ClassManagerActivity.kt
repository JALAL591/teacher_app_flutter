package com.edu.teacher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

class ClassManagerActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var subjectTitle: TextView
    private lateinit var gradeSectionText: TextView
    private lateinit var addLessonButton: FloatingActionButton
    private lateinit var localDraftsRecyclerView: RecyclerView
    private lateinit var publishedLessonsRecyclerView: RecyclerView
    private lateinit var refreshButton: Button
    
    private var classId: String = ""
    private var classInfo = JSONObject()
    private var localLessons = mutableListOf<JSONObject>()
    private var publishedLessons = mutableListOf<JSONObject>()
    private var isLoading = false
    private var syncingId: String? = null
    
    companion object {
        private const val TEACHER_ID = "TCH-001"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_manager)
        
        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
        
        classId = intent.getStringExtra("class_id") ?: ""
        val subject = intent.getStringExtra("class_subject") ?: "المادة"
        val grade = intent.getStringExtra("class_grade") ?: "الصف"
        val section = intent.getStringExtra("class_section") ?: "أ"
        
        // تهيئة العناصر
        backButton = findViewById(R.id.backButton)
        subjectTitle = findViewById(R.id.subjectTitle)
        gradeSectionText = findViewById(R.id.gradeSectionText)
        addLessonButton = findViewById(R.id.addLessonButton)
        localDraftsRecyclerView = findViewById(R.id.localDraftsRecyclerView)
        publishedLessonsRecyclerView = findViewById(R.id.publishedLessonsRecyclerView)
        refreshButton = findViewById(R.id.refreshButton)
        
        subjectTitle.text = subject
        gradeSectionText.text = "$grade • شعبة $section"
        
        localDraftsRecyclerView.layoutManager = LinearLayoutManager(this)
        publishedLessonsRecyclerView.layoutManager = LinearLayoutManager(this)
        
        backButton.setOnClickListener { finish() }
        addLessonButton.setOnClickListener {
            val intent = Intent(this, AddLessonActivity::class.java)
            intent.putExtra("class_id", classId)
            startActivity(intent)
        }
        refreshButton.setOnClickListener { loadAllData() }
        
        loadAllData()
    }
    
    override fun onResume() {
        super.onResume()
        loadAllData()
    }
    
    private fun loadAllData() {
        isLoading = true
        refreshButton.isEnabled = false
        
        // تحميل الدروس المحلية
        val lessonsKey = "lessons_$TEACHER_ID"
        val savedLessons = prefs.getString(lessonsKey, "[]")
        val allLessons = JSONArray(savedLessons)
        
        localLessons.clear()
        for (i in 0 until allLessons.length()) {
            val lesson = allLessons.getJSONObject(i)
            if (lesson.optString("classId") == classId) {
                localLessons.add(lesson)
            }
        }
        
        // تحميل الدروس المنشورة (محاكاة)
        publishedLessons.clear()
        
        localDraftsRecyclerView.adapter = LocalLessonsAdapter(localLessons) { lesson, position ->
            showPublishDialog(lesson, position)
        }
        publishedLessonsRecyclerView.adapter = PublishedLessonsAdapter(publishedLessons) { lesson, position ->
            showDeletePublishedDialog(lesson, position)
        }
        
        isLoading = false
        refreshButton.isEnabled = true
    }
    
    private fun showPublishDialog(lesson: JSONObject, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("بث الدرس")
            .setMessage("هل أنت متأكد من بث هذا الدرس للطلاب؟")
            .setPositiveButton("بث") { _, _ ->
                publishLesson(lesson, position)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun publishLesson(lesson: JSONObject, position: Int) {
        Toast.makeText(this, "📡 جاري بث الدرس: ${lesson.optString("title")}", Toast.LENGTH_SHORT).show()
        
        // إضافة الدرس إلى القائمة المنشورة
        val publishedLesson = JSONObject(lesson.toString())
        publishedLesson.put("isPublished", true)
        publishedLessons.add(0, publishedLesson)
        publishedLessonsRecyclerView.adapter?.notifyItemInserted(0)
        
        // إزالة من المسودات
        localLessons.removeAt(position)
        localDraftsRecyclerView.adapter?.notifyItemRemoved(position)
        
        // حفظ التغييرات
        saveLocalLessons()
        
        Toast.makeText(this, "✅ تم بث الدرس بنجاح!", Toast.LENGTH_SHORT).show()
    }
    
    private fun showDeletePublishedDialog(lesson: JSONObject, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("حذف الدرس")
            .setMessage("سيتم حذف الدرس من تطبيق الطالب نهائياً؟")
            .setPositiveButton("حذف") { _, _ ->
                publishedLessons.removeAt(position)
                publishedLessonsRecyclerView.adapter?.notifyItemRemoved(position)
                Toast.makeText(this, "تم الحذف من السيرفر", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
    
    private fun saveLocalLessons() {
        val lessonsArray = JSONArray()
        localLessons.forEach { lessonsArray.put(it) }
        prefs.edit().putString("lessons_$TEACHER_ID", lessonsArray.toString()).apply()
    }
    
    // Adapter للمسودات المحلية
    inner class LocalLessonsAdapter(
        private val lessons: List<JSONObject>,
        private val onPublish: (JSONObject, Int) -> Unit
    ) : RecyclerView.Adapter<LocalLessonsAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_local_lesson, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lesson = lessons[position]
            holder.titleText.text = lesson.optString("title", "بدون عنوان")
            holder.publishButton.setOnClickListener { onPublish(lesson, position) }
            
            val imageBase64 = lesson.optString("image", "")
            if (imageBase64 != null && imageBase64.isNotEmpty()) {
                // عرض الصورة (سيتم تنفيذه لاحقاً)
            }
        }
        
        override fun getItemCount(): Int = lessons.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.lessonImage)
            val titleText: TextView = itemView.findViewById(R.id.lessonTitle)
            val publishButton: Button = itemView.findViewById(R.id.publishButton)
        }
    }
    
    // Adapter للدروس المنشورة
    inner class PublishedLessonsAdapter(
        private val lessons: List<JSONObject>,
        private val onDelete: (JSONObject, Int) -> Unit
    ) : RecyclerView.Adapter<PublishedLessonsAdapter.ViewHolder>() {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_published_lesson, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val lesson = lessons[position]
            holder.titleText.text = lesson.optString("title", "بدون عنوان")
            holder.deleteButton.setOnClickListener { onDelete(lesson, position) }
        }
        
        override fun getItemCount(): Int = lessons.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val titleText: TextView = itemView.findViewById(R.id.lessonTitle)
            val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
        }
    }
}