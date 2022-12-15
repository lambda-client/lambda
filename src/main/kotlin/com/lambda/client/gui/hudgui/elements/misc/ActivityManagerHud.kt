package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.ActivityManager

internal object ActivityManagerHud: LabelHud(
    name = "ActivityManager",
    category = Category.MISC,
    description = "Display current activities."
) {
    override fun SafeClientEvent.updateText() {
        if (ActivityManager.subActivities.isEmpty()) return

        ActivityManager.appendInfo(displayText, primaryColor, secondaryColor, 0)
    }

}