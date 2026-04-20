package com.edu.student.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LessonStorage {

    private const val PREFS_NAME = "lessons_storage"
    private const val KEY_LESSONS = "saved_lessons"

    fun saveLesson(context: Context, lesson: JSONObject) {
        val lessonId = lesson.optString("id", "")
        if (lessonId.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_LESSONS, "[]") ?: "[]"
        val lessonsArray = JSONArray(existingJson)

        val updatedArray = JSONArray()
        for (i in 0 until lessonsArray.length()) {
            val existing = lessonsArray.getJSONObject(i)
            if (existing.optString("id") != lessonId) {
                updatedArray.put(existing)
            }
        }

        updatedArray.put(lesson)

        prefs.edit()
            .putString(KEY_LESSONS, updatedArray.toString())
            .apply()

        Log.d("LessonStorage", "Lesson saved: $lessonId")
    }

    fun loadAllLessons(context: Context): List<JSONObject> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LESSONS, "[]") ?: "[]"
        val array = JSONArray(json)

        val lessons = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            val lesson = array.getJSONObject(i)
            validateAndFixMediaPaths(context, lesson)
            lessons.add(lesson)
        }

        return lessons
    }

    private fun validateAndFixMediaPaths(context: Context, lesson: JSONObject) {
        listOf(
            "localImagePath" to "imagePath",
            "localVideoPath" to "videoPath",
            "localPdfPath" to "pdfPath"
        ).forEach { (localKey, _) ->
            val path = lesson.optString(localKey, "")
            if (path.isNotEmpty() && !File(path).exists()) {
                Log.w("LessonStorage", "Media missing: $path")
                lesson.put(localKey, "")
            }
        }
    }

    fun deleteLesson(context: Context, lessonId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingJson = prefs.getString(KEY_LESSONS, "[]") ?: "[]"
        val lessonsArray = JSONArray(existingJson)

        val updatedArray = JSONArray()
        for (i in 0 until lessonsArray.length()) {
            val existing = lessonsArray.getJSONObject(i)
            if (existing.optString("id") != lessonId) {
                updatedArray.put(existing)
            }
        }

        prefs.edit()
            .putString(KEY_LESSONS, updatedArray.toString())
            .apply()

        Log.d("LessonStorage", "Lesson deleted: $lessonId")
    }
}