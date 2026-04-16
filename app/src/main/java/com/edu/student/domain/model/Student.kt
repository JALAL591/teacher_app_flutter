package com.edu.student.domain.model

data class Student(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val points: Int = 0,
    val grade: String = "",
    val section: String = "",
    val joinedClasses: List<JoinedClass> = emptyList(),
    val activated: Boolean = false,
    val joinedDate: String = ""
)

data class JoinedClass(
    val teacherId: String,
    val teacherName: String = "المعلم الحالي",
    val subject: String = "مادة جديدة",
    val lastSync: String = "الآن",
    val status: String = "active"
)
