package com.edu.teacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.databinding.ActivityStatsBinding
import com.edu.teacher.ui.stats.LessonSubmissionsActivity
import com.edu.teacher.ui.stats.StatsSubjectAdapter
import org.json.JSONArray
import org.json.JSONObject

class StatsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityStatsBinding
    private lateinit var teacherId: String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        teacherId = DataManager.getTeacherId(this) ?: run {
            finish()
            return
        }
        
        setupViews()
        loadStatsData()
    }
    
    override fun onResume() {
        super.onResume()
        loadStatsData()
    }
    
    private fun setupViews() {
        binding.backButton.setOnClickListener { finish() }
        binding.subjectsRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun loadStatsData() {
        if (teacherId.isEmpty()) {
            binding.backButton.visibility = View.GONE
            binding.subjectsRecyclerView.visibility = View.GONE
            return
        }
        
        val fullData = DataManager.getFullTeacherData(this, teacherId)
        val subjectsArray = fullData.optJSONArray("subjects") ?: JSONArray()
        
        if (subjectsArray.length() == 0) {
            binding.subjectsRecyclerView.visibility = View.GONE
        } else {
            binding.subjectsRecyclerView.visibility = View.VISIBLE
            
            val adapter = StatsSubjectAdapter(subjectsArray) { _, _, lesson ->
                val intent = Intent(this, LessonSubmissionsActivity::class.java)
                intent.putExtra("lesson", lesson.toString())
                intent.putExtra("submissions", lesson.optJSONArray("submissions")?.toString() ?: "[]")
                intent.putExtra("stats", lesson.optJSONObject("stats")?.toString() ?: "{}")
                startActivity(intent)
            }
            binding.subjectsRecyclerView.adapter = adapter
        }
    }
}