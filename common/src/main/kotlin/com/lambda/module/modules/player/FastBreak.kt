package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.renderer.esp.global.BlockESPRenderer
import com.lambda.graphics.renderer.esp.global.buildFilled
import com.lambda.graphics.renderer.esp.global.buildOutline
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.transform
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import com.lambda.util.math.MathUtils.lerp
import net.minecraft.util.math.Box
import java.awt.Color

object FastBreak : Module(
    name = "FastBreak",
    description = "Break blocks faster.",
    defaultTags = setOf(
        ModuleTag.PLAYER, ModuleTag.WORLD
    )
) {
    private val page by setting("Page", Page.Mining)

    private val breakDelay by setting("Break Delay", 5, 0..5, 1, unit = "ticks", description = "The tick delay between breaking blocks", visibility = { page == Page.Mining })
    private val breakThreshold by setting("Break Threshold", 0.7f, 0.2f..1.0f, 0.1f, description = "The progress at which the block will break.", visibility = { page == Page.Mining })

    private val renderMode by setting("Render Mode", RenderMode.InOut, "The animation style of the renders", visibility = { page == Page.Render })
    private val renderSetting by setting("Render Setting", RenderSetting.Both, "The different ways to draw the renders", visibility = { page == Page.Render && renderMode.isEnabled() })
    private val fillColor by setting("Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline })
    private val outlineColor by setting("Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })
    private val outlineWidth by setting("Outline Width", 1f, 0f..3f, 0.1f, "the thickness of the outline", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })


    private val renderer = BlockESPRenderer
    private var boxSet = emptySet<Box>()

    private enum class Page {
        Mining, Render
    }

    private enum class RenderMode {
        InOut, OutIn, None;

        fun isEnabled(): Boolean =
            this != None
    }

    private enum class RenderSetting {
        Both, Fill, Outline
    }

    init {
        listener<PacketEvent.Send.Pre> {
            if (it.packet !is PlayerActionC2SPacket
                || it.packet.action != Action.STOP_DESTROY_BLOCK
            ) return@listener

            connection.sendPacket(
                PlayerActionC2SPacket(
                    Action.ABORT_DESTROY_BLOCK,
                    // For the exploit to work, the position must be outside the player range, so any
                    // position farther than 6 blocks will work.
                    // This is only required for grim 2 and potentially grim 3 in the future if they update it
                    it.packet.pos.up(2024 - 4 - 18),
                    it.packet.direction
                )
            )
        }

        listener<TickEvent.Pre> {
            interaction.blockBreakingCooldown = interaction.blockBreakingCooldown.coerceAtMost(breakDelay)
        }

        listener<InteractionEvent.BreakingProgress.Pre> {
            it.progress += world.getBlockState(it.pos)
                .calcBlockBreakingDelta(player, world, it.pos) * (1 - breakThreshold)
        }

        listener<TickEvent.Post> {
            if (!renderMode.isEnabled()) return@listener

            val pos = interaction.currentBreakingPos
            boxSet = world.getBlockState(pos).getOutlineShape(world, pos).boundingBoxes.toSet()
        }

        listener<RenderEvent.World> {
            if (!interaction.isBreakingBlock || !renderMode.isEnabled()) return@listener

            val pos = interaction.currentBreakingPos
            val breakDelta = world.getBlockState(pos).calcBlockBreakingDelta(player, world, pos)

            renderer.clear()
            boxSet.forEach { box ->
                val lerpBox = lerp(
                    getLerp(box, interaction.currentBreakingProgress - breakDelta).offset(pos)
                    , getLerp(box, interaction.currentBreakingProgress).offset(pos)
                    , mc.tickDelta.toDouble()
                )

                if (renderSetting != RenderSetting.Outline) {
                    renderer.buildFilled(lerpBox, fillColor)
                }

                if (renderSetting != RenderSetting.Fill) {
                    // Currently there isn't an outline method and I don't want to mess with anything that might have different plans
                    renderer.buildOutline(lerpBox, outlineColor)
                }
            }
            renderer.upload()
        }
    }

    private fun getLerp(box: Box, factor: Float): Box {
        return if (renderMode == RenderMode.InOut) {
            lerp(Box(box.center, box.center), box, factor.toDouble())
        } else {
            lerp(box, Box(box.center, box.center), factor.toDouble())
        }
    }

    fun SafeContext.interpolateProgress(min: Double = 0.0, max: Double = 1.0) =
        transform(interaction.currentBreakingProgress.toDouble(), 0.0, 1.0, min, max)
}
