package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.activity.Activity
import com.lambda.client.event.ListenerManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.ActivityManager

internal object ActivityManagerHud: LabelHud(
    name = "ActivityManager",
    category = Category.MISC,
    description = "Display current activities."
) {
    val anonymize by setting("Anonymize", false)

    override fun SafeClientEvent.updateText() {
        if (ActivityManager.noSubActivities()) return

        displayText.addLine("Current Activities ${ActivityManager.getAllSubActivities().size}")

        ActivityManager.appendInfo(displayText, primaryColor, secondaryColor)

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