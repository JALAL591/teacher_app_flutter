package com.edu.student.domain.model

data class Question(
    val id: String,
    val type: String,
    val text: String,
    val options: List<String> = emptyList(),
    val answer: String = ""
)

data class AnswerSubmission(
    val questionId: String,
    val questionText: String,
    val questionType: String,
    val selectedAnswer: String?,
    val correctAnswer: String?,
    val isCorrect: Boolean,
    val textAnswer: String?,
    val imageAnswer: String?
)
