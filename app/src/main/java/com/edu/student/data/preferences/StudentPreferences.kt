package com.edu.student.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.edu.student.domain.model.Student
import com.edu.student.domain.model.StudentStats
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class StudentPreferences(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "student_prefs"
        private const val KEY_STUDENT_DATA = "student_data"
        private const val KEY_ACTIVATED = "activated"
        private const val KEY_COMPLETED_ACTIONS = "completed_actions"
        private const val KEY_THEME = "theme"
        private const val KEY_TEACHER_IP = "teacher_ip"
        private const val KEY_ASSIGNED_TEACHER_ID = "assigned_teacher_id"
        private const val KEY_STUDENT_CACHE_PREFIX = "student_cache_"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SAVED_USER_ID = "saved_user_id"
        private const val KEY_SAVED_PASSWORD = "saved_password"
    }
    
    // ==================== Login State ====================
    fun loginStudent(studentId: String, studentName: String, grade: String, section: String) {
        prefs.edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
        prefs.edit().putString("current_student_id", studentId).apply()
        prefs.edit().putString("current_student_name", studentName).apply()
        prefs.edit().putString("current_grade", grade).apply()
        prefs.edit().putString("current_section", section).apply()
        prefs.edit().putLong("last_login_time", System.currentTimeMillis()).apply()
    }
    
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun logout() {
        prefs.edit().remove(KEY_IS_LOGGED_IN).apply()
        prefs.edit().remove("current_student_id").apply()
        prefs.edit().remove("current_student_name").apply()
        prefs.edit().remove("current_grade").apply()
        prefs.edit().remove("current_section").apply()
        prefs.edit().remove("last_login_time").apply()
    }
    
    fun getCurrentStudent(): Student? {
        return if (isLoggedIn()) getStudent() else null
    }
    
    // ==================== Remember Me ====================
    fun saveLoginCredentials(userId: String, password: String, rememberMe: Boolean) {
        prefs.edit().putString(KEY_SAVED_USER_ID, userId).apply()
        
        if (rememberMe && password.isNotEmpty()) {
            val encodedPassword = android.util.Base64.encodeToString(
                password.toByteArray(), 
                android.util.Base64.NO_WRAP
            )
            prefs.edit().putString(KEY_SAVED_PASSWORD, encodedPassword).apply()
            prefs.edit().putBoolean(KEY_REMEMBER_ME, true).apply()
        } else {
            prefs.edit().remove(KEY_SAVED_PASSWORD).apply()
            prefs.edit().putBoolean(KEY_REMEMBER_ME, false).apply()
        }
    }
    
    fun getSavedCredentials(): Pair<String?, String?> {
        val userId = prefs.getString(KEY_SAVED_USER_ID, null)
        val encodedPassword = prefs.getString(KEY_SAVED_PASSWORD, null)
        val password = encodedPassword?.let {
            try {
                String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
            } catch (e: Exception) { null }
        }
        return Pair(userId, password)
    }
    
    fun isRememberMeEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER_ME, false)
    }
    
    fun isActivated(): Boolean = prefs.getBoolean(KEY_ACTIVATED, false)
    
    fun setActivated(activated: Boolean) {
        prefs.edit().putBoolean(KEY_ACTIVATED, activated).apply()
    }
    
    fun getStudent(): Student? {
        val json = prefs.getString(KEY_STUDENT_DATA, null) ?: return null
        return try {
            gson.fromJson(json, Student::class.java)
        } catch (e: Exception) {
            null
        }
    }
    
    fun saveStudent(student: Student) {
        prefs.edit().putString(KEY_STUDENT_DATA, gson.toJson(student)).apply()
    }
    
    fun getCompletedActions(): List<String> {
        val json = prefs.getString(KEY_COMPLETED_ACTIONS, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun addCompletedAction(actionKey: String) {
        val actions = getCompletedActions().toMutableList()
        if (!actions.contains(actionKey)) {
            actions.add(actionKey)
            prefs.edit().putString(KEY_COMPLETED_ACTIONS, gson.toJson(actions)).apply()
        }
    }
    
    fun isActionCompleted(lessonId: String, actionType: String): Boolean {
        return getCompletedActions().contains("${lessonId}-${actionType}")
    }
    
    fun getTheme(): String = prefs.getString(KEY_THEME, "light") ?: "light"
    
    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
    
    fun getTeacherIP(): String? = prefs.getString(KEY_TEACHER_IP, null)
    
    fun setTeacherIP(ip: String) {
        prefs.edit().putString(KEY_TEACHER_IP, ip).apply()
    }
    
    fun getAssignedTeacherId(): String? = prefs.getString(KEY_ASSIGNED_TEACHER_ID, null)
    
    fun setAssignedTeacherId(id: String) {
        prefs.edit().putString(KEY_ASSIGNED_TEACHER_ID, id).apply()
    }
    
    fun getCachedLessons(teacherId: String): String? {
        return prefs.getString("${KEY_STUDENT_CACHE_PREFIX}${teacherId}", null)
    }
    
    fun saveCachedLessons(teacherId: String, lessonsJson: String) {
        prefs.edit().putString("${KEY_STUDENT_CACHE_PREFIX}${teacherId}", lessonsJson).apply()
    }
    
    fun getStats(): StudentStats {
        val student = getStudent()
        val points = student?.points ?: 0
        val actions = getCompletedActions()
        return StudentStats(
            stars = points,
            level = (points / 100) + 1,
            completedCount = actions.size
        )
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    fun saveHomeworkSolution(lessonId: String, solution: String) {
        prefs.edit().putString("homework_$lessonId", solution).apply()
    }
    
    fun getHomeworkSolution(lessonId: String): String? {
        return prefs.getString("homework_$lessonId", null)
    }
    
    fun removeHomeworkSolution(lessonId: String) {
        prefs.edit().remove("homework_$lessonId").apply()
    }
    
    fun getAllHomeworkSolutions(): Map<String, String> {
        val all = prefs.all
        val solutions = mutableMapOf<String, String>()
        for ((key, value) in all) {
            if (key.startsWith("homework_") && value is String) {
                val lessonId = key.removePrefix("homework_")
                solutions[lessonId] = value
            }
        }
        return solutions
    }
}
