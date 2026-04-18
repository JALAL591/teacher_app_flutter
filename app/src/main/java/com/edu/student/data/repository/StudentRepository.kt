package com.edu.student.data.repository

import android.content.Context
import com.edu.student.data.preferences.StudentPreferences
import com.edu.student.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
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
    
    fun saveHomeworkSolution(lessonId: String, answers: List<AnswerSubmission>, lesson: Lesson) {
        val solution = JSONObject().apply {
            put("lessonId", lessonId)
            put("lessonTitle", lesson.title)
            put("subjectId", lesson.subjectId)
            put("subjectTitle", lesson.subjectTitle)
            put("classId", lesson.classId)
            put("timestamp", System.currentTimeMillis())
            put("answers", JSONArray().apply {
                answers.forEach { answer ->
                    put(JSONObject().apply {
                        put("questionId", answer.questionId)
                        put("questionText", answer.questionText)
                        put("questionType", answer.questionType)
                        put("selectedAnswer", answer.selectedAnswer ?: "")
                        put("correctAnswer", answer.correctAnswer ?: "")
                        put("isCorrect", answer.isCorrect)
                        put("textAnswer", answer.textAnswer ?: "")
                        put("imageAnswer", answer.imageAnswer ?: "")
                    })
                }
            })
            put("summary", JSONObject().apply {
                val correctCount = answers.count { it.isCorrect }
                val totalQuestions = answers.size
                put("totalQuestions", totalQuestions)
                put("correctAnswers", correctCount)
                put("wrongAnswers", totalQuestions - correctCount)
                put("score", correctCount)
                put("percentage", if (totalQuestions > 0) (correctCount * 100) / totalQuestions else 0)
            })
        }
        
        prefs.saveHomeworkSolution(lessonId, solution.toString())
    }
    
    fun getHomeworkSolution(lessonId: String): JSONObject? {
        val jsonStr = prefs.getHomeworkSolution(lessonId) ?: return null
        return try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getAllHomeworkSolutions(): List<JSONObject> {
        val solutions = mutableListOf<JSONObject>()
        val all = prefs.getAllHomeworkSolutions()
        for ((_, jsonStr) in all) {
            try {
                solutions.add(JSONObject(jsonStr))
            } catch (e: Exception) {
            }
        }
        return solutions.sortedByDescending { it.optLong("timestamp", 0) }
    }
    
    fun removeHomeworkSolution(lessonId: String) {
        prefs.removeHomeworkSolution(lessonId)
    }
    
    fun hasHomeworkSolution(lessonId: String): Boolean {
        return prefs.getHomeworkSolution(lessonId) != null
    }
    
    fun logout() {
        prefs.clearAll()
    }
}
