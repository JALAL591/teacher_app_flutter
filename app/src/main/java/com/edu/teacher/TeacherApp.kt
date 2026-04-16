package com.edu.teacher

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class TeacherApp : Application() {
    
    companion object {
        lateinit var instance: TeacherApp
            private set
        
        fun toggleTheme(enableDark: Boolean) {
            val prefs = instance.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            val theme = if (enableDark) "dark" else "light"
            prefs.edit().putString("theme", theme).apply()
            
            val nightMode = if (enableDark) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
        
        fun syncTheme() {
            val prefs = instance.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            val theme = prefs.getString("theme", "light") ?: "light"
            
            val nightMode = when (theme) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
            
            if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
                AppCompatDelegate.setDefaultNightMode(nightMode)
            }
        }
        
        fun isDarkMode(): Boolean {
            return try {
                AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            } catch (e: Exception) {
                val prefs = instance.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
                prefs.getString("theme", "light") == "dark"
            }
        }
        
        fun applyThemeWithDebounce(enableDark: Boolean, debounceMs: Long = 400) {
            toggleTheme(enableDark)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        syncTheme()
    }
}
