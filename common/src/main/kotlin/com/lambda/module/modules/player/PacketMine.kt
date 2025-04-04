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
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
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
import java.util.concurrent.ConcurrentLinkedQueue

object PacketMine : Module(
    "Packet Mine",
    "automatically breaks blocks, and does it faster",
    setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.Build)

    private val build = BuildSettings(this) { page == Page.Build }
    private val rotation = RotationSettings(this) { page == Page.Rotation }
    private val interact = InteractionSettings(this, InteractionMask.Block) { page == Page.Interaction }
    private val inventory = InventorySettings(this) { page == Page.Inventory }
    private val hotbar = HotbarSettings(this) { page == Page.Hotbar }

    private val pendingInteractionsList = ConcurrentLinkedQueue<BuildContext>()

    private var breaks = 0
    private var itemDrops = 0

    init {
        listen<PlayerEvent.Attack.Block> { event ->
            event.cancel()

            val blockState = blockState(event.pos)
            val buildResult = event.pos
                .toStructure(TargetState.State(blockState.fluidState.blockState))
                .toBlueprint()
                .simulate(
                    player.eyePos,
                    interact = interact,
                    rotation = rotation,
                    inventory = inventory,
                    build = build
                )
                .minOrNull() ?: return@listen

            if (buildResult !is BreakResult.Break) return@listen
            val request = BreakRequest(
                listOf(buildResult.context), build, rotation, interact, inventory, hotbar,
                pendingInteractionsList = pendingInteractionsList,
                onBreak = { breaks++ }
            ) { _ -> itemDrops++ }
            build.breakSettings.request(request)
        }
    }

    enum class Page {
        Build, Rotation, Interaction, Inventory, Hotbar
    }
}