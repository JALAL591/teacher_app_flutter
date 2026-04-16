package com.edu.student.domain.model

data class Question(
    val id: String,
    val type: String,
    val text: String,
    val options: List<String> = emptyList(),
    val answer: String = ""
)
