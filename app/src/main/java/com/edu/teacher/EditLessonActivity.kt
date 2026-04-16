package com.edu.teacher

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.edu.teacher.databinding.ActivityEditLessonBinding
import com.edu.teacher.databinding.ItemQuestionBinding
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EditLessonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditLessonBinding

    private var lessonId: String = ""
    private var teacherId: String = ""
    private var classesList = mutableListOf<JSONObject>()
    private var selectedClassId = ""
    private var selectedUnit = ""
    private var imageUri: Uri? = null
    private var videoUri: Uri? = null
    private var questionsList = mutableListOf<QuestionItem>()

    private val allUnits by lazy { resources.getStringArray(R.array.unit_names).toList() }

    data class QuestionItem(var id: String, var type: String, var text: String, var options: MutableList<String>, var answer: String)

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUri = it; Glide.with(this).load(it).into(binding.imagePreview); binding.imagePreview.visibility = View.VISIBLE }
    }
    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { videoUri = it; binding.videoPreview.text = getString(R.string.status_video_ready); binding.videoPreview.visibility = View.VISIBLE }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditLessonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        teacherId = DataManager.getTeacherId(this) ?: run { finish(); return }
        lessonId = intent.getStringExtra("lesson_id") ?: ""

        initViews()
        loadData()
    }

    private fun initViews() {
        try {
            binding.pageTitle.text = getString(R.string.title_edit_lesson)
            binding.lessonIdText.text = getString(R.string.label_lesson_id, lessonId)
            binding.backButton.setOnClickListener { finish() }
            binding.saveButton.setOnClickListener { saveLesson() }
            binding.imageContainer.setOnClickListener { pickImageLauncher.launch("image/*") }
            binding.videoContainer.setOnClickListener { pickVideoLauncher.launch("video/*") }
            binding.addChoiceButton.setOnClickListener { addQuestion("choice") }
            binding.addBooleanButton.setOnClickListener { addQuestion("boolean") }
            binding.addEssayButton.setOnClickListener { addQuestion("essay") }
            binding.questionsRecyclerView.layoutManager = LinearLayoutManager(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadData() {
        val subjects = DataManager.getSubjects(this, teacherId)
        classesList.clear()
        subjects.forEach { subject ->
            val subjectId = subject.optString("id")
            val subjectName = subject.optString("name", getString(R.string.default_subject))
            val classes = DataManager.getClasses(this, teacherId, subjectId)
            classes.forEach { cls -> cls.put("subjectName", subjectName as Any); classesList.add(cls) }
        }

        setupSpinners()

        val currentLesson = classesList.flatMap { cls ->
            DataManager.getLessons(this, teacherId, cls.optString("id"))
        }.find { it.optString("id") == lessonId }

        if (currentLesson != null) {
            selectedClassId = currentLesson.optString("classId", "")
            selectedUnit = currentLesson.optString("unit", allUnits[0])
            binding.titleInput.setText(currentLesson.optString("title", ""))
            binding.contentInput.setText(currentLesson.optString("content", ""))

            val imageUrl = currentLesson.optString("image", "")
            if (imageUrl.isNotEmpty()) { Glide.with(this).load(imageUrl).into(binding.imagePreview); binding.imagePreview.visibility = View.VISIBLE }

            val questionsArray = currentLesson.optJSONArray("questions")
            if (questionsArray != null) for (i in 0 until questionsArray.length()) {
                val q = questionsArray.getJSONObject(i)
                questionsList.add(QuestionItem(
                    id = q.optString("id", UUID.randomUUID().toString()), type = q.optString("type", "choice"),
                    text = q.optString("text", ""),
                    options = mutableListOf<String>().apply {
                        val opts = q.optJSONArray("options")
                        if (opts != null) for (j in 0 until opts.length()) add(opts.optString(j))
                    },
                    answer = q.optString("answer", "1")
                ))
            }
        } else { Toast.makeText(this, getString(R.string.error_lesson_not_found), Toast.LENGTH_SHORT).show(); finish() }

        binding.questionsRecyclerView.adapter = QuestionsAdapter(questionsList) { position ->
            questionsList.removeAt(position); binding.questionsRecyclerView.adapter?.notifyItemRemoved(position)
        }
    }

    private fun setupSpinners() {
        val classNames = classesList.map { "${it.optString("subjectName", getString(R.string.default_subject))} - ${it.optString("name", getString(R.string.default_class))}" }
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.classSpinner.adapter = classAdapter
        val classPosition = classesList.indexOfFirst { it.optString("id") == selectedClassId }
        if (classPosition >= 0) binding.classSpinner.setSelection(classPosition)
        binding.classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < classesList.size) selectedClassId = classesList[position].optString("id")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allUnits)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.unitSpinner.adapter = unitAdapter
        val unitPosition = allUnits.indexOf(selectedUnit)
        if (unitPosition >= 0) binding.unitSpinner.setSelection(unitPosition)
        binding.unitSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedUnit = allUnits[position] }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun addQuestion(type: String) {
        questionsList.add(QuestionItem(
            id = UUID.randomUUID().toString(), type = type, text = "",
            options = when (type) { "choice" -> mutableListOf("", "", ""); "boolean" -> mutableListOf(getString(R.string.answer_true), getString(R.string.answer_false)); else -> mutableListOf() },
            answer = when (type) { "boolean" -> getString(R.string.answer_true); else -> "1" }
        ))
        binding.questionsRecyclerView.adapter?.notifyItemInserted(questionsList.size - 1)
    }

    private fun saveLesson() {
        val title = binding.titleInput.text.toString().trim()
        if (title.isEmpty()) { Toast.makeText(this, getString(R.string.warning_enter_lesson_title), Toast.LENGTH_SHORT).show(); return }
        if (selectedClassId.isEmpty()) { Toast.makeText(this, getString(R.string.warning_select_class), Toast.LENGTH_SHORT).show(); return }

        binding.saveButton.isEnabled = false; binding.saveButton.alpha = 0.5f

        val questionsArray = JSONArray()
        questionsList.forEach { q ->
            val qObj = JSONObject()
            qObj.put("id", q.id as Any); qObj.put("type", q.type as Any); qObj.put("text", q.text as Any)
            qObj.put("options", JSONArray(q.options) as Any); qObj.put("answer", q.answer as Any)
            questionsArray.put(qObj)
        }

        val updatedLesson = JSONObject().apply {
            put("id", lessonId as Any); put("teacherId", teacherId as Any); put("classId", selectedClassId as Any)
            put("unit", selectedUnit as Any); put("title", title as Any); put("content", binding.contentInput.text.toString() as Any)
            put("image", (imageUri?.toString() ?: "") as Any); put("video", (videoUri?.toString() ?: "") as Any)
            put("questions", questionsArray as Any)
            put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()) as Any)
            put("isPublished", true as Any)
        }

        DataManager.updateLesson(this, teacherId, selectedClassId, lessonId, updatedLesson)
        Toast.makeText(this, getString(R.string.toast_lesson_updated), Toast.LENGTH_LONG).show()
        finish()
    }

    inner class QuestionsAdapter(
        private val questions: MutableList<QuestionItem>, private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
            val binding = ItemQuestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return QuestionViewHolder(binding)
        }
        override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
            val q = questions[position]
            holder.binding.questionType.text = when (q.type) {
                "choice" -> getString(R.string.question_type_mcq)
                "boolean" -> getString(R.string.question_type_tf)
                else -> getString(R.string.question_type_essay)
            }
            holder.binding.questionText.setText(q.text)
            holder.binding.questionText.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { q.text = s.toString() }
            })
            holder.binding.deleteButton.setOnClickListener { onDelete(position) }
        }
        override fun getItemCount(): Int = questions.size
        inner class QuestionViewHolder(val binding: ItemQuestionBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
