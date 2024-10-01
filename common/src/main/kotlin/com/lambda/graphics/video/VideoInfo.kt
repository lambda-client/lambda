package com.lambda.graphics.video

data class VideoInfo(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val frameDuration: () -> Double,
)
