package com.edu.teacher.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    fun applyTheme(mode: String) {
        when (mode) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_YES
            )
            "light" -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_NO
            )
            else -> AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
        }
    }

    fun toggleTheme(context: Context, prefs: SharedPreferences) {
        val currentMode = prefs.getString("theme", "light")
        val newMode = if (currentMode == "dark") "light" else "dark"
        
        prefs.edit().putString("theme", newMode).apply()
        applyTheme(newMode)
    }

    fun toggleThemeWithDebounce(enableDark: Boolean, debounceMs: Long = 400) {
        try {
            val prefs = com.edu.teacher.TeacherApp.instance.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            val theme = if (enableDark) "dark" else "light"
            prefs.edit().putString("theme", theme).apply()
            
            val nightMode = if (enableDark) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSavedTheme(prefs: SharedPreferences) {
        val savedTheme = prefs.getString("theme", "light") ?: "light"
        applyTheme(savedTheme)
    }

    fun isDarkMode(): Boolean {
        return try {
            AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        } catch (e: Exception) {
            val prefs = com.edu.teacher.TeacherApp.instance.getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            prefs.getString("theme", "light") == "dark"
        }
    }
}
