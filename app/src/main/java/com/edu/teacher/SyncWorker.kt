package com.edu.teacher

import android.content.Context
import androidx.work.Data
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Worker للمزامنة التلقائية في الخلفية كل 15 دقيقة
 * يعمل حتى عندما يكون التطبيق مغلقاً
 * 
 * يقبل المعرفات عبر inputData:
 * - teacherId: معرف المعلم
 * - subjectId: معرف المادة (اختياري)
 * - classId: معرف الفصل (اختياري)
 * 
 * إذا لم تُمرر المعرفات، يقوم بمزامنة شاملة لجميع المواد والفصول
 */
class SyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        return try {
            val teacherId = inputData.getString(KEY_TEACHER_ID)
            val subjectId = inputData.getString(KEY_SUBJECT_ID)
            val classId = inputData.getString(KEY_CLASS_ID)

            performSync(teacherId, subjectId, classId)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun performSync(teacherId: String?, subjectId: String?, classId: String?) {
        val context = applicationContext

        // إذا لم يتم تمرير teacherId، نحاول جلبه من DataManager
        val actualTeacherId = teacherId ?: DataManager.getTeacherId(context)
        if (actualTeacherId == null) {
            android.util.Log.w("SyncWorker", "${EmojiConstants.WARNING} لا يوجد معلم مسجل - تخطي المزامنة")
            return
        }

        android.util.Log.d("SyncWorker", "${EmojiConstants.SYNC} بدء المزامنة - teacherId: $actualTeacherId")

        var totalSubjects = 0
        var totalClasses = 0
        var totalLessons = 0

        // تحديد نطاق المزامنة
        if (subjectId != null && classId != null) {
            // مزامنة محددة (فصل واحد)
            syncSingleClass(context, actualTeacherId, subjectId, classId)
            totalClasses = 1
        } else if (subjectId != null) {
            // مزامنة مادة كاملة (جميع فصولها)
            totalClasses = syncSubject(context, actualTeacherId, subjectId)
        } else {
            // مزامنة شاملة (جميع المواد والفصول)
            val subjects = DataManager.getSubjects(context, actualTeacherId)
            totalSubjects = subjects.size

            subjects.forEach { subject ->
                val sId = subject.optString("id")
                totalClasses += syncSubject(context, actualTeacherId, sId)
            }
        }

        // حساب إجمالي الدروس
        if (subjectId != null && classId != null) {
            totalLessons = DataManager.getLessons(context, actualTeacherId, classId).size
        } else {
            val subjects = if (subjectId != null) {
                listOfNotNull(DataManager.getSubjectById(context, actualTeacherId, subjectId))
            } else {
                DataManager.getSubjects(context, actualTeacherId)
            }

            subjects.forEach { subject ->
                val sId = subject.optString("id")
                val classes = DataManager.getClasses(context, actualTeacherId, sId)
                classes.forEach { cls ->
                    totalLessons += DataManager.getLessons(context, actualTeacherId, cls.optString("id")).size
                }
            }
        }

        // بناء تقرير المزامنة
        val syncPayload = JSONObject().apply {
            put("teacherId", actualTeacherId as Any)
            put("syncType", when {
                classId != null -> "single_class"
                subjectId != null -> "single_subject"
                else -> "full_sync"
            } as Any)
            put("subjectsCount", totalSubjects as Any)
            put("classesCount", totalClasses as Any)
            put("lessonsCount", totalLessons as Any)
            put("syncedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()) as Any)
        }

        android.util.Log.d("SyncWorker", "${EmojiConstants.SUCCESS} تم المزامنة: ${syncPayload.toString()}")

        // تحديث طابع المزامنة
        DataManager.updateLastSyncTime(context)

        // حفظ نسخة احتياطية
        val prefs = DataManager.getPrefs(context)
        prefs.edit()
            .putString("backup_last_sync", syncPayload.toString())
            .putLong("backup_timestamp", System.currentTimeMillis())
            .apply()
    }

    /**
     * مزامنة فصل واحد
     */
    private fun syncSingleClass(context: Context, teacherId: String, @Suppress("UNUSED_PARAMETER") subjectId: String, classId: String) {
        val lessons = DataManager.getLessons(context, teacherId, classId)
        android.util.Log.d("SyncWorker", "${EmojiConstants.BOOKS} فصل $classId: ${lessons.size} دروس")

        // هنا يمكن إضافة رفع البيانات للسيرفر
        // مثال: apiClient.uploadLessons(teacherId, classId, lessons)
    }

    /**
     * مزامنة مادة كاملة (جميع فصولها)
     * @return عدد الفصول
     */
    private fun syncSubject(context: Context, teacherId: String, subjectId: String): Int {
        val classes = DataManager.getClasses(context, teacherId, subjectId)
        var totalLessons = 0

        classes.forEach { cls ->
            val classId = cls.optString("id")
            val lessons = DataManager.getLessons(context, teacherId, classId)
            totalLessons += lessons.size
            android.util.Log.d("SyncWorker", "${EmojiConstants.BOOKS} فصل ${cls.optString("name")}: ${lessons.size} دروس")
        }

        android.util.Log.d("SyncWorker", "${EmojiConstants.BOOKS} مادة $subjectId: ${classes.size} فصول، $totalLessons دروس")
        return classes.size
    }

    companion object {
        const val WORK_NAME = "teacher_app_sync_worker"
        const val SYNC_INTERVAL_MINUTES = 15L

        // مفاتيح inputData
        const val KEY_TEACHER_ID = "teacher_id"
        const val KEY_SUBJECT_ID = "subject_id"
        const val KEY_CLASS_ID = "class_id"

        /**
         * إنشاء Data للمزامنة الشاملة
         */
        fun createFullSyncData(teacherId: String): Data {
            return Data.Builder()
                .putString(KEY_TEACHER_ID, teacherId)
                .build()
        }

        /**
         * إنشاء Data لمزامنة مادة محددة
         */
        fun createSubjectSyncData(teacherId: String, subjectId: String): Data {
            return Data.Builder()
                .putString(KEY_TEACHER_ID, teacherId)
                .putString(KEY_SUBJECT_ID, subjectId)
                .build()
        }

        /**
         * إنشاء Data لمزامنة فصل محدد
         */
        fun createClassSyncData(teacherId: String, subjectId: String, classId: String): Data {
            return Data.Builder()
                .putString(KEY_TEACHER_ID, teacherId)
                .putString(KEY_SUBJECT_ID, subjectId)
                .putString(KEY_CLASS_ID, classId)
                .build()
        }
    }
}
