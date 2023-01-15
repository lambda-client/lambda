package com.lambda.client.module.modules.movement

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PlayerEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.MovementUtils.centerPlayer
import com.lambda.client.util.MovementUtils.isCentered
import com.lambda.client.util.combat.SurroundUtils
import com.lambda.client.util.combat.SurroundUtils.checkHole
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.BlockPos

object Anchor : Module(
    name = "Anchor",
    description = "Stops your motion when you are above hole",
    category = Category.MOVEMENT
) {
    private val mode by setting("Mode", Mode.BOTH)
    private val autoCenter by setting("Auto Center", true)
    private val stopYMotion by setting("Stop Y Motion", false)
    private val disableInHole by setting("Disable In Hole", false)
    private val pitchTrigger by setting("Pitch Trigger", true)
    private val pitch by setting("Pitch", 75, 0..80, 1, { pitchTrigger })
    private val verticalRange by setting("Vertical Range", 3, 1..5, 1)

    private enum class Mode {
        BOTH, BEDROCK
    }

    private var wasInHole = false

    init {
        onDisable {
            wasInHole = false
        }

        safeListener<PlayerEvent.Move> {
            val isInHole = player.onGround && isHole(player.flooredPosition)
            val validPitch = !pitchTrigger || player.rotationPitch > pitch

            // Disable after in hole for 2 ticks
            if (wasInHole && isInHole && disableInHole) {
                disable()
                return@safeListener
            }

            if (validPitch) {
                // Stops XZ motion
                if (isInHole || isAboveHole()) {
                    if (player.isCentered(player.flooredPosition)) {
                        player.motionX = 0.0
                        player.motionZ = 0.0
                    } else if (autoCenter) {
                        player.centerPlayer()
                    }
                }

                // Stops Y motion
                if (stopYMotion && isInHole) {
                    player.motionY = -0.08 // Minecraft needs this for on ground check
                }
            }

            wasInHole = isInHole
        }
    }

    /**
     * Checks whether the player is above hole
     */
    private fun SafeClientEvent.isAboveHole(): Boolean {
        val flooredPos = player.flooredPosition

        for (yOffset in 1..verticalRange) {
            val pos = flooredPos.down(yOffset)
            if (!world.isAirBlock(pos)) return false
            if (isHole(pos)) return true
        }

        return false
    }

    /**
     * Checks whether the specified block position is a valid type of hole
     */
    private fun SafeClientEvent.isHole(pos: BlockPos): Boolean {
        val type = checkHole(pos)
        return mode == Mode.BOTH && type != SurroundUtils.HoleType.NONE
            || mode == Mode.BEDROCK && type == SurroundUtils.HoleType.BEDROCK
    }

}