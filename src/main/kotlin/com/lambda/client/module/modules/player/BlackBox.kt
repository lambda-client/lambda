package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.InfoCalculator.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.roundToInt

object BlackBox : Module(
    name = "BlackBox",
    category = Category.PLAYER,
    description = "Logs info about player (you)"
) {

    private val delay by setting("Delay", 10, 1..300, 1)
    private val toChat by setting("Log to chat", true)
    private val coords by setting("Log Coordinates", true)
    private val dimension by setting("Log Dimension", true)
    private val speed by setting("Log Speed", true)
    private val health by setting("Log Health", true)
    private val xp by setting("Log XP level", true)

    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (timer.tick(delay)) {
                val list = mutableListOf<String>()
                if (coords) list.add("coordinates: ${mc.player.position.x} ${mc.player.position.y} ${mc.player.position.z}")
                if (dimension) list.add("dimension: ${getDimensionName(mc.player.dimension)}")
                if (speed) list.add("speed: ${speed().roundToInt()} m/s")
                if (health) list.add("health: ${mc.player.health + mc.player.absorptionAmount}")
                if (xp) list.add("xp level: ${mc.player.experienceLevel}")

                list.forEach {
                    LambdaMod.LOG.info(it)
                    if (toChat) MessageSendHelper.sendChatMessage(it)
                }
            }
        }
    }

    private fun getDimensionName(dim: Int) = when (dim) {
        -1 -> "nether"
        0 -> "overworld"
        else -> "end"
    }
}