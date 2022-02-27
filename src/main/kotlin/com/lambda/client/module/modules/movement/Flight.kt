package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener

object Flight : Module(
    name = "Flight",
    category = Category.MOVEMENT,
    description = "Makes the player fly",
    modulePriority = 500
) {
    private val mode by setting("Mode", FlightMode.VANILLA)
    private val speed by setting("Speed", 1.0f, 0.0f..10.0f, 0.1f)
    private val glideSpeed by setting("Glide Speed", 0.05, 0.0..0.3, 0.001)

    private enum class FlightMode {
        VANILLA, STATIC
    }

    init {
        onDisable {
            runSafe {
                player.capabilities?.apply {
                    isFlying = false
                    flySpeed = 0.05f
                }
            }
        }

        safeListener<PlayerTravelEvent> {
            when (mode) {
                FlightMode.STATIC -> {
                    player.capabilities.isFlying = true
                    player.capabilities.flySpeed = speed

                    player.motionX = 0.0
                    player.motionY = -glideSpeed
                    player.motionZ = 0.0

                    if (mc.gameSettings.keyBindJump.isKeyDown) player.motionY += speed / 2.0f
                    if (mc.gameSettings.keyBindSneak.isKeyDown) player.motionY -= speed / 2.0f
                }
                FlightMode.VANILLA -> {
                    player.capabilities.isFlying = true
                    player.capabilities.flySpeed = speed / 11.11f

                    if (glideSpeed != 0.0
                        && !mc.gameSettings.keyBindJump.isKeyDown
                        && !mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -glideSpeed
                }
            }
        }
    }
}
