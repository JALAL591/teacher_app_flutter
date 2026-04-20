package com.edu.teacher

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.edu.common.ModelManager
import com.edu.teacher.utils.ThemeManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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

        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this)
            android.util.Log.d("TeacherApp", "PDFBox initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("TeacherApp", "PDFBox init failed: ${e.message}")
        }

        syncTheme()
        
        createMediaDirectories()
        
        GlobalScope.launch {
            ModelManager.copyModelsAsync(this@TeacherApp) { model, progress ->
                android.util.Log.d("TeacherApp", "Copying $model: $progress%")
            }
            ModelManager.logModelsInfo(this@TeacherApp)
        }
    }
    
    private fun createMediaDirectories() {
        val directories = listOf("images", "videos", "pdfs", "lessons")
        
        directories.forEach { dir ->
            val file = java.io.File(filesDir, dir)
            if (!file.exists()) {
                file.mkdirs()
                android.util.Log.d("TeacherApp", "Created $dir directory: ${file.absolutePath}")
            }
        }
    }
    
    fun getLessonMediaDir(
        teacherId: String,
        subjectId: String,
        classId: String,
        grade: String,
        section: String,
        lessonId: String
    ): java.io.File {
        val path = "lessons/$teacherId/$subjectId/$classId/${grade}_$section/$lessonId"
        val dir = java.io.File(filesDir, path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    fun getImagesDir(
        teacherId: String,
        subjectId: String,
        classId: String,
        grade: String,
        section: String,
        lessonId: String
    ): java.io.File {
        val dir = java.io.File(
            getLessonMediaDir(teacherId, subjectId, classId, grade, section, lessonId),
            "images"
        )
        dir.mkdirs()
        return dir
    }
    
    fun getVideoDir(
        teacherId: String,
        subjectId: String,
        classId: String,
        grade: String,
        section: String,
        lessonId: String
    ): java.io.File {
        val dir = java.io.File(
            getLessonMediaDir(teacherId, subjectId, classId, grade, section, lessonId),
            "video"
        )
        dir.mkdirs()
        return dir
    }
    
    fun getPdfDir(
        teacherId: String,
        subjectId: String,
        classId: String,
        grade: String,
        section: String,
        lessonId: String
    ): java.io.File {
        val dir = java.io.File(
            getLessonMediaDir(teacherId, subjectId, classId, grade, section, lessonId),
            "pdf"
        )
        dir.mkdirs()
        return dir
    }
    
    fun deleteLessonMedia(
        teacherId: String,
        subjectId: String,
        classId: String,
        grade: String,
        section: String,
        lessonId: String
    ) {
        val dir = getLessonMediaDir(teacherId, subjectId, classId, grade, section, lessonId)
        if (dir.exists()) {
            dir.deleteRecursively()
            android.util.Log.d("TeacherApp", "Deleted lesson media: $lessonId")
        }
    }
    
    fun deleteClassMedia(
        teacherId: String,
        subjectId: String,
        classId: String,
        grade: String,
        section: String
    ) {
        val path = "lessons/$teacherId/$subjectId/$classId/${grade}_$section"
        val dir = java.io.File(filesDir, path)
        if (dir.exists()) {
            dir.deleteRecursively()
            android.util.Log.d("TeacherApp", "Deleted class media: ${grade}_$section")
        }
    }
}
