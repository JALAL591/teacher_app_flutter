package com.edu.teacher

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.edu.teacher.databinding.ActivityAddClassBinding
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class AddClassActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddClassBinding

    private val allGrades = listOf(
        "الأول الابتدائي", "الثاني الابتدائي", "الثالث الابتدائي",
        "الرابع الابتدائي", "الخامس الابتدائي", "السادس الابتدائي",
        "الأول المتوسط", "الثاني المتوسط", "الثالث المتوسط",
        "الأول الثانوي", "الثاني الثانوي", "الثالث الثانوي"
    )
    private val sections = listOf("أ", "ب", "ج", "د", "هـ")
    private var selectedGrade = ""
    private var selectedSection = "أ"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddClassBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGradeSpinner()
        setupSectionsButtons()

        binding.backButton.setOnClickListener { finish() }
        binding.saveButton.setOnClickListener { saveClass() }
    }

    private fun setupGradeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, allGrades)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.gradeSpinner.adapter = adapter
        binding.gradeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                selectedGrade = allGrades[p2]
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupSectionsButtons() {
        binding.sectionsContainer.removeAllViews()
        sections.forEach { section ->
            val btn = MaterialButton(this).apply {
                text = section
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    resources.getDimensionPixelSize(R.dimen.button_height_sm)
                ).apply {
                    weight = 1f
                    setMargins(4, 0, 4, 0)
                }
                gravity = Gravity.CENTER
                isAllCaps = false
                setBackgroundResource(R.drawable.section_btn_unselected)
                setTextColor(ContextCompat.getColor(context, R.color.tg_hint))
                textSize = 16f
                setPadding(0, 0, 0, 0)
                setOnClickListener { selectSection(section, this) }
            }
            binding.sectionsContainer.addView(btn)
        }
        // تحديد أول شعبة افتراضياً
        selectSection("أ", binding.sectionsContainer.getChildAt(0) as MaterialButton)
    }

    private fun selectSection(section: String, button: MaterialButton) {
        selectedSection = section
        // إعادة تعيين جميع الأزرار
        for (i in 0 until binding.sectionsContainer.childCount) {
            val child = binding.sectionsContainer.getChildAt(i)
            if (child is MaterialButton) {
                child.setBackgroundResource(R.drawable.section_btn_unselected)
                child.setTextColor(ContextCompat.getColor(this, R.color.tg_hint))
            }
        }
        // تمييز الزر المحدد
        button.setBackgroundResource(R.drawable.section_btn_selected)
        button.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun saveClass() {
        val teacherId = DataManager.getTeacherId(this)
        if (teacherId == null) {
            Toast.makeText(this, R.string.error_teacher_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val subjectName = binding.subjectInput.text.toString().trim()
        if (subjectName.isEmpty()) {
            binding.subjectInput.error = getString(R.string.warning_enter_subject)
            binding.subjectInput.requestFocus()
            return
        }
        if (selectedGrade.isEmpty()) {
            Toast.makeText(this, R.string.warning_select_grade, Toast.LENGTH_SHORT).show()
            return
        }

        // Find or create subject
        val subjects = DataManager.getSubjects(this, teacherId)
        var subject = subjects.find { it.optString("name").equals(subjectName, ignoreCase = true) }

        if (subject == null) {
            subject = JSONObject().apply {
                put("id", DataManager.generateSubjectId() as Any)
                put("name", subjectName as Any)
                put("teacherId", teacherId as Any)
                put("emoji", getEmoji(subjectName) as Any)
                put("createdAt", DataManager.getSyncTimestamp() as Any)
            }
            DataManager.addSubject(this, teacherId, subject)
        }

        val subjectId = subject.optString("id")

        // Create new class
        val newClass = JSONObject().apply {
            put("id", DataManager.generateClassId() as Any)
            put("subjectId", subjectId as Any)
            put("teacherId", teacherId as Any)
            put("name", "$subjectName - $selectedGrade" as Any)
            put("grade", selectedGrade as Any)
            put("section", selectedSection as Any)
            put("createdAt", DataManager.getSyncTimestamp() as Any)
        }

        DataManager.addClass(this, teacherId, subjectId, newClass)
        Toast.makeText(this, R.string.toast_class_added, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun getEmoji(subject: String): String {
        val s = subject.lowercase()
        return when {
            s.contains("قرآن") || s.contains("إسلام") -> EmojiConstants.QURAN_ISLAMIC
            s.contains("رياضيات") || s.contains("حساب") -> EmojiConstants.MATH
            s.contains("علوم") || s.contains("فيزياء") || s.contains("كيمياء") -> EmojiConstants.SCIENCE
            s.contains("عربي") || s.contains("لغة") -> EmojiConstants.ARABIC
            s.contains("إنجليزي") || s.contains("انجليزي") -> EmojiConstants.ENGLISH
            s.contains("تاريخ") || s.contains("جغرافيا") -> EmojiConstants.HISTORY_GEOGRAPHY
            s.contains("رياضة") || s.contains("بدنية") -> EmojiConstants.PE_SPORTS
            s.contains("فن") || s.contains("رسم") -> EmojiConstants.ART
            else -> EmojiConstants.DEFAULT_SUBJECT
        }
    }
}
