package com.lambda.client.util.notifications

data class Notification(
    val text: String,
    val type: NotificationType,
    var duration: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
)