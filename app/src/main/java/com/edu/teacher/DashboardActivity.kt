package com.edu.teacher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

class DashboardActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var teacherNameText: TextView
    private lateinit var teacherIdText: TextView
    private lateinit var copyButton: Button
    private lateinit var statsStudents: TextView
    private lateinit var statsClasses: TextView
    private lateinit var statsPoints: TextView
    private lateinit var classesRecyclerView: RecyclerView
    private lateinit var addClassButton: FloatingActionButton
    private lateinit var settingsButton: Button
    private lateinit var logoutButton: Button
    private lateinit var bottomNav: BottomNav

    private var teacherInfo = JSONObject()
    private var classesList = mutableListOf<JSONObject>()
    private var isCopied = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)

        initViews()
        loadData()
        setupListeners()
    }

    private fun initViews() {
        teacherNameText = findViewById(R.id.teacherNameText)
        teacherIdText = findViewById(R.id.teacherIdText)
        copyButton = findViewById(R.id.copyButton)
        statsStudents = findViewById(R.id.statsStudents)
        statsClasses = findViewById(R.id.statsClasses)
        statsPoints = findViewById(R.id.statsPoints)
        classesRecyclerView = findViewById(R.id.classesRecyclerView)
        addClassButton = findViewById(R.id.addClassButton)
        settingsButton = findViewById(R.id.settingsButton)
        logoutButton = findViewById(R.id.logoutButton)
        bottomNav = findViewById(R.id.bottomNav)

        classesRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadData() {
        val teacherInfoStr = prefs.getString("teacher_info", null)
        if (teacherInfoStr != null) {
            teacherInfo = JSONObject(teacherInfoStr)
            val name = teacherInfo.optString("name", "المعلم")
            val id = teacherInfo.optString("id", "TCH-001")
            teacherNameText.text = "أهلاً، ${name.split(" ")[0]} 👋"
            teacherIdText.text = id

            val classesArray = teacherInfo.optJSONArray("classes")
            classesList.clear()
            if (classesArray != null) {
                for (i in 0 until classesArray.length()) {
                    classesList.add(classesArray.getJSONObject(i))
                }
            }
            statsClasses.text = classesList.size.toString()
            statsStudents.text = "0"
            statsPoints.text = "0"

            classesRecyclerView.adapter = ClassesAdapter(classesList) { classData, position ->
                val intent = Intent(this, ClassManagerActivity::class.java)
                intent.putExtra("class_id", classData.optString("id"))
                intent.putExtra("class_subject", classData.optString("subject"))
                intent.putExtra("class_grade", classData.optString("grade"))
                intent.putExtra("class_section", classData.optString("section"))
                startActivity(intent)
            }
        }
    }

    private fun setupListeners() {
        copyButton.setOnClickListener { copyTeacherId() }
        addClassButton.setOnClickListener { startActivity(Intent(this, AddClassActivity::class.java)) }
        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        logoutButton.setOnClickListener { logout() }

        bottomNav.setOnItemSelectedListener { position ->
            when (position) {
                1 -> startActivity(Intent(this, StatsActivity::class.java))
                2 -> startActivity(Intent(this, SyncActivity::class.java))
                3 -> startActivity(Intent(this, SettingsActivity::class.java))
                -1 -> startActivity(Intent(this, AddLessonActivity::class.java))
            }
        }
    }

    private fun copyTeacherId() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("teacher_id", teacherIdText.text)
        clipboard.setPrimaryClip(clip)
        isCopied = true
        copyButton.text = "تم النسخ ✓"
        copyButton.setBackgroundColor(resources.getColor(R.color.emerald_500, null))
        copyButton.postDelayed({
            isCopied = false
            copyButton.text = "نسخ الكود"
            copyButton.setBackgroundColor(resources.getColor(R.color.white, null))
        }, 2000)
        Toast.makeText(this, "✅ تم نسخ المعرف", Toast.LENGTH_SHORT).show()
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("هل أنت متأكد من تسجيل الخروج؟")
            .setPositiveButton("نعم") { _, _ ->
                prefs.edit().clear().apply()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun getEmoji(subject: String): String {
        val s = subject.lowercase()
        return when {
            s.contains("قرآن") -> "🌙"
            s.contains("إسلام") -> "🕌"
            s.contains("رياضيات") -> "📐"
            s.contains("علوم") -> "🧪"
            s.contains("عربي") -> "✍️"
            s.contains("انجليزي") -> "🔤"
            else -> "📚"
        }
    }

    inner class ClassesAdapter(
        private val classes: List<JSONObject>,
        private val onItemClick: (JSONObject, Int) -> Unit
    ) : RecyclerView.Adapter<ClassesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_class, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val classData = classes[position]
            holder.subjectText.text = classData.optString("subject", "مادة")
            holder.gradeText.text = classData.optString("grade", "الصف")
            holder.sectionText.text = "شعبة ${classData.optString("section", "أ")}"
            holder.emojiText.text = getEmoji(classData.optString("subject", ""))

            holder.deleteButton.setOnClickListener {
                AlertDialog.Builder(holder.itemView.context)
                    .setTitle("حذف الفصل")
                    .setMessage("هل أنت متأكد من حذف هذا الفصل نهائياً؟")
                    .setPositiveButton("حذف") { _, _ ->
                        deleteClass(position)
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }

            holder.itemView.setOnClickListener {
                onItemClick(classData, position)
            }
        }

        override fun getItemCount(): Int = classes.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val emojiText: TextView = itemView.findViewById(R.id.emojiText)
            val subjectText: TextView = itemView.findViewById(R.id.subjectText)
            val gradeText: TextView = itemView.findViewById(R.id.gradeText)
            val sectionText: TextView = itemView.findViewById(R.id.sectionText)
            val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
        }

        private fun deleteClass(position: Int) {
            val updatedList = classesList.toMutableList()
            updatedList.removeAt(position)
            classesList.clear()
            classesList.addAll(updatedList)

            val newArray = JSONArray()
            classesList.forEach { newArray.put(it) }
            teacherInfo.put("classes", newArray)
            prefs.edit().putString("teacher_info", teacherInfo.toString()).apply()

            notifyItemRemoved(position)
            statsClasses.text = classesList.size.toString()
            Toast.makeText(this@DashboardActivity, "✅ تم حذف الفصل", Toast.LENGTH_SHORT).show()
        }
    }
}