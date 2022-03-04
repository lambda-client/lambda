package com.lambda.client.module.modules.movement

import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

object FastFall : Module(
    name = "FastFall",
    description = "Makes you fall faster",
    category = Category.MOVEMENT,
    modulePriority = 50
) {
    private val mode by setting("Mode", Mode.MOTION)
    private val fallSpeed by setting("Fall Speed", 6.0, 0.1..10.0, 0.1)
    private val fallDistance by setting("Max Fall Distance", 2, 0..10, 1)

    private var timering = false
    private var motioning = false

    private enum class Mode {
        MOTION, TIMER
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (player.onGround
                || player.isElytraFlying
                || player.isInLava
                || player.isInWater
                || player.isInWeb
                || player.fallDistance < fallDistance
                || player.capabilities.isFlying) {
                reset()
                return@safeListener
            }

            when (mode) {
                Mode.MOTION -> {
                    player.motionY -= fallSpeed
                    motioning = true
                }
                Mode.TIMER -> {
                    modifyTimer(50.0f / (fallSpeed * 2.0f).toFloat())
                    timering = true
                }
            }
        }
    }

    override fun getHudInfo(): String {
        return if (timering || motioning) "ACTIVE"
        else ""
    }

    private fun reset() {
        if (timering) {
            resetTimer()
            timering = false
        }
        motioning = false
    }
}
