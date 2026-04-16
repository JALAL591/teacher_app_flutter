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
    val questions: List<Question> = emptyList(),
    val isPublished: Boolean = true,
    val createdAt: String = ""
)
