package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object Biome : LabelHud(
    name = "Biome",
    category = Category.WORLD,
    description = "Display the current biome you are in"
) {

    override fun SafeClientEvent.updateText() {
        val biome = world.getBiome(player.position).biomeName ?: "Unknown"

        displayText.add(biome, primaryColor)
        displayText.add("Biome", secondaryColor)
    }

}