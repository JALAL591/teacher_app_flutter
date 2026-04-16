package com.edu.student

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.edu.student.data.preferences.StudentPreferences

class StudentApp : Application() {
    
    companion object {
        lateinit var instance: StudentApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        syncTheme()
    }
    
    private fun syncTheme() {
        val prefs = StudentPreferences(this)
        val theme = prefs.getTheme()
        
        val nightMode = when (theme) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
    
    fun toggleTheme(enableDark: Boolean) {
        val prefs = StudentPreferences(this)
        prefs.setTheme(if (enableDark) "dark" else "light")
        
        val nightMode = if (enableDark) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
