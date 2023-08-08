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
        if (dubs) {
            displayText.add("Dubs:", primaryColor)
            displayText.add("${ChestCountManager.dubsCount}", secondaryColor)
            displayText.add("Chests:", primaryColor)
            displayText.add("${ChestCountManager.chestCount - (ChestCountManager.dubsCount * 2)}", secondaryColor)
        } else {
            displayText.add("Chests:", primaryColor)
            displayText.add("${ChestCountManager.chestCount}", secondaryColor)
        }

        if (!shulkers) return
        displayText.add("Shulkers:", primaryColor)
        displayText.add("${ChestCountManager.shulkerCount}", secondaryColor)
    }
}

