package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.commons.utils.grammar
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.OnlineTimeManager
import java.time.Duration

internal object OnlineTime: LabelHud(
    name = "OnlineTime",
    category = Category.MISC,
    description = "Displays how long you have been online"
) {
    override fun SafeClientEvent.updateText() {
        val onlineTime = OnlineTimeManager.getOnlineTime()
        displayText.add("Online:", secondaryColor)
        displayText.add(formatDuration(onlineTime), primaryColor)
    }

    // avoiding kotlin.time.Duration.toString() because it shows down to milliseconds
    private fun formatDuration(duration: Duration): String {
        val secondsInMinute = 60L
        val secondsInHour = secondsInMinute * 60L
        val seconds = duration.seconds % secondsInMinute
        val minutes = duration.seconds / secondsInMinute % secondsInMinute
        val hours = duration.seconds / secondsInHour % 24L
        return buildString {
            if (hours > 0) append(grammar(hours.toInt(), "hour", "hours") + ", ")
            if (minutes > 0) append(grammar(minutes.toInt(), "minute", "minutes") + ", ")
            append(grammar(seconds.toInt(), "second", "seconds"))
        }
    }

}