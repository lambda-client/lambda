package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.ChestCountManager

internal object ChestCounter : LabelHud(
    name = "ChestCounter",
    category = Category.WORLD,
    description = "Displays the number of chests and shulkers currently loaded"
) {
    private val dubs by setting("Count Dubs", true, description = "Counts double chests instead of individual chests")
    private val shulkers by setting("Count Shulkers", true, description = "Counts shulkers in the world")

    override fun SafeClientEvent.updateText() {
        displayText.add(if (dubs) "Dubs:" else "Chests:", primaryColor)
        displayText.add(if (dubs) "${ChestCountManager.dubsCount}" else "${ChestCountManager.chestCount}", secondaryColor)
        if (!shulkers) return
        displayText.add("Shulkers:", primaryColor)
        displayText.add("${ChestCountManager.shulkerCount}", secondaryColor)
    }
}

