package com.lambda.client.gui.hudgui.elements.client

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
    private val details by setting("Details", false)
    private var startTime = 0L

    override fun SafeClientEvent.updateText() {
        if (ActivityManager.hasNoSubActivities) {
            startTime = 0L
            return
        }

        if (startTime == 0L) startTime = System.currentTimeMillis()

        val runTimeMS = System.currentTimeMillis() - startTime
        val runTimeS = (runTimeMS + 1) / 1000.0

        with(ActivityManager) {
            displayText.add("Runtime", secondaryColor)
            displayText.addLine(DurationFormatUtils.formatDuration(runTimeMS, "HH:mm:ss,SSS"), primaryColor)
            displayText.add("Amount", secondaryColor)
            displayText.add(ActivityManager.allSubActivities.size.toString(), primaryColor)
            displayText.add("Current", secondaryColor)
            displayText.add(getCurrentActivity().activityName, primaryColor)
            displayText.add("Total Placed", secondaryColor)
            displayText.add(totalBlocksPlaced.toString(), primaryColor)
            displayText.add("Total Broken", secondaryColor)
            displayText.add(totalBlocksBroken.toString(), primaryColor)
            displayText.add("Place/s", secondaryColor)
            displayText.add("%.2f".format(totalBlocksPlaced / runTimeS), primaryColor)
            displayText.add("Break/s", secondaryColor)
            displayText.addLine("%.2f".format(totalBlocksBroken / runTimeS), primaryColor)

            appendInfo(displayText, primaryColor, secondaryColor, details)
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

    // STATS?

    var totalBlocksPlaced = 0
    var totalBlocksBroken = 0
}