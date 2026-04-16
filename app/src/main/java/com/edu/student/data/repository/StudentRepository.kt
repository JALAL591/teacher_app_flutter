package com.edu.student.data.repository

import android.content.Context
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class StudentRepository(context: Context) {
    
    private val prefs = StudentPreferences(context)
    private val gson = Gson()
    
    fun isLoggedIn(): Boolean = prefs.isActivated()
    
    fun getStudent(): Student? = prefs.getStudent()
    
    fun createStudent(name: String, avatar: String? = null): Student {
        val student = Student(
            id = "std_${UUID.randomUUID().toString().take(9)}",
            name = name.trim(),
            avatar = avatar,
            points = 0,
            activated = true,
            joinedDate = System.currentTimeMillis().toString()
        )
        prefs.saveStudent(student)
        prefs.setActivated(true)
        return student
    }
    
    fun updateStudent(student: Student) {
        prefs.saveStudent(student)
    }
    
    fun addPoints(lessonId: String, actionType: String, points: Int) {
        val student = getStudent() ?: return
        val actionKey = "${lessonId}-${actionType}"
        
        if (prefs.isActionCompleted(lessonId, actionType)) return
        
        val updatedStudent = student.copy(points = student.points + points)
        prefs.saveStudent(updatedStudent)
        prefs.addCompletedAction(actionKey)
    }
    
    fun isActionCompleted(lessonId: String, actionType: String): Boolean {
        return prefs.isActionCompleted(lessonId, actionType)
    }
    
    fun getStats(): StudentStats = prefs.getStats()
    
    fun getSubjects(): List<Subject> {
        val teacherId = prefs.getAssignedTeacherId() ?: return emptyList()
        val cached = prefs.getCachedLessons(teacherId) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<Lesson>>() {}.type
            val lessons: List<Lesson> = gson.fromJson(cached, type)
            groupLessonsBySubject(lessons)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun saveLessons(lessons: List<Lesson>, teacherId: String? = null) {
        val tid = teacherId ?: prefs.getAssignedTeacherId() ?: return
        prefs.saveCachedLessons(tid, gson.toJson(lessons))
    }
    
    private fun groupLessonsBySubject(lessons: List<Lesson>): List<Subject> {
        if (lessons.isEmpty()) return emptyList()
        
        val subjectMap = mutableMapOf<String, Subject>()
        
        lessons.forEach { lesson ->
            val subjectId = lesson.subjectId.ifEmpty { 
                java.net.URLEncoder.encode(lesson.subjectTitle, "UTF-8") 
            }
            val subjectTitle = lesson.subjectTitle.ifEmpty { "مواد عامة" }
            
            if (!subjectMap.containsKey(subjectId)) {
                subjectMap[subjectId] = Subject(
                    id = subjectId,
                    title = subjectTitle,
                    lessons = mutableListOf()
                )
            }
            (subjectMap[subjectId]?.lessons as? MutableList)?.add(lesson)
        }
        
        return subjectMap.values.toList()
    }
    
    fun getTheme(): String = prefs.getTheme()
    
    fun setTheme(theme: String) = prefs.setTheme(theme)
    
    fun logout() {
        prefs.clearAll()
    }
}
