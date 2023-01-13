package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.activity.Activity
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.ActivityManager
import org.apache.commons.lang3.time.DurationFormatUtils

internal object ActivityManagerHud: LabelHud(
    name = "ActivityManager",
    category = Category.MISC,
    description = "Display current activities."
) {
    val anonymize by setting("Anonymize", false)
    private var startTime = 0L

    override fun SafeClientEvent.updateText() {
        if (ActivityManager.hasNoSubActivities) {
            startTime = 0L
            return
        }

        if (startTime == 0L) startTime = System.currentTimeMillis()

        with(ActivityManager) {
            displayText.add("Runtime:", primaryColor)
            displayText.addLine(DurationFormatUtils.formatDuration(System.currentTimeMillis() - startTime, "HH:mm:ss,SSS"), secondaryColor)
            displayText.add("Amount:", primaryColor)
            displayText.add(ActivityManager.allSubActivities.size.toString(), secondaryColor)
            displayText.add("Current:", primaryColor)
            displayText.addLine(getCurrentActivity().activityName, secondaryColor)

            appendInfo(displayText, primaryColor, secondaryColor)
        }

        val sync = ListenerManager.listenerMap.keys.filterIsInstance<Activity>()
        val async = ListenerManager.asyncListenerMap.keys.filterIsInstance<Activity>()

        if (sync.isNotEmpty() || async.isNotEmpty()) {
            displayText.addLine("")
            displayText.addLine("Subscribers:")
            if (sync.isNotEmpty()) displayText.addLine("SYNC ${sync.map { it::class.simpleName }}")
            if (async.isNotEmpty()) displayText.addLine("ASYNC ${async.map { it::class.simpleName }}")
        }
    }
}