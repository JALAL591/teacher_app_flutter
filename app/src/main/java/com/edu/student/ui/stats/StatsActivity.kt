package com.edu.student.ui.stats

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.edu.teacher.R
import com.edu.teacher.databinding.*
import com.edu.student.data.repository.StudentRepository
import com.edu.student.domain.model.Subject
import com.edu.student.ui.common.SubjectProgressAdapter
import com.edu.student.ui.dashboard.DashboardActivity
import com.edu.student.ui.settings.SettingsActivity

class StatsActivity : AppCompatActivity() {
    
    private lateinit var binding: StudentActivityStatsBinding
    private lateinit var repository: StudentRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = StudentActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        repository = StudentRepository(this)
        
        setupViews()
        loadData()
    }
    
    override fun onResume() {
        super.onResume()
        loadData()
    }
    
    private fun setupViews() {
        binding.backButton.setOnClickListener { finish() }
        
        if (::binding.isInitialized) {
            binding.bottomNav.setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    }
                    R.id.nav_stats -> { }
                    R.id.nav_settings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        finish()
                    }
                }
                true
            }
            binding.bottomNav.selectedItemId = R.id.nav_stats
        }
    }
    
    private fun loadData() {
        val student = repository.getStudent()
        val stats = repository.getStats()
        
        binding.studentName.text = student?.name ?: "طالب"
        
        binding.starsCount.text = stats.stars.toString()
        binding.levelCount.text = stats.level.toString()
        
        val progressPercent = stats.stars % 100
        binding.progressBar.progress = progressPercent
        binding.progressText.text = "التقدم: $progressPercent%"
        
        binding.completedCount.text = stats.completedCount.toString()
        
        val subjects = repository.getSubjects()
        
        if (subjects.isEmpty()) {
            binding.subjectsList.visibility = View.GONE
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.subjectsList.visibility = View.VISIBLE
            binding.emptyText.visibility = View.GONE
            
            binding.subjectsList.layoutManager = LinearLayoutManager(this)
            binding.subjectsList.adapter = SubjectProgressAdapter(subjects, repository)
        }
    }
    
    // TODO: Add badge views to XML
    // private fun updateBadges(stars: Int) {
    //     binding.badge1Active.visibility = if (stars >= 1) View.VISIBLE else View.INVISIBLE
    //     binding.badge2Active.visibility = if (stars >= 50) View.VISIBLE else View.INVISIBLE
    //     binding.badge3Active.visibility = if (stars >= 150) View.VISIBLE else View.INVISIBLE
    //     binding.badge4Active.visibility = if (stars >= 500) View.VISIBLE else View.INVISIBLE
    // }
}
