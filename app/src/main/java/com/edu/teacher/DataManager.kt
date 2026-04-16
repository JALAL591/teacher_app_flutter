package com.edu.teacher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * مساعد مركزي لإدارة البيانات والمعرفات الفريدة
 * التسلسل الهرمي: المعلم → مواد → فصول/شعب → دروس
 */
object DataManager {

    // ==================== مفاتيح التخزين ====================
    private const val PREFS_NAME = "teacher_app"
    private const val KEY_TEACHER_INFO = "teacher_info"
    private const val KEY_SUBJECTS_PREFIX = "subjects_"
    private const val KEY_CLASSES_PREFIX = "classes_"
    private const val KEY_LESSONS_PREFIX = "lessons_"
    private const val KEY_SUBMISSIONS_PREFIX = "submissions_"
    private const val KEY_ATTENDANCE_PREFIX = "attendance_"
    private const val KEY_LAST_SYNC = "last_sync_time"

    // ==================== إنشاء معرفات فريدة ====================

    fun generateTeacherId(name: String): String {
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        val cleanName = name.trim().split(" ")[0]
        return "${cleanName}_$uuid"
    }

    fun generateSubjectId(): String = "subj_${UUID.randomUUID()}"

    fun generateClassId(): String = "cls_${UUID.randomUUID()}"

    fun generateLessonId(): String = "lesson_${UUID.randomUUID()}"

    fun generateSubmissionId(): String = "sub_${UUID.randomUUID()}"

    // ==================== إدارة بيانات المعلم ====================

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getTeacherInfo(context: Context): JSONObject? {
        val jsonStr = getPrefs(context).getString(KEY_TEACHER_INFO, null)
        return if (jsonStr != null) JSONObject(jsonStr) else null
    }

    fun saveTeacherInfo(context: Context, info: JSONObject) {
        getPrefs(context).edit().putString(KEY_TEACHER_INFO, info.toString()).apply()
    }

    fun getTeacherId(context: Context): String? {
        return getTeacherInfo(context)?.optString("id")
    }

    fun getTeacherName(context: Context): String {
        return getTeacherInfo(context)?.optString("name", context.getString(R.string.default_teacher))
            ?: context.getString(R.string.default_teacher)
    }

    // ==================== إدارة المواد (Subjects) ====================
    // المادة هي المستوى الأول بعد المعلم (مثل: رياضيات، علوم)

    fun getSubjectsKey(teacherId: String): String = "${KEY_SUBJECTS_PREFIX}$teacherId"

