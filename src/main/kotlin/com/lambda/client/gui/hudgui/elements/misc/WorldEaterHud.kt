package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.construction.core.BuildStructure
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.module.modules.misc.WorldEater

internal object WorldEaterHud : LabelHud(
    name = "WorldEater",
    category = Category.MISC,
    description = "Statistics about the world eater."
) {
    override fun SafeClientEvent.updateText() {
        if (WorldEater.ownedActivity == null) {
            displayText.addLine("Not running", primaryColor)
            return
        }

        with(WorldEater) {
            displayText.add("Progress", secondaryColor)
            displayText.addLine("${"%.3f".format(quarries.value.sumOf { it.containedBlocks.count { pos -> world.isAirBlock(pos) } }.toFloat() / quarries.value.sumOf { it.containedBlocks.size } * 100)}%", primaryColor)
            displayText.add("Layers left", secondaryColor)
            displayText.addLine("${ownedActivity?.subActivities?.filterIsInstance<BuildStructure>()?.count { it.status == Activity.Status.UNINITIALIZED }}", primaryColor)
        }
    }
}