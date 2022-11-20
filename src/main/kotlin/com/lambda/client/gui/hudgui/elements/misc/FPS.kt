package com.lambda.client.gui.hudgui.elements.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.CircularArray
import com.lambda.client.util.graphics.AnimationUtils
import kotlin.math.roundToInt

internal object FPS : LabelHud(
    name = "FPS",
    category = Category.MISC,
    description = "Frames per second in game"
) {

    private val showAverage by setting("Show Average", true)
    private val showMin by setting("Show Min", false)
    private val showMax by setting("Show Max", false)

    private var updateTime = 0L
    private var prevFps = 0
    private var currentFps = 0

    private val longFps = CircularArray(10, 0)

    private var prevAvgFps = 0
    private var currentAvgFps = 0

    @JvmStatic
    fun updateFps(fps: Int) {
        prevFps = currentFps
        currentFps = fps

        longFps.add(fps)

        prevAvgFps = currentAvgFps
        currentAvgFps = longFps.average().roundToInt()
        updateTime = System.currentTimeMillis()
    }

    override fun SafeClientEvent.updateText() {
        val deltaTime = AnimationUtils.toDeltaTimeFloat(updateTime) / 1000.0f
        val fps = (prevFps + (currentFps - prevFps) * deltaTime).roundToInt()
        val avg = (prevAvgFps + (currentAvgFps - prevAvgFps) * deltaTime).roundToInt()

        displayText.add("FPS", secondaryColor)
        displayText.add(fps.toString(), primaryColor)

        if (showAverage) {
            displayText.add("AVG", secondaryColor)
            displayText.add(avg.toString(), primaryColor)
        }

        if (showMin) {
            displayText.add("MIN", secondaryColor)
            displayText.add(longFps.min().toString(), primaryColor)
        }

        if (showMax) {
            displayText.add("MAX", secondaryColor)
            displayText.add(longFps.max().toString(), primaryColor)
        }
    }

}