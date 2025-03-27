/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.global.DynamicESP
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.lerp
import com.lambda.util.math.transform
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
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

    private val renderMode by setting("Render Mode", RenderMode.Out, "The animation style of the renders", visibility = { page == Page.Render })
    private val renderSetting by setting("Render Setting", RenderSetting.Both, "The different ways to draw the renders", visibility = { page == Page.Render && renderMode.isEnabled() })

    private val fillColourMode by setting("Fill Mode", ColourMode.Dynamic, visibility = { page == Page.Render && renderSetting != RenderSetting.Outline })
    private val staticFillColour by setting("Static Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the static fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Static })
    private val startFillColour by setting("Start Fill Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the start fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Dynamic })
    private val endFillColour by setting("End Fill Colour", Color(0f, 1f, 0f, 0.3f), "The colour used to render the end fill of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Outline && fillColourMode == ColourMode.Dynamic  })

    private val outlineColourMode by setting("Outline Mode", ColourMode.Dynamic, visibility = { page == Page.Render && renderSetting != RenderSetting.Fill })
    private val staticOutlineColour by setting("Static Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the static outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Static })
    private val startOutlineColour by setting("Start Outline Colour", Color(1f, 0f, 0f, 0.3f), "The colour used to render the start outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Dynamic })
    private val endOutlineColour by setting("End Outline Colour", Color(0f, 1f, 0f, 0.3f), "The colour used to render the end outline of the box", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill && outlineColourMode == ColourMode.Dynamic  })
    private val outlineWidth by setting("Outline Width", 1f, 0f..3f, 0.1f, "the thickness of the outline", visibility = { page == Page.Render && renderMode.isEnabled() && renderSetting != RenderSetting.Fill })


    private val renderer = DynamicESP
    private var boxSet = emptySet<Box>()

    private enum class Page {
        Mining, Render
    }

    private enum class RenderMode {
        Out, In, InOut, OutIn, Static, None;

        fun isEnabled(): Boolean =
            this != None
    }

    private enum class ColourMode {
        Static, Dynamic
    }

    private enum class RenderSetting {
        Both, Fill, Outline
    }

    init {
        listen<PacketEvent.Send.Pre> {
            if (it.packet !is PlayerActionC2SPacket
                || it.packet.action != Action.STOP_DESTROY_BLOCK
            ) return@listen

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

        listen<TickEvent.Pre> {
            interaction.blockBreakingCooldown = interaction.blockBreakingCooldown.coerceAtMost(breakDelay)
        }

        listen<PlayerEvent.Breaking.Update> {
            it.progress += world.getBlockState(it.pos)
                .calcBlockBreakingDelta(player, world, it.pos) * (1 - breakThreshold)
        }

        listen<TickEvent.Post> {
            if (!renderMode.isEnabled()) return@listen

            val pos = interaction.currentBreakingPos
            boxSet = world.getBlockState(pos).getOutlineShape(world, pos).boundingBoxes.toSet()
        }

        listen<RenderEvent.World> {
            if (!interaction.isBreakingBlock || !renderMode.isEnabled()) return@listen

            val pos = interaction.currentBreakingPos
            val breakDelta = world.getBlockState(pos).calcBlockBreakingDelta(player, world, pos)

            renderer.clear()
            boxSet.forEach { box ->
                val previousFactor = interaction.currentBreakingProgress - breakDelta
                val nextFactor = interaction.currentBreakingProgress
                val currentFactor = lerp(mc.tickDelta, previousFactor, nextFactor)

                val fillColour = if (fillColourMode == ColourMode.Dynamic) {
                    lerp(currentFactor.toDouble(), startFillColour, endFillColour)
                } else {
                    staticFillColour
                }

                val outlineColour = if (outlineColourMode == ColourMode.Dynamic) {
                    lerp(currentFactor.toDouble(), startOutlineColour, endOutlineColour)
                } else {
                    staticOutlineColour
                }

                val renderBox = if (renderMode != RenderMode.Static) {
                    getLerpBox(box, currentFactor).offset(pos)
                } else {
                    box.offset(pos)
                }

                val dynamicAABB = DynamicAABB()
                dynamicAABB.update(renderBox)

                if (renderSetting != RenderSetting.Outline) renderer.buildFilled(dynamicAABB, fillColour)
                if (renderSetting != RenderSetting.Fill) renderer.buildOutline(dynamicAABB, outlineColour)
            }
            renderer.upload()
        }
    }

    private fun getLerpBox(box: Box, factor: Float): Box {
        val boxCenter = Box(box.center, box.center)
        return when (renderMode) {
            RenderMode.Out -> lerp(factor.toDouble(), boxCenter, box)
            RenderMode.In -> lerp(factor.toDouble(), box, boxCenter)
            RenderMode.InOut -> {
                if (factor >= 0.5f) lerp((factor.toDouble() - 0.5) * 2, boxCenter, box)
                else lerp(factor.toDouble() * 2, box, boxCenter)
            }
            RenderMode.OutIn -> {
                if (factor >= 0.5f) lerp((factor.toDouble() - 0.5) * 2, box, boxCenter)
                else lerp(factor.toDouble() * 2, boxCenter, box)
            }
            else -> box
        }
    }

    fun SafeContext.interpolateProgress(min: Double = 0.0, max: Double = 1.0) =
        transform(interaction.currentBreakingProgress.toDouble(), 0.0, 1.0, min, max)
}
