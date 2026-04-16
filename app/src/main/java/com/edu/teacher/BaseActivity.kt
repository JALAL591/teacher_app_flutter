package com.edu.teacher

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity

abstract class BaseActivity : AppCompatActivity() {

    private var themeInitComplete = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStoredTheme()
        themeInitComplete = true
    }

    private fun applyStoredTheme() {
        try {
            val prefs = getSharedPreferences("teacher_app", Context.MODE_PRIVATE)
            val theme = prefs.getString("theme", "light")
            val targetMode = if (theme == "dark") {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            val currentMode = AppCompatDelegate.getDefaultNightMode()
            if (currentMode != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isThemeInitComplete(): Boolean = themeInitComplete

    fun toggleThemeWithFlickPrevention(enableDark: Boolean, debounceMs: Long = 300) {
        TeacherApp.applyThemeWithDebounce(enableDark, debounceMs)
    }
}
