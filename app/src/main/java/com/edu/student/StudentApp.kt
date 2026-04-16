package com.edu.student

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.edu.student.data.preferences.StudentPreferences

class StudentApp : Application() {
    
    companion object {
        lateinit var instance: StudentApp
            private set
        
        fun applyTheme(mode: String) {
            when (mode) {
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        
        fun toggleTheme(enableDark: Boolean) {
            val prefs = StudentPreferences(instance)
            val theme = if (enableDark) "dark" else "light"
            prefs.setTheme(theme)
            applyTheme(theme)
        }
        
        fun isDarkMode(): Boolean {
            return try {
                AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            } catch (e: Exception) {
                val prefs = StudentPreferences(instance)
                prefs.getTheme() == "dark"
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        syncTheme()
    }
    
    private fun syncTheme() {
        val prefs = StudentPreferences(this)
        val theme = prefs.getTheme()
        applyTheme(theme)
    }
}
