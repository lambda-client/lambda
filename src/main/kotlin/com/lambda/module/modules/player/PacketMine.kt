/*
 * Copyright 2025 Lambda
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

import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.HotbarSettings
import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.breaking.BreakRequest.Companion.breakRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.math.distSq
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import com.lambda.util.world.raycast.InteractionMask
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import java.awt.Color
import java.util.concurrent.ConcurrentLinkedQueue

object PacketMine : Module(
    name = "PacketMine",
    description = "automatically breaks blocks, and does it faster",
    tag = ModuleTag.PLAYER
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Build("Build"),
        Rotation("Rotation"),
        Interaction("Interaction"),
        Inventory("Inventory"),
        Hotbar("Hotbar"),
        Render("Render")
    }

    private val rebreakMode by setting("Rebreak Mode", RebreakMode.Manual, "The method used to re-break blocks after they've been broken once") { breakConfig.rebreak }.group(Group.General)
    private val breakRadius by setting("Break Radius", 0, 0..5, 1, "Selects and breaks all blocks within the break radius of the selected block").group(Group.General)
    private val flatten by setting("Flatten", true, "Wont allow breaking extra blocks under your players position") { breakRadius > 0 }.group(Group.General)
    private val queue by setting("Queue", false, "Queues blocks to break so you can select multiple at once").group(Group.General)
        .onValueChange { _, to -> if (!to) queuePositions.clear() }
    private val queueOrder by setting("Queue Order", QueueOrder.Standard, "Which end of the queue to break blocks from") { queue }.group(Group.General)

    private val build = BuildSettings(this, Group.Build)
    private val breakConfig = build.breaking
    private val rotation = RotationSettings(this, Group.Rotation)
    private val interact = InteractionSettings(this, Group.Interaction, InteractionMask.Block)
    private val inventory = InventorySettings(this, Group.Inventory)
    private val hotbar = HotbarSettings(this, Group.Hotbar)

    private val renderQueue by setting("Render Queue", true, "Adds renders to signify what block positions are queued").group(Group.Render)
    private val renderSize by setting("Render Size", 0.3f, 0.01f..1f, 0.01f, "The scale of the queue renders") { renderQueue }.group(Group.Render)
    private val renderMode by setting("Render Mode", RenderMode.State, "The style of the queue renders") { renderQueue }.group(Group.Render)
    private val dynamicColor by setting("Dynamic Color", true, "Interpolates the color between start and end") { renderQueue }.group(Group.Render)
    private val staticColor by setting("Color", Color(255, 0, 0, 60).brighter()) { renderQueue && !dynamicColor }.group(Group.Render)
    private val startColor by setting("Start Color", Color(255, 255, 0, 60).brighter(), "The color of the start (closest to breaking) of the queue") { renderQueue && dynamicColor }.group(Group.Render)
    private val endColor by setting("End Color", Color(255, 0, 0, 60).brighter(), "The color of the end (farthest from breaking) of the queue") { renderQueue && dynamicColor }.group(Group.Render)

    private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()

    private var breaks = 0
    private var itemDrops = 0

    private val breakPositions = arrayOfNulls<BlockPos>(2)
    private val queuePositions = ArrayList<MutableCollection<BlockPos>>()
    private val SafeContext.queueSorted
        get() = when (queueOrder) {
            QueueOrder.Standard -> queuePositions
            QueueOrder.Reversed -> queuePositions.asReversed()
            QueueOrder.Closest -> queuePositions.sortedBy {
                it.firstOrNull()
                    ?.toCenterPos()
                    ?.let { center ->
                        center distSq player.pos
                    } ?: Double.MAX_VALUE
            }
        }

    private var reBreakPos: BlockPos? = null
    private var attackedThisTick = false

    init {
        listen<TickEvent.Post> {
            attackedThisTick = false
        }

        listen<PlayerEvent.Attack.Block> {
            it.cancel()
        }

        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()
            val pos = event.pos
            val positions = mutableListOf<BlockPos>().apply {
                if (breakRadius <= 0) {
                    add(pos)
                    return@apply
                }
                BlockPos.iterateOutwards(pos, breakRadius, breakRadius, breakRadius).forEach { blockPos ->
                    if (blockPos distSq pos <= (breakRadius * breakRadius) && (!flatten || (blockPos.y >= player.blockPos.y || blockPos == pos))) {
                        add(blockPos.toImmutable())
                    }
                }
            }
            positions.removeIf { breakPos ->
                (queue && queuePositions.any { it == breakPos }) || breakPos == breakPositions[1]
            }
            if (positions.isEmpty()) return@listen
            val activeBreaking = if (queue) {
                queuePositions.add(positions)
                breakPositions.toList() + queueSorted.flatten()
            } else {
                queuePositions.clear()
                queuePositions.add(positions)
                queuePositions.flatten() + if (breakConfig.doubleBreak) {
                    breakPositions[1] ?: breakPositions[0]
                } else null
            }
            requestBreakManager(activeBreaking)
            attackedThisTick = true
            queuePositions.trimToSize()
        }

        listen<TickEvent.Input.Post> {
            if (!attackedThisTick) {
                requestBreakManager((breakPositions + queueSorted.flatten()).toList())
                if (!breakConfig.rebreak || (rebreakMode != RebreakMode.Auto && rebreakMode != RebreakMode.AutoConstant)) return@listen
                val reBreak = reBreakPos ?: return@listen
                requestBreakManager(listOf(reBreak), true)
            }
        }

        listen<RenderEvent.StaticESP> { event ->
            if (!renderQueue) return@listen
            queueSorted.forEachIndexed { index, positions ->
                positions.forEach { pos ->
                    val color = if (dynamicColor) lerp(index / queuePositions.size.toDouble(), startColor, endColor)
                    else staticColor
                    val boxes = when (renderMode) {
                        RenderMode.State -> blockState(pos).getOutlineShape(world, pos).boundingBoxes
                        RenderMode.Box -> listOf(Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0))
                    }.map { lerp(renderSize.toDouble(), Box(it.center, it.center), it).offset(pos) }

                    boxes.forEach { box ->
                        event.renderer.buildFilled(box, color)
                        event.renderer.buildOutline(box, color.setAlpha(1.0))
                    }
                }
            }
        }

        onDisable {
            breakPositions[0] = null
            breakPositions[1] = null
            queuePositions.clear()
            reBreakPos = null
            attackedThisTick = false
        }
    }

    private fun SafeContext.requestBreakManager(requestPositions: Collection<BlockPos?>, reBreaking: Boolean = false) {
        if (requestPositions.count { it != null } <= 0) return
        val breakContexts = breakContexts(requestPositions)
        if (!reBreaking) {
            queuePositions.retainAllPositions(breakContexts)
        }
        breakRequest(
            breakContexts, pendingInteractions, rotation, hotbar, interact, inventory, build,
        ) {
            onStart { queuePositions.removePos(it); addBreak(it) }
            onUpdate { queuePositions.removePos(it) }
            onStop { removeBreak(it); breaks++ }
            onCancel { removeBreak(it, true) }
            onReBreakStart { reBreakPos = it }
            onReBreak { reBreakPos = it }
        }.submit()
    }

    private fun SafeContext.breakContexts(positions: Collection<BlockPos?>) =
        positions
            .asSequence()
            .filterNotNull()
            .associateWith { TargetState.State(blockState(it).fluidState.blockState) }
            .toBlueprint()
            .simulate(player.eyePos, interact, rotation, inventory, build)
            .asSequence()
            .filterIsInstance<BreakResult.Break>()
            .map { it.context }
            .toCollection(mutableListOf())

    private fun addBreak(pos: BlockPos) {
        if (breakConfig.doubleBreak && breakPositions[0] != null) {
            breakPositions[1] = breakPositions[0]
        }
        breakPositions[0] = pos
        reBreakPos = null
    }

    private fun removeBreak(pos: BlockPos, includeReBreak: Boolean = false) {
        breakPositions.forEachIndexed { index, breakPos ->
            if (breakPos == pos) {
                breakPositions[index] = null
            }
        }
        if (includeReBreak && pos == reBreakPos) {
            reBreakPos = null
        }
    }

    private fun ArrayList<MutableCollection<BlockPos>>.removePos(element: BlockPos): Boolean {
        var anyRemoved = false
        removeIf {
            val removed = it.remove(element)
            anyRemoved = anyRemoved or removed
            return@removeIf removed && it.isEmpty()
        }
        return anyRemoved
    }

    private fun ArrayList<MutableCollection<BlockPos>>.retainAllPositions(positions: Collection<BreakContext>): Boolean {
        var modified = false
        forEach {
            modified = modified or it.retainAll { pos ->
                positions.any { retain ->
                    retain.blockPos == pos
                }
            }
        }
        return modified
    }

    private fun ArrayList<MutableCollection<BlockPos>>.any(predicate: (BlockPos) -> Boolean): Boolean {
        if (isEmpty()) return false
        forEach { if (it.any(predicate)) return true }
        return false
    }

    enum class RebreakMode(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        Manual("Manual", "Re-break only when you trigger it explicitly."),
        Auto("Auto", "Automatically re-break when it’s beneficial or required."),
        AutoConstant("Auto (Constant)", "Continuously re-break as soon as conditions allow; most aggressive.")
    }

    enum class QueueOrder(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        Standard("Standard", "Process in planned order (first in, first out)."),
        Reversed("Reversed", "Process in reverse planned order (last in, first out)."),
        Closest("Closest", "Process the closest targets first.")
    }

    private enum class RenderMode(
        override val displayName: String,
        override val description: String
    ) : NamedEnum, Describable {
        State("State", "Render the actual block state for preview."),
        Box("Box", "Render a simple box to show position and size.")
    }
}
