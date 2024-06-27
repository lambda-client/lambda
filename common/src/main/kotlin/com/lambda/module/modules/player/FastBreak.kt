package com.lambda.module.modules.player

import com.lambda.event.events.InteractionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.Block
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import java.awt.Color

object FastBreak : Module(
    name = "FastBreak",
    description = "Break blocks faster.",
    defaultTags = setOf(
        ModuleTag.PLAYER, ModuleTag.WORLD
    )
) {
    private val page by setting("Page", Page.Mining)

    private val mineSpeed by setting("Mine Speed", 5, 0..5, 1, unit = "ticks", description = "Reduce the mining cooldown.", visibility = { page == Page.Mining })
    private val breakThreshold by setting("Break Threshold", 0.7f, 0.2f..1.0f, 0.1f, description = "The progress at which the block will break.", visibility = { page == Page.Mining })

    private val renderOutline by setting("Render Outline", true, description = "Render an outline around the block being broken.", visibility = { page == Page.Rendering })
    private val renderOutlineColor by setting("Outline Color", Color.CYAN, description = "The color of the outline.", visibility = { page == Page.Rendering && renderOutline })
    private val renderFill by setting("Render Fill", true, description = "Fill the block with a color based on the mining progress.", visibility = { page == Page.Rendering })
    private val renderFillColor by setting("Fill Color", Color.CYAN, description = "The color of the fill.", visibility = { page == Page.Rendering && renderFill })
    private val renderFillGrow by setting("Fill Grow", 0.0f, 0.0f..1.0f, 0.1f, description = "The amount the fill grows based on the mining progress.", visibility = { page == Page.Rendering && renderFill })

    private var breakingBlock: Block? = null

    private enum class Page {
        Mining, Rendering
    }

    init {
        listener<PacketEvent.Send.Pre> {
            if (it.packet !is PlayerActionC2SPacket) return@listener
            if (it.packet.action != Action.STOP_DESTROY_BLOCK) return@listener

            connection.sendPacket(PlayerActionC2SPacket(
                Action.ABORT_DESTROY_BLOCK,
                // For the exploit to work, the position must be out of the player range, so any
                // position farther than 6 blocks will work.
                it.packet.pos.up(2024-4-18),
                it.packet.direction
            ))
        }

        listener<TickEvent.Pre> {
            if (!interaction.isBreakingBlock)
                return@listener

            // This should work but doesn't ?
            interaction.blockBreakingCooldown -= mineSpeed
        }

        listener<InteractionEvent.UpdateBlockBreakingProgress.Post> {
            if (!interaction.isBreakingBlock || interaction.currentBreakingProgress < breakThreshold) return@listener

            connection.sendPacket(PlayerActionC2SPacket(
                Action.STOP_DESTROY_BLOCK
                , interaction.currentBreakingPos
                , mc.crosshairTarget?.blockResult?.side)
            )
        }

        listener<InteractionEvent.GetBlockBreakingProgress> {
            it.value = (interaction.currentBreakingProgress * (2f - breakThreshold) * 10).toInt().coerceAtMost(9)
        }

        listener<RenderEvent.World> {
            // Here we render an outline around the block being broken.
            // Then we fill with a color based on the mining progress.
            // We should use interpolation to make the fill grow smoothly.
        }
    }
}
