package com.example.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val year: Int = 0
)
