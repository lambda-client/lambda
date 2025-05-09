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

    private val pendingInteractionsList = ConcurrentLinkedQueue<BuildContext>()

    private var breaks = 0
    private var itemDrops = 0

    private val breakingPositions = arrayOfNulls<BlockPos>(2)
    private var reBreakPos: BlockPos? = null

    private var attackedThisTick = false

    init {
        listen<TickEvent.Post> {
            attackedThisTick = false
        }

        //ToDo: run on every tick stage
        listen<TickEvent.Pre> {
            if (breakConfig.reBreak && reBreakMode != ReBreakMode.Auto && reBreakMode != ReBreakMode.AutoConstant) return@listen
            val reBreak = reBreakPos ?: return@listen
            requestBreakManager(reBreak)
        }

        listen<PlayerEvent.Attack.Block> { it.cancel() }
        listen<PlayerEvent.Breaking.Update> { event ->
            event.cancel()
            if (breakingPositions.any { it == event.pos }) return@listen
            val secondary = if (breakConfig.doubleBreak) {
                breakingPositions[1] ?: breakingPositions[0]
            } else null
            requestBreakManager(event.pos, secondary)
            attackedThisTick = true
        }

        listen<TickEvent.Input.Post> {
            if (!attackedThisTick) requestBreakManager(*breakingPositions.toList().toTypedArray())
        }

        onDisable {
            breakingPositions[0] = null
            breakingPositions[1] = null
            reBreakPos = null
            attackedThisTick = false
        }
    }

    private fun SafeContext.requestBreakManager(vararg requestPositions: BlockPos?) {
        if (requestPositions.isEmpty()) return
        val request = BreakRequest(
            breakContexts(requestPositions.filterNotNull()), build, rotation, hotbar, pendingInteractions = pendingInteractionsList,
            onAccept = {
                if (breakConfig.doubleBreak && breakingPositions[1] == null) {
                    breakingPositions[1] = breakingPositions[0]
                }
                breakingPositions[0] = it
                reBreakPos = null
            },
            onCancel = { nullifyBreakPos(it, true) },
            onBreak = {
                breaks++
                nullifyBreakPos(it)
            },
            onReBreakStart = { reBreakPos = it },
            onReBreak = { reBreakPos = it },
            onItemDrop = { _ -> itemDrops++ }
        )
        breakConfig.request(request)
    }

    private fun nullifyBreakPos(pos: BlockPos, includeReBreak: Boolean = false) {
        breakingPositions.forEachIndexed { index, breakPos ->
            if (breakPos == pos) {
                breakingPositions[index] = null
            }
        }
        if (includeReBreak && pos == reBreakPos) {
            reBreakPos = null
            return
        }
    }

    private fun SafeContext.breakContexts(breakPositions: Collection<BlockPos>) =
        breakPositions
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

    enum class Page {
        Build, Rotation, Interaction, Inventory, Hotbar
    }

    enum class ReBreakMode {
        Manual,
        Auto,
        AutoConstant;
    }
}