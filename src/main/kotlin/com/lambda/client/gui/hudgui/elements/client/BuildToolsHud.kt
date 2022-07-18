package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.buildtools.Statistics
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud

internal object BuildToolsHud : LabelHud(
    name = "BuildToolsHud",
    category = Category.CLIENT,
    description = "Hud for the build engine"
) {
    val simpleMovingAverageRange by setting("Moving Average", 60, 5..600, 5, description = "Sets the timeframe of the average", unit = " seconds")
    val showSession by setting("Show Session", true, description = "Toggles the Session section in HUD")
    val showPerformance by setting("Show Performance", true, description = "Toggles the Performance section in HUD")
    val showEnvironment by setting("Show Environment", true, description = "Toggles the Environment section in HUD")
    val showTask by setting("Show Task", true, description = "Toggles the Task section in HUD")
    val showEstimations by setting("Show Estimations", true, description = "Toggles the Estimations section in HUD")
    val showQueue by setting("Show Queue", false, description = "Shows task queue in HUD")
    private val resetStats = setting("Reset Stats", false, description = "Resets the stats")

    init {
        resetStats.consumers.add { _, it ->
            if (it) Statistics.setupStatistics()
            false
        }
    }

    override fun SafeClientEvent.updateText() = Statistics.gatherStatistics(displayText)
}