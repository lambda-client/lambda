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

import com.lambda.config.groups.BuildConfig
import com.lambda.config.settings.complex.Bind
import com.lambda.config.AutomationConfig.Companion.automationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.result.results.PlaceResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.Request.Companion.submit
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.InputUtils.isKeyPressed
import com.lambda.util.KeyCode
import com.lambda.util.math.distSq
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

object Scaffold : Module(
    name = "Scaffold",
    description = "Places blocks under the player",
    tag = ModuleTag.PLAYER,
) {
    private val bridgeRange by setting("Bridge Range", 5, 0..5, 1, "The range at which blocks can be placed to help build support for the player", unit = " blocks")
    private val onlyBelow by setting("Only Below", true, "Restricts bridging to only below the player to avoid place spam if it's impossible to reach the supporting position") { bridgeRange > 0 }
    private val descend by setting("Descend", KeyCode.Unbound, "Lower the place position by one to allow the player to lower y level")
    private val descendAmount by setting("Descend Amount", 1, 1..5, 1, "The amount to lower the place position by when descending", unit = " blocks") { descend != Bind.EMPTY }

    private val pendingActions = ConcurrentLinkedQueue<BuildContext>()

    override val buildConfig = object : BuildConfig by super.buildConfig {
        override val pathing = false
        override val stayInRange = false
        override val collectDrops = false
    }
    override val inventoryConfig = object : InventoryConfig by super.inventoryConfig {
        override val accessShulkerBoxes = false
        override val accessEnderChest = false
        override val accessChests = false
        override val accessStashes = false
    }

    init {
        defaultAutomationConfig = automationConfig {
            buildConfig.apply {
                editTyped(::pathing, ::stayInRange, ::collectDrops) {
                    defaultValue(false)
                    hide()
                }
            }
            inventoryConfig.apply {
                editTyped(::accessShulkerBoxes, ::accessEnderChest, ::accessChests, ::accessStashes) {
                    defaultValue(false)
                    hide()
                }
            }
        }

        listen<TickEvent.Pre> {
            val playerSupport = player.blockPos.down()
            val alreadySupported = blockState(playerSupport).hasSolidTopSurface(world, playerSupport, player)
            if (alreadySupported) return@listen
            val offset = if (isKeyPressed(descend.key)) descendAmount else 0
            val beneath = playerSupport.down(offset)
            runSafeAutomated {
                scaffoldPositions(beneath)
                    .associateWith { TargetState.Solid(emptySet()) }
                    .toBlueprint()
                    .simulate()
                    .filterIsInstance<PlaceResult.Place>()
                    .minByOrNull { it.pos distSq beneath }
                    ?.let { result ->
                        submit(PlaceRequest(
                            setOf(result.context),
                            pendingActions,
                            this@Scaffold
                        ))
                    }
            }
        }

        listen<MovementEvent.Sneak> {
            if (descend.key != mc.options.sneakKey.boundKey.code) return@listen
            it.sneak = false
        }
    }

    private fun SafeContext.scaffoldPositions(beneath: BlockPos): List<BlockPos> {
        if (!blockState(beneath).isReplaceable) return emptyList()
        if (placeConfig.airPlace.isEnabled) return listOf(beneath)

        return BlockPos.iterateOutwards(beneath, bridgeRange, bridgeRange, bridgeRange)
            .asSequence()
            .filter { !onlyBelow || it.y <= beneath.y }
            .filter { blockState(it).isReplaceable }
            .map { it.blockPos }
            .toList()
    }
}
