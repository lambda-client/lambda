package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object Velocity : LabelHud(
    name = "Velocity",
    description = "Shows your velocity",
    category = Category.PLAYER
) {
    private val newlines by setting("Vertical", true)
    override fun SafeClientEvent.updateText() {
        displayText.addLine("Velocity: ", primaryColor)
        if (newlines) {
            displayText.addLine("X " + String.format("%.3f", player.motionX), secondaryColor)
            displayText.addLine("Y " + String.format("%.3f", player.motionY), secondaryColor)
            displayText.add("Z " + String.format("%.3f", player.motionZ), secondaryColor)
        } else displayText.addLine("X " + String.format("%.3f", player.motionX) + ", Y " + String.format("%.3f", player.motionY) + ", Z " + String.format("%.3f", player.motionZ), secondaryColor)
    }
}