package com.edu.teacher

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.edu.teacher.utils.ThemeManager

class TeacherApp : Application() {
    
    var teacherServer: TeacherServer? = null
    
    companion object {
        private var _instance: TeacherApp? = null
        
        val instance: TeacherApp
            get() = _instance ?: throw IllegalStateException(
                "TeacherApp has not been initialized yet. " +
                "Ensure Application.onCreate() has been called."
            )
        
        fun isInitialized(): Boolean = _instance != null
        
        fun getInstanceSafe(): TeacherApp? = _instance
        
        fun toggleTheme(enableDark: Boolean) {
            if (!isInitialized()) return
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
            ThemeManager.loadSavedTheme(prefs)
        }
        
        fun isDarkMode(): Boolean {
            return ThemeManager.isDarkMode()
        }
        
        fun applyThemeWithDebounce(enableDark: Boolean, debounceMs: Long = 400) {
            ThemeManager.toggleThemeWithDebounce(enableDark, debounceMs)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        _instance = this
        syncTheme()
    }
}
