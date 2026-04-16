package com.edu.student.domain.model

data class Subject(
    val id: String,
    val title: String,
    val color: String = "#6366f1",
    val icon: String = "BookOpen",
    val lessons: List<Lesson> = emptyList(),
    var points: Int = 0
)
