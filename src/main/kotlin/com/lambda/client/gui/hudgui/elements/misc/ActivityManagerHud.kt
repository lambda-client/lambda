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
    override fun SafeClientEvent.updateText() {
        if (ActivityManager.noSubActivities()) return

        displayText.addLine("Current Activities ${ActivityManager.getAllSubActivities().size}")

        ActivityManager.appendInfo(displayText, primaryColor, secondaryColor)

        displayText.addLine("")
        displayText.addLine("Subscribers:")
        ListenerManager.listenerMap.keys.filterIsInstance<Activity>().forEach {
            displayText.addLine("${it::class.simpleName}")
        }
        ListenerManager.asyncListenerMap.keys.filterIsInstance<Activity>().forEach {
            displayText.addLine("${it::class.simpleName}")
        }
    }
}