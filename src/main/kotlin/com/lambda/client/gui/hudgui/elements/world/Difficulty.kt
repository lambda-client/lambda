package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import java.util.*

internal object Difficulty : LabelHud(
    name = "Difficulty",
    description = "Shows world difficulty",
    category = Category.WORLD
) {
    private val showDifficulty by setting("Show \"Difficulty\"", true)
    private val vertical by setting("Vertical", true, { showDifficulty })
    override fun SafeClientEvent.updateText() {
        if (vertical) {
            if (showDifficulty) displayText.addLine("Difficulty", primaryColor)
        } else if (showDifficulty) displayText.add("Difficulty", primaryColor)
        displayText.add(world.worldInfo.difficulty.name.lowercase(Locale.getDefault()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }, secondaryColor)
    }
}