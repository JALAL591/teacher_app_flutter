package com.edu.teacher

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EditLessonActivity : AppCompatActivity() {

    // ==================== المتغيرات ====================

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var pageTitle: TextView
    private lateinit var lessonIdText: TextView
    private lateinit var saveButton: Button
    private lateinit var classSpinner: Spinner
    private lateinit var unitSpinner: Spinner
    private lateinit var titleInput: TextInputEditText
    private lateinit var contentInput: EditText
    private lateinit var imageContainer: View
    private lateinit var imagePreview: ImageView
    private lateinit var videoContainer: View
    private lateinit var videoPreview: TextView
    private lateinit var questionsRecyclerView: RecyclerView
    private lateinit var addChoiceButton: Button
    private lateinit var addBooleanButton: Button
    private lateinit var addEssayButton: Button

    private var lessonId: String = ""
    private var lessonType: String = "draft"
    private var teacherClasses = mutableListOf<JSONObject>()
    private var selectedClassId = ""
    private var selectedUnit = "الوحدة الأولى"
    private var imageUri: Uri? = null
    private var videoUri: Uri? = null
    private var questionsList = mutableListOf<QuestionItem>()
    private var isLoading = true
    private var isSaving = false

    private val allUnits = listOf(
        "الوحدة الأولى", "الوحدة الثانية", "الوحدة الثالثة",
        "الوحدة الرابعة", "الوحدة الخامسة", "الوحدة السادسة"
    )

    data class QuestionItem(
        var id: String,
        var type: String,
        var text: String,
        var options: MutableList<String>,
        var answer: String
    )

    companion object {
        private const val TEACHER_ID = "TCH-001"
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            Glide.with(this).load(it).into(imagePreview)
            imagePreview.visibility = View.VISIBLE
        }
    }

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            videoUri = it
            videoPreview.text = "🎥 فيديو جاهز"
            videoPreview.visibility = View.VISIBLE
        }
    }

    // ==================== دورة حياة النشاط ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_lesson)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        lessonId = intent.getStringExtra("lesson_id") ?: ""
        lessonType = intent.getStringExtra("lesson_type") ?: "draft"

        initViews()
        loadData()
    }

    // ==================== تهيئة العناصر ====================

    private fun initViews() {
        backButton = findViewById(R.id.backButton)
        pageTitle = findViewById(R.id.pageTitle)
        lessonIdText = findViewById(R.id.lessonIdText)
        saveButton = findViewById(R.id.saveButton)
        classSpinner = findViewById(R.id.classSpinner)
        unitSpinner = findViewById(R.id.unitSpinner)
        titleInput = findViewById(R.id.titleInput)
        contentInput = findViewById(R.id.contentInput)
        imageContainer = findViewById(R.id.imageContainer)
        imagePreview = findViewById(R.id.imagePreview)
        videoContainer = findViewById(R.id.videoContainer)
        videoPreview = findViewById(R.id.videoPreview)
        questionsRecyclerView = findViewById(R.id.questionsRecyclerView)
        addChoiceButton = findViewById(R.id.addChoiceButton)
        addBooleanButton = findViewById(R.id.addBooleanButton)
        addEssayButton = findViewById(R.id.addEssayButton)

        pageTitle.text = "تعديل وبث الدرس"
        lessonIdText.text = "ID: $lessonId"

        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveLesson() }

        imageContainer.setOnClickListener { pickImageLauncher.launch("image/*") }
        videoContainer.setOnClickListener { pickVideoLauncher.launch("video/*") }

        addChoiceButton.setOnClickListener { addQuestion("choice") }
        addBooleanButton.setOnClickListener { addQuestion("boolean") }
        addEssayButton.setOnClickListener { addQuestion("essay") }

        questionsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    // ==================== تحميل البيانات ====================

    private fun loadData() {
        val teacherInfoStr = prefs.getString("teacher_info", null)
        if (teacherInfoStr != null) {
            val teacherInfo = JSONObject(teacherInfoStr)
            val classesArray = teacherInfo.optJSONArray("classes")
            if (classesArray != null) {
                for (i in 0 until classesArray.length()) {
                    teacherClasses.add(classesArray.getJSONObject(i))
                }
            }
        }

        setupSpinners()

        // تحميل بيانات الدرس من SharedPreferences
        val storageKey = "lessons_$TEACHER_ID"
        val savedLessons = prefs.getString(storageKey, "[]")
        val lessonsArray = JSONArray(savedLessons)
        
        var currentLesson: JSONObject? = null
        for (i in 0 until lessonsArray.length()) {
            val lesson = lessonsArray.getJSONObject(i)
            if (lesson.optString("id") == lessonId) {
                currentLesson = lesson
                break
            }
        }

        if (currentLesson != null) {
            selectedClassId = currentLesson.optString("classId", "")
            selectedUnit = currentLesson.optString("unit", allUnits[0])
            titleInput.setText(currentLesson.optString("title", ""))
            contentInput.setText(currentLesson.optString("content", ""))
            
            val imageUrl = currentLesson.optString("image", "")
            if (imageUrl != null && imageUrl.isNotEmpty()) {
                Glide.with(this).load(imageUrl).into(imagePreview)
                imagePreview.visibility = View.VISIBLE
            }
            
            val questionsArray = currentLesson.optJSONArray("questions")
            if (questionsArray != null) {
                for (i in 0 until questionsArray.length()) {
                    val q = questionsArray.getJSONObject(i)
                    questionsList.add(QuestionItem(
                        id = q.optString("id", UUID.randomUUID().toString()),
                        type = q.optString("type", "choice"),
                        text = q.optString("text", ""),
                        options = mutableListOf<String>().apply {
                            val opts = q.optJSONArray("options")
                            if (opts != null) {
                                for (j in 0 until opts.length()) {
                                    add(opts.optString(j))
                                }
                            }
                        },
                        answer = q.optString("answer", "1")
                    ))
                }
            }
        } else {
            Toast.makeText(this, "تعذر العثور على الدرس", Toast.LENGTH_SHORT).show()
            finish()
        }

        setupQuestionsAdapter()
        isLoading = false
    }

    // ==================== إعداد القوائم المنسدلة ====================

    private fun setupSpinners() {
        val classNames = teacherClasses.map {
            "${it.optString("subject")} - ${it.optString("grade")}"
        }
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        classSpinner.adapter = classAdapter

        val classPosition = teacherClasses.indexOfFirst { it.optString("id") == selectedClassId }
        if (classPosition >= 0) classSpinner.setSelection(classPosition)

        classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedClassId = teacherClasses[position].optString("id")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allUnits)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        unitSpinner.adapter = unitAdapter
        
        val unitPosition = allUnits.indexOf(selectedUnit)
        if (unitPosition >= 0) unitSpinner.setSelection(unitPosition)

        unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedUnit = allUnits[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ==================== إضافة سؤال جديد ====================

    private fun addQuestion(type: String) {
        val newQuestion = QuestionItem(
            id = UUID.randomUUID().toString(),
            type = type,
            text = "",
            options = when (type) {
                "choice" -> mutableListOf("", "", "")
                "boolean" -> mutableListOf("صح", "خطأ")
                else -> mutableListOf()
            },
            answer = when (type) {
                "boolean" -> "صح"
                else -> "1"
            }
        )
        questionsList.add(newQuestion)
        questionsRecyclerView.adapter?.notifyItemInserted(questionsList.size - 1)
    }

    // ==================== إعداد Adapter الأسئلة ====================

    private fun setupQuestionsAdapter() {
        questionsRecyclerView.adapter = QuestionsAdapter(questionsList) { position ->
            questionsList.removeAt(position)
            questionsRecyclerView.adapter?.notifyItemRemoved(position)
        }
    }

    // ==================== حفظ الدرس ====================

    private fun saveLesson() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "يرجى كتابة عنوان الدرس", Toast.LENGTH_SHORT).show()
            return
        }

        isSaving = true
        saveButton.isEnabled = false
        saveButton.text = "جاري الحفظ..."

        val selectedClass = teacherClasses.find { it.optString("id") == selectedClassId }
        
        val questionsArray = JSONArray()
        questionsList.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id)
            qObj.put("type", q.type)
            qObj.put("text", q.text)
            qObj.put("options", JSONArray(q.options))
            qObj.put("answer", q.answer)
            questionsArray.put(qObj)
        }

        val updatedLesson = JSONObject().apply {
            put("id", lessonId)
            put("tid", TEACHER_ID)
            put("classId", selectedClassId)
            put("subjectId", selectedClassId)
            put("subjectTitle", selectedClass?.optString("subject") ?: "عام")
            put("unit", selectedUnit)
            put("title", title)
            put("description", contentInput.text.toString())
            put("image", imageUri?.toString())
            put("video", videoUri?.toString())
            put("questions", questionsArray)
            put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            put("isPublished", true)
        }

        // حفظ في SharedPreferences
        val storageKey = "lessons_$TEACHER_ID"
        val savedLessons = prefs.getString(storageKey, "[]")
        val lessonsArray = JSONArray(savedLessons)
        
        val existingIndex = (0 until lessonsArray.length()).firstOrNull { 
            lessonsArray.getJSONObject(it).optString("id") == lessonId
        }
        
        if (existingIndex != null) {
            lessonsArray.put(existingIndex, updatedLesson)
        } else {
            lessonsArray.put(0, updatedLesson)
        }
        
        prefs.edit().putString(storageKey, lessonsArray.toString()).apply()

        Toast.makeText(this, "✅ تم تحديث وبث الدرس بنجاح", Toast.LENGTH_LONG).show()
        
        isSaving = false
        finish()
    }

    // ==================== Adapter للأسئلة ====================

    inner class QuestionsAdapter(
        private val questions: MutableList<QuestionItem>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_question, parent, false)
            return QuestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
            val q = questions[position]
            holder.questionType.text = when (q.type) {
                "choice" -> "اختيارات"
                "boolean" -> "صح/خطأ"
                else -> "مقالي"
            }
            holder.questionText.setText(q.text)
            
            holder.questionText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    q.text = s.toString()
                }
            })
            
            holder.deleteButton.setOnClickListener { onDelete(position) }

            if (q.type == "choice") {
                // سيتم عرض خيارات الاختيار
            }
        }

        override fun getItemCount(): Int = questions.size

        inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val questionType: TextView = itemView.findViewById(R.id.questionType)
            val questionText: EditText = itemView.findViewById(R.id.questionText)
            val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
        }
    }
}
