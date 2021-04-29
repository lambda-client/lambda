package com.lambda.client.module.modules.movement

import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.mixin.extension.isInWeb
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object FastFall : Module(
    name = "FastFall",
    category = Category.MOVEMENT,
    description = "Makes you fall faster",
    modulePriority = 50
) {
    private val mode = setting("Mode", Mode.MOTION)
    private val fallSpeed = setting("Fall Speed", 6.0, 0.1..10.0, 0.1)
    private val fallDistance = setting("Max Fall Distance", 2, 0..10, 1)

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
                || player.fallDistance < fallDistance.value
                || player.capabilities.isFlying) {
                reset()
                return@safeListener
            }

            when (mode.value) {
                Mode.MOTION -> {
                    player.motionY -= fallSpeed.value
                    motioning = true
                }
                Mode.TIMER -> {
                    modifyTimer(50.0f / (fallSpeed.value * 2.0f).toFloat())
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
