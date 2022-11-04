package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.PacketManager

internal object Packets : LabelHud(
    name = "Packets",
    category = Category.MISC,
    description = "Information about your packet traffic."
) {
    private val perSecond by setting("Per second", true)
    private val total by setting("Total", true)
    private val lastSent by setting("Last sent packet info", true)
    private val lastReceived by setting("Last received packet info", true)

    override fun SafeClientEvent.updateText() {
        if (perSecond) {
            displayText.add("Sent/s", secondaryColor)
            displayText.add(PacketManager.recentSent.count().toString(), primaryColor)
            displayText.add("Received/s", secondaryColor)
            displayText.addLine(PacketManager.recentReceived.count().toString(), primaryColor)
        }

        if (total) {
            displayText.add("Total sent", secondaryColor)
            displayText.add(PacketManager.totalSent.toString(), primaryColor)
            displayText.add("Total received", secondaryColor)
            displayText.addLine(PacketManager.totalReceived.toString(), primaryColor)
        }

        if (lastSent) {
            PacketManager.recentSent.lastOrNull()?.let {
                displayText.add("Last sent", secondaryColor)
                displayText.add(it.first.javaClass.simpleName ?: "None", primaryColor)
                displayText.addLine("${System.currentTimeMillis() - it.second}ms", primaryColor)
            }
        }

        if (lastReceived) {
            PacketManager.recentReceived.lastOrNull()?.let {
                displayText.add("Last received", secondaryColor)
                displayText.add(it.first.javaClass.simpleName ?: "None", primaryColor)
                displayText.addLine("${System.currentTimeMillis() - it.second}ms", primaryColor)
            }
        }
    }
}