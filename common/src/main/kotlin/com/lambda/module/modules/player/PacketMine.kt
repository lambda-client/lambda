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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.breaking.BreakRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.world.raycast.InteractionMask
import net.minecraft.util.math.BlockPos
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

object PacketMine : Module(
    "PacketMine",
    "automatically breaks blocks, and does it faster",
    setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.Build)

    private val build = BuildSettings(this) { page == Page.Build }
    private val breakConfig = build.breaking
    private val rotation = RotationSettings(this) { page == Page.Rotation }
    private val interact = InteractionSettings(this, InteractionMask.Block) { page == Page.Interaction }
    private val inventory = InventorySettings(this) { page == Page.Inventory }
    private val hotbar = HotbarSettings(this) { page == Page.Hotbar }

    private val reBreakMode by setting("ReBreak Mode", ReBreakMode.Manual, "The method used to re-break blocks after they've been broken once") { breakConfig.reBreak }
    private val queue by setting("Queue", false, "Queues blocks to break so you can select multiple at once")
        .onValueChange { _, to -> if (!to) queuePositions.clear() }
    private val queueOrder by setting("Queue Order", QueueOrder.Standard, "Which end of the queue to break blocks from") { queue }

    private val pendingInteractionsList = ConcurrentLinkedQueue<BuildContext>()

    private var breaks = 0
    private var itemDrops = 0

    private val breakPositions = arrayOfNulls<BlockPos>(2)
    private val queuePositions = LinkedList<BlockPos>()
    private val queueSorted
        get() = when (queueOrder) {
            QueueOrder.Standard -> queuePositions
            QueueOrder.Reversed -> queuePositions.asReversed()
        }

    private var reBreakPos: BlockPos? = null

    private var attackedThisTick = false

    init {
        listen<TickEvent.Post> {
            attackedThisTick = false
        }

        //ToDo: run on every tick stage
        listen<TickEvent.Pre> {
            if (!breakConfig.reBreak || (reBreakMode != ReBreakMode.Auto && reBreakMode != ReBreakMode.AutoConstant)) return@listen
            val reBreak = reBreakPos ?: return@listen
            requestBreakManager(listOf(reBreak), true)
        }

        listen<PlayerEvent.Attack.Block> { it.cancel() }
        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()
            if ((breakPositions + queuePositions).any { it == event.pos }) return@listen
            val activeBreaking = if (queue) {
                queuePositions.addLast(event.pos)
                breakPositions + queueSorted
            } else {
                arrayOf<BlockPos?>(event.pos) + if (breakConfig.doubleBreak) {
                    breakPositions[1] ?: breakPositions[0]
                } else null
            }
            requestBreakManager(activeBreaking.toList())
            attackedThisTick = true
        }

        listen<TickEvent.Input.Post> {
            if (!attackedThisTick) requestBreakManager((breakPositions + queueSorted).toList())
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
        if (requestPositions.isEmpty()) return
        val breakContexts = breakContexts(requestPositions)
        if (!reBreaking) {
            breakPositions.forEachIndexed { index, breakPos ->
                if (breakContexts.none { it.expectedPos == breakPos }) {
                    breakPositions[index] = null
                }
            }
            queuePositions.removeIf { queuePos ->
                breakContexts.none { it.expectedPos == queuePos }
            }
        }
        val request = BreakRequest(
            breakContexts, build, rotation, hotbar, pendingInteractions = pendingInteractionsList,
            onAccept = {
                addBreak(it)
                queuePositions.remove(it)
            },
            onCancel = { removeBreak(it, true) },
            onBreak = {
                breaks++
                removeBreak(it)
            },
            onReBreakStart = { reBreakPos = it },
            onReBreak = { reBreakPos = it },
            onItemDrop = { _ -> itemDrops++ }
        )
        breakConfig.request(request)
    }

    private fun SafeContext.breakContexts(positions: Collection<BlockPos?>) =
        positions
            .filterNotNull()
            .associateWith { TargetState.State(blockState(it).fluidState.blockState) }
            .toBlueprint()
            .simulate(
                player.eyePos,
                interact = interact,
                rotation = rotation,
                inventory = inventory,
                build = build
            )
            .filterIsInstance<BreakResult.Break>()
            .map { it.context }

    private fun addBreak(pos: BlockPos) {
        if (breakConfig.doubleBreak && breakPositions[1] == null) {
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

    enum class Page {
        Build, Rotation, Interaction, Inventory, Hotbar
    }

    enum class ReBreakMode {
        Manual,
        Auto,
        AutoConstant;
    }

    enum class QueueOrder {
        Standard,
        Reversed
    }
}