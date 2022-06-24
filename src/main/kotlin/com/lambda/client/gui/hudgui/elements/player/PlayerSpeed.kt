package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.InfoCalculator.speed
import java.util.*

internal object PlayerSpeed : LabelHud(
    name = "PlayerSpeed",
    category = Category.PLAYER,
    description = "Player movement speed"
) {

    private val speedUnit by setting("Speed Unit", SpeedUnit.MPS)
    private val averageSpeedTime by setting("Average Speed Ticks", 10, 1..50, 1)

    @Suppress("UNUSED")
    private enum class SpeedUnit(override val displayName: String, val multiplier: Double) : DisplayEnum {
        MPS("m/s", 1.0),
        KMH("km/h", 3.6),
        MPH("mph", 2.237) // Monkey Americans
    }

    private val speedList = ArrayDeque<Double>()

    override fun SafeClientEvent.updateText() {
        updateSpeedList()

        var averageSpeed = if (speedList.isEmpty()) 0.0 else speedList.sum() / speedList.size

        averageSpeed *= speedUnit.multiplier
        averageSpeed = MathUtils.round(averageSpeed, 2)

        displayText.add("%.2f".format(averageSpeed), primaryColor)
        displayText.add(speedUnit.displayName, secondaryColor)
    }

    private fun SafeClientEvent.updateSpeedList() {
        val speed = speed()

        if (speed > 0.0 || mc.player.ticksExisted % 4 == 0) {
            speedList.add(speed) // Only adding it every 4 ticks if speed is 0
        } else {
            speedList.pollFirst()
        }

        while (speedList.size > averageSpeedTime) speedList.pollFirst()
    }

}