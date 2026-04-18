package com.edu.student.domain.model

data class Lesson(
    val id: String,
    val title: String,
    val description: String = "",
    val content: String = "",
    val unit: String = "الوحدة الأولى",
    val videoUrl: String? = null,
    val pdfUrl: String? = null,
    val pdfBase64: String? = null,
    val image: String? = null,
    val subjectId: String = "",
    val subjectTitle: String = "",
    val classId: String = "",
    val grade: String = "",
    val section: String = "",
    val questions: List<Question> = emptyList(),
    val isPublished: Boolean = true,
    val createdAt: String = ""
)

data class HomeworkSubmission(
    val submissionId: String,
    val studentId: String,
    val studentName: String,
    val studentGrade: String,
    val studentSection: String,
    val avatar: String?,
    val lessonId: String,
    val lessonTitle: String,
    val subjectId: String,
    val subjectTitle: String,
    val classId: String,
    val timestamp: Long,
    val answers: List<AnswerSubmission>,
    val summary: SubmissionSummary
)

data class SubmissionSummary(
    val totalQuestions: Int,
    val correctAnswers: Int,
    val wrongAnswers: Int,
    val essayQuestions: Int,
    val score: Int,
    val percentage: Int
)
