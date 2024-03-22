package com.lambda.module.modules

import com.lambda.config.InteractionSettings
import com.lambda.config.RotationSettings
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object RotationTest : Module(
    name = "RotationTest",
    description = "Test rotation",
    defaultTags = setOf()
) {
    private val rotationConfig = RotationSettings(this)
    private val interactionConfig = InteractionSettings(this)

    private var pos: BlockPos = BlockPos.ORIGIN
    private var side = Direction.UP

    init {
        onEnable {
            val hit = mc.crosshairTarget as? BlockHitResult ?: return@onEnable
            pos = hit.blockPos
            side = hit.side
        }

        listener<RotationEvent.Pre> { event ->
//            val target = getClosestEntity<VillagerEntity>(
//                player.eyePos, interaction.reachDistance.toDouble()
//            ) ?: return@listener

            event.lookAtBlock(rotationConfig, interactionConfig, pos, setOf(side))
        }

        listener<RotationEvent.Post> {
            (mc.crosshairTarget as? BlockHitResult)?.let { hit ->
                if (hit.blockPos != pos || hit.side != side) {
                    interaction.cancelBlockBreaking()
                    return@listener
                }

                interaction.updateBlockBreakingProgress(pos, side)
                player.swingHand(Hand.MAIN_HAND)
                return@listener
            }

            interaction.cancelBlockBreaking()
        }
    }
}