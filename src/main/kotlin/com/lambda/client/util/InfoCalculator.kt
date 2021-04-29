package com.lambda.client.util

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.TimerManager
import com.lambda.client.util.MovementUtils.realSpeed

object InfoCalculator {
    private val mc = Wrapper.minecraft

    fun getServerType() = if (mc.isIntegratedServerRunning) "Singleplayer" else mc.currentServerData?.serverIP
        ?: "Main Menu"

    fun ping() = mc.player?.let { mc.connection?.getPlayerInfo(it.uniqueID)?.responseTime ?: 1 } ?: -1

    fun SafeClientEvent.speed(): Double {
        val tps = 1000.0 / TimerManager.tickLength
        return player.realSpeed * tps
    }

    fun dimension() = when (mc.player?.dimension) {
        -1 -> "Nether"
        0 -> "Overworld"
        1 -> "End"
        else -> "No Dimension"
    }
}