    fun getSubjects(context: Context, teacherId: String): MutableList<JSONObject> {
        val prefs = getPrefs(context)
        val key = getSubjectsKey(teacherId)
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    fun saveSubjects(context: Context, teacherId: String, subjects: List<JSONObject>) {
        val prefs = getPrefs(context)
        val key = getSubjectsKey(teacherId)
        val array = JSONArray()
        subjects.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun addSubject(context: Context, teacherId: String, subject: JSONObject): Boolean {
        val subjects = getSubjects(context, teacherId)
        // التحقق من عدم التكرار
        if (subjects.any { it.optString("name") == subject.optString("name") }) {
            return false
        }
        subjects.add(subject)
        saveSubjects(context, teacherId, subjects)
        return true
    }

    fun removeSubject(context: Context, teacherId: String, subjectId: String) {
        val subjects = getSubjects(context, teacherId).filter { it.optString("id") != subjectId }
        saveSubjects(context, teacherId, subjects)
        // حذف الفصول والدروس المرتبطة
        removeClassesForSubject(context, teacherId, subjectId)
    }

    // ==================== إدارة الفصول/الشعب (Classes) ====================
    // كل فصل يرتبط بمادة معينة ومعلم معين

    fun getClassesKey(teacherId: String, subjectId: String): String =
        "${KEY_CLASSES_PREFIX}${teacherId}_${subjectId}"

    fun getClasses(context: Context, teacherId: String, subjectId: String): MutableList<JSONObject> {
        val prefs = getPrefs(context)
        val key = getClassesKey(teacherId, subjectId)
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    fun saveClasses(context: Context, teacherId: String, subjectId: String, classes: List<JSONObject>) {
        val prefs = getPrefs(context)
        val key = getClassesKey(teacherId, subjectId)
        val array = JSONArray()
        classes.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun addClass(context: Context, teacherId: String, subjectId: String, classData: JSONObject) {
        val classes = getClasses(context, teacherId, subjectId)
        classes.add(classData)
        saveClasses(context, teacherId, subjectId, classes)
    }

    fun removeClass(context: Context, teacherId: String, subjectId: String, classId: String) {
        val classes = getClasses(context, teacherId, subjectId).filter { it.optString("id") != classId }
        saveClasses(context, teacherId, subjectId, classes)
    }

    fun removeClassesForSubject(context: Context, teacherId: String, subjectId: String) {
        val prefs = getPrefs(context)
        val key = getClassesKey(teacherId, subjectId)
        prefs.edit().remove(key).apply()
    }

    // الحصول على جميع الفصول لجميع المواد
    fun getAllClasses(context: Context, teacherId: String): MutableList<JSONObject> {
        val subjects = getSubjects(context, teacherId)
        val allClasses = mutableListOf<JSONObject>()
        subjects.forEach { subject ->
            val subjectId = subject.optString("id")
            allClasses.addAll(getClasses(context, teacherId, subjectId))
        }
        return allClasses
    }

    // ==================== إدارة الدروس (Lessons) ====================
    // كل درس يرتبط بفصل معين (ومنه بمادة ومعلم)

    fun getLessonsKey(teacherId: String, classId: String): String =
        "${KEY_LESSONS_PREFIX}${teacherId}_${classId}"

    fun getLessons(context: Context, teacherId: String, classId: String): MutableList<JSONObject> {
        val prefs = getPrefs(context)
        val key = getLessonsKey(teacherId, classId)
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    fun saveLessons(context: Context, teacherId: String, classId: String, lessons: List<JSONObject>) {
        val prefs = getPrefs(context)
        val key = getLessonsKey(teacherId, classId)
        val array = JSONArray()
        lessons.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun addLesson(context: Context, teacherId: String, classId: String, lesson: JSONObject) {
        val lessons = getLessons(context, teacherId, classId)
        lessons.add(0, lesson)
        saveLessons(context, teacherId, classId, lessons)
    }

    fun updateLesson(context: Context, teacherId: String, classId: String, lessonId: String, updatedLesson: JSONObject) {
        val lessons = getLessons(context, teacherId, classId)
        val index = lessons.indexOfFirst { it.optString("id") == lessonId }
        if (index >= 0) {
            lessons[index] = updatedLesson
            saveLessons(context, teacherId, classId, lessons)
        }
    }

    fun removeLesson(context: Context, teacherId: String, classId: String, lessonId: String) {
        val lessons = getLessons(context, teacherId, classId).filter { it.optString("id") != lessonId }
        saveLessons(context, teacherId, classId, lessons)
    }

    // ==================== إدارة التسليمات ====================

    fun getSubmissionsKey(teacherId: String): String = "${KEY_SUBMISSIONS_PREFIX}$teacherId"

    fun getSubmissions(context: Context, teacherId: String): MutableList<JSONObject> {
        val prefs = getPrefs(context)
        val key = getSubmissionsKey(teacherId)
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    fun saveSubmissions(context: Context, teacherId: String, submissions: List<JSONObject>) {
        val prefs = getPrefs(context)
        val key = getSubmissionsKey(teacherId)
        val array = JSONArray()
        submissions.forEach { array.put(it) }
        prefs.edit().putString(key, array.toString()).apply()
    }

    // ==================== إدارة الحضور ====================

    fun getAttendanceKey(classId: String): String = "${KEY_ATTENDANCE_PREFIX}$classId"

    fun saveAttendance(context: Context, classId: String, historyMap: JSONObject) {
        getPrefs(context).edit().putString(getAttendanceKey(classId), historyMap.toString()).apply()
    }

    fun getAttendance(context: Context, classId: String): JSONObject {
        val jsonStr = getPrefs(context).getString(getAttendanceKey(classId), "{}") ?: "{}"
        return JSONObject(jsonStr)
    }

    // ==================== المزامنة ====================

    fun getLastSyncTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_SYNC, 0L)
    }

    fun updateLastSyncTime(context: Context) {
        getPrefs(context).edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    fun getSyncTimestamp(): String = System.currentTimeMillis().toString()

    // ==================== دوال مساعدة ====================

    fun getSubjectById(context: Context, teacherId: String, subjectId: String): JSONObject? {
        return getSubjects(context, teacherId).find { it.optString("id") == subjectId }
    }

    fun getClassById(context: Context, teacherId: String, classId: String): JSONObject? {
        val subjects = getSubjects(context, teacherId)
        for (subject in subjects) {
            val classes = getClasses(context, teacherId, subject.optString("id"))
            val found = classes.find { it.optString("id") == classId }
            if (found != null) return found
        }
        return null
    }

    fun getSubjectForClass(context: Context, teacherId: String, classId: String): JSONObject? {
        val subjects = getSubjects(context, teacherId)
        for (subject in subjects) {
            val classes = getClasses(context, teacherId, subject.optString("id"))
            if (classes.any { it.optString("id") == classId }) return subject
        }
        return null
    }
}
