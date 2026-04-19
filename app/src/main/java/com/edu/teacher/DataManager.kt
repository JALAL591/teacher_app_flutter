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
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_REMEMBER_ME = "remember_me"
    private const val KEY_SAVED_USER_ID = "saved_user_id"
    private const val KEY_SAVED_PASSWORD = "saved_password"
    private const val KEY_CURRENT_TEACHER_ID = "current_teacher_id"
    private const val KEY_CURRENT_TEACHER_NAME = "current_teacher_name"
    private const val KEY_SCHOOL_NAME = "school_name"
    private const val KEY_LAST_LOGIN_TIME = "last_login_time"

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
    
    // ==================== Login State ====================
    fun loginTeacher(context: Context, teacherId: String, teacherName: String, schoolName: String = "") {
        getPrefs(context).edit().putBoolean(KEY_IS_LOGGED_IN, true).apply()
        getPrefs(context).edit().putString(KEY_CURRENT_TEACHER_ID, teacherId).apply()
        getPrefs(context).edit().putString(KEY_CURRENT_TEACHER_NAME, teacherName).apply()
        getPrefs(context).edit().putString(KEY_SCHOOL_NAME, schoolName).apply()
        getPrefs(context).edit().putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis()).apply()
    }
    
    fun isLoggedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    fun logoutTeacher(context: Context) {
        getPrefs(context).edit().remove(KEY_IS_LOGGED_IN).apply()
        getPrefs(context).edit().remove(KEY_CURRENT_TEACHER_ID).apply()
        getPrefs(context).edit().remove(KEY_CURRENT_TEACHER_NAME).apply()
        getPrefs(context).edit().remove(KEY_LAST_LOGIN_TIME).apply()
    }
    
    fun getCurrentTeacherInfo(context: Context): TeacherInfo? {
        if (!isLoggedIn(context)) return null
        return TeacherInfo(
            id = getPrefs(context).getString(KEY_CURRENT_TEACHER_ID, "") ?: "",
            name = getPrefs(context).getString(KEY_CURRENT_TEACHER_NAME, "") ?: "",
            schoolName = getPrefs(context).getString(KEY_SCHOOL_NAME, "") ?: ""
        )
    }
    
    data class TeacherInfo(
        val id: String,
        val name: String,
        val schoolName: String
    )
    
    // ==================== Remember Me ====================
    fun saveLoginCredentials(context: Context, userId: String, password: String, rememberMe: Boolean) {
        getPrefs(context).edit().putString(KEY_SAVED_USER_ID, userId).apply()
        
        if (rememberMe && password.isNotEmpty()) {
            val encodedPassword = android.util.Base64.encodeToString(
                password.toByteArray(), 
                android.util.Base64.NO_WRAP
            )
            getPrefs(context).edit().putString(KEY_SAVED_PASSWORD, encodedPassword).apply()
            getPrefs(context).edit().putBoolean(KEY_REMEMBER_ME, true).apply()
        } else {
            getPrefs(context).edit().remove(KEY_SAVED_PASSWORD).apply()
            getPrefs(context).edit().putBoolean(KEY_REMEMBER_ME, false).apply()
        }
    }
    
    fun getSavedCredentials(context: Context): Pair<String?, String?> {
        val userId = getPrefs(context).getString(KEY_SAVED_USER_ID, null)
        val encodedPassword = getPrefs(context).getString(KEY_SAVED_PASSWORD, null)
        val password = encodedPassword?.let {
            try {
                String(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
            } catch (e: Exception) { null }
        }
        return Pair(userId, password)
    }
    
    fun isRememberMeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMEMBER_ME, false)
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

    // ==================== دوال البيانات الكاملة والتسليمات ====================

    fun getFullTeacherData(context: Context, teacherId: String): JSONObject {
        val result = JSONObject()
        val subjectsArray = JSONArray()

        val subjects = getSubjects(context, teacherId)

        for (i in 0 until subjects.size) {
            val subject = subjects[i]
            val subjectId = subject.optString("id")
            val subjectName = subject.optString("name", "")

            val subjectWithClasses = JSONObject()
            subjectWithClasses.put("id", subjectId)
            subjectWithClasses.put("name", subjectName)

            val classesArray = JSONArray()
            val classes = getClasses(context, teacherId, subjectId)

            for (j in 0 until classes.size) {
                val cls = classes[j]
                val classId = cls.optString("id")
                val className = cls.optString("name", "")
                val grade = cls.optString("grade", "")
                val section = cls.optString("section", "")

                val classWithLessons = JSONObject()
                classWithLessons.put("id", classId)
                classWithLessons.put("name", className)
                classWithLessons.put("grade", grade)
                classWithLessons.put("section", section)

                val lessonsArray = JSONArray()
                val lessons = getLessons(context, teacherId, classId)

                for (k in 0 until lessons.size) {
                    val lesson = lessons[k]
                    val lessonId = lesson.optString("id")
                    val lessonTitle = lesson.optString("title", "")

                    val lessonWithSubmissions = JSONObject()
                    lessonWithSubmissions.put("id", lessonId)
                    lessonWithSubmissions.put("title", lessonTitle)
                    lessonWithSubmissions.put("unit", lesson.optString("unit", ""))
                    lessonWithSubmissions.put("createdAt", lesson.optString("createdAt", ""))

                    val submissions = getSubmissionsForLesson(context, teacherId, classId, lessonId)
                    val submissionsArray = JSONArray()
                    for (submission in submissions) {
                        submissionsArray.put(submission)
                    }
                    lessonWithSubmissions.put("submissions", submissionsArray)

                    val stats = calculateLessonStats(submissions)
                    lessonWithSubmissions.put("stats", stats)

                    lessonsArray.put(lessonWithSubmissions)
                }

                classWithLessons.put("lessons", lessonsArray)
                classesArray.put(classWithLessons)
            }

            subjectWithClasses.put("classes", classesArray)
            subjectsArray.put(subjectWithClasses)
        }

        result.put("subjects", subjectsArray)
        return result
    }

    fun addSubmission(context: Context, teacherId: String, submission: JSONObject) {
        val submissions = getSubmissions(context, teacherId)
        submissions.add(submission)
        saveSubmissions(context, teacherId, submissions)
    }

    fun addSubmissionToLesson(
        context: Context,
        teacherId: String,
        classId: String,
        lessonId: String,
        submission: JSONObject
    ) {
        val key = "submissions_lesson_${teacherId}_${classId}_${lessonId}"
        val prefs = getPrefs(context)
        val existingJson = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(existingJson)
        array.put(submission)
        prefs.edit().putString(key, array.toString()).apply()
    }

    fun getSubmissionsForLesson(context: Context, teacherId: String, classId: String, lessonId: String): MutableList<JSONObject> {
        val key = "submissions_lesson_${teacherId}_${classId}_${lessonId}"
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(jsonStr)
        val list = mutableListOf<JSONObject>()
        for (i in 0 until array.length()) {
            list.add(array.getJSONObject(i))
        }
        return list
    }

    fun calculateLessonStats(submissions: List<JSONObject>): JSONObject {
        val totalStudents = submissions.size
        var totalScore = 0
        var totalQuestions = 0
        var completedCount = 0

        for (sub in submissions) {
            val summary = sub.optJSONObject("summary")
            if (summary != null) {
                totalScore += summary.optInt("score", 0)
                totalQuestions += summary.optInt("totalQuestions", 0)
                completedCount++
            }
        }

        val averageScore = if (totalQuestions > 0) (totalScore * 100) / totalQuestions else 0
        val completionRate = if (totalStudents > 0) (completedCount * 100) / totalStudents else 0

        return JSONObject().apply {
            put("totalSubmissions", totalStudents)
            put("completedCount", completedCount)
            put("completionRate", completionRate)
            put("averageScore", averageScore)
        }
    }

    fun getPendingSubmissions(context: Context, teacherId: String): MutableList<JSONObject> {
        val allSubmissions = getSubmissions(context, teacherId)
        val pendingList = mutableListOf<JSONObject>()
        for (sub in allSubmissions) {
            if (sub.optString("status") == "pending") {
                pendingList.add(sub)
            }
        }
        return pendingList
    }

    fun getGradedSubmissions(context: Context, teacherId: String): MutableList<JSONObject> {
        val allSubmissions = getSubmissions(context, teacherId)
        val gradedList = mutableListOf<JSONObject>()
        for (sub in allSubmissions) {
            if (sub.optString("status") == "graded") {
                gradedList.add(sub)
            }
        }
        return gradedList
    }

    fun gradeSubmission(context: Context, teacherId: String, submissionId: String, score: Int, feedback: String) {
        val submissions = getSubmissions(context, teacherId)
        var foundIndex = -1
        var foundSubmission: JSONObject? = null

        for (i in 0 until submissions.size) {
            if (submissions[i].optString("submissionId") == submissionId) {
                foundIndex = i
                foundSubmission = submissions[i]
                break
            }
        }

        if (foundIndex >= 0 && foundSubmission != null) {
            foundSubmission.put("status", "graded")
            foundSubmission.put("finalScore", score)
            foundSubmission.put("teacherFeedback", feedback)
            foundSubmission.put("gradedAt", System.currentTimeMillis())
            submissions[foundIndex] = foundSubmission
            saveSubmissions(context, teacherId, submissions)

            val classId = foundSubmission.optString("classId", "")
            val lessonId = foundSubmission.optString("lessonId", "")
            if (classId.isNotEmpty() && lessonId.isNotEmpty()) {
                updateSubmissionInLesson(context, teacherId, classId, lessonId, foundSubmission)
            }
        }
    }

    private fun updateSubmissionInLesson(context: Context, teacherId: String, classId: String, lessonId: String, updatedSubmission: JSONObject) {
        val key = "submissions_lesson_${teacherId}_${classId}_${lessonId}"
        val prefs = getPrefs(context)
        val existingJson = prefs.getString(key, "[]") ?: "[]"
        val array = JSONArray(existingJson)

        val submissionId = updatedSubmission.optString("submissionId")
        for (i in 0 until array.length()) {
            if (array.getJSONObject(i).optString("submissionId") == submissionId) {
                array.put(i, updatedSubmission)
                break
            }
        }

        prefs.edit().putString(key, array.toString()).apply()
    }

    fun getSubmissionsStats(context: Context, teacherId: String): JSONObject {
        val submissions = getSubmissions(context, teacherId)
        val total = submissions.size
        var pending = 0
        var graded = 0

        var totalQuestions = 0
        var totalScore = 0

        for (sub in submissions) {
            val status = sub.optString("status", "pending")
            if (status == "pending") {
                pending++
            } else if (status == "graded") {
                graded++
            }

            val summary = sub.optJSONObject("summary")
            if (summary != null) {
                totalQuestions += summary.optInt("totalQuestions", 0)
                totalScore += summary.optInt("score", 0)
            }
        }

        val avgPercentage = if (totalQuestions > 0) (totalScore * 100) / totalQuestions else 0

        return JSONObject().apply {
            put("totalSubmissions", total)
            put("pendingSubmissions", pending)
            put("gradedSubmissions", graded)
            put("averageScore", avgPercentage)
        }
    }
}
