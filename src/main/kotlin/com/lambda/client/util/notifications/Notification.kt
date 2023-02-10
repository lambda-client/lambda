package com.lambda.client.util.notifications

data class Notification(
    val text: String,
    val type: NotificationType,
    val duration: Int,
    val startTime: Long = System.currentTimeMillis(),
)