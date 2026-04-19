package com.edu.student

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.edu.common.ModelManager
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.services.TeacherClient
import com.edu.student.utils.PermissionHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class StudentApp : Application() {
    
    companion object {
        private var _instance: StudentApp? = null
        
        val instance: StudentApp
            get() = _instance ?: throw IllegalStateException(
                "StudentApp has not been initialized yet. " +
                "Ensure Application.onCreate() has been called."
            )
        
        fun isInitialized(): Boolean = _instance != null
        
        fun getInstanceSafe(): StudentApp? = _instance
        
        private var _teacherClient: TeacherClient? = null
        
        @Synchronized
        fun getTeacherClient(context: Context): TeacherClient {
            if (_teacherClient == null) {
                _teacherClient = TeacherClient(context.applicationContext)
                _teacherClient?.init()
            }
            return _teacherClient!!
        }
        
        private fun applyTheme(mode: String) {
            when (mode) {
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        
        fun toggleTheme(enableDark: Boolean) {
            if (!isInitialized()) return
            try {
                val prefs = StudentPreferences(instance)
                val theme = if (enableDark) "dark" else "light"
                prefs.setTheme(theme)
                applyTheme(theme)
            } catch (e: Exception) { }
        }
        
        fun isDarkMode(): Boolean {
            return try {
                if (!isInitialized()) return false
                AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
            } catch (e: Exception) {
                try {
                    val prefs = StudentPreferences(instance)
                    prefs.getTheme() == "dark"
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        _instance = this
        getTeacherClient(this)
        syncTheme()
        createNotificationChannel()
        
        GlobalScope.launch {
            ModelManager.copyModelsAsync(this@StudentApp) { model, progress ->
                android.util.Log.d("StudentApp", "Copying $model: $progress%")
            }
            ModelManager.logModelsInfo(this@StudentApp)
        }
    }
    
    private fun syncTheme() {
        val prefs = StudentPreferences(this)
        val theme = prefs.getTheme()
        applyTheme(theme)
    }
    
    private fun createNotificationChannel() {
        PermissionHelper.createNotificationChannel(this)
    }
}
