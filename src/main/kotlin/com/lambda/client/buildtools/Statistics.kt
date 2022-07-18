package com.lambda.client.buildtools

import com.lambda.client.buildtools.BuildToolsManager.disableError
import com.lambda.client.buildtools.task.TaskProcessor.packetLimiter
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.elements.client.BuildToolsHud
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedDeque

object Statistics {
    val simpleMovingAveragePlaces = ConcurrentLinkedDeque<Long>()
    val simpleMovingAverageBreaks = ConcurrentLinkedDeque<Long>()
    var totalBlocksPlaced = 0
    var totalBlocksBroken = 0
    private var runTime = 0L
    private var prevFood = 0
    private var foodLoss = 1
    private var lastToolDamage = 0
    var durabilityUsages = 0

    fun setupStatistics() {
        simpleMovingAveragePlaces.clear()
        simpleMovingAverageBreaks.clear()
        runTime = System.currentTimeMillis()
        totalBlocksPlaced = 0
        totalBlocksBroken = 0
        prevFood = 0
        foodLoss = 1
        lastToolDamage = 0
        durabilityUsages = 0
    }

    fun SafeClientEvent.updateStatistics() {
        runTime += 50
        updateFoodLevel()
        updateDequeues()
    }

    private fun SafeClientEvent.updateFoodLevel() {
        val currentFood = player.foodStats.foodLevel
        if (currentFood < 7.0) {
            disableError("Out of food")
        }
        if (currentFood != prevFood) {
            if (currentFood < prevFood) foodLoss++
            prevFood = currentFood
        }
    }

    private fun updateDequeues() {
        val removeTime = System.currentTimeMillis() - BuildToolsHud.simpleMovingAverageRange * 1000L

        updateDeque(simpleMovingAveragePlaces, removeTime)
        updateDeque(simpleMovingAverageBreaks, removeTime)

        updateDeque(packetLimiter, System.currentTimeMillis() - 1000L)
    }

    private fun updateDeque(deque: ConcurrentLinkedDeque<Long>, removeTime: Long) {
        while (deque.isNotEmpty() && deque.first() < removeTime) {
            deque.removeFirst()
        }
    }

    fun printFinishStats(additionalMassage: String) {
        val message = "    §9> §7Placed blocks: §a%,d§r".format(totalBlocksPlaced) +
            "\n    §9> §7Destroyed blocks: §a%,d§r".format(totalBlocksBroken) + additionalMassage

        MessageSendHelper.sendRawChatMessage(message)
    }

    fun gatherStatistics(displayText: TextComponent) {

    }
}