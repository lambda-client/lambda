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
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.RotationSettings
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
import com.lambda.interaction.request.placing.PlaceRequest
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.KeyCode
import com.lambda.util.InputUtils.isKeyPressed
import com.lambda.util.NamedEnum
import com.lambda.util.math.distSq
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

object Scaffold : Module(
    name = "Scaffold",
    description = "Places blocks under the player",
    tag = ModuleTag.PLAYER,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Build("Build"),
        Rotation("Rotation"),
        Hotbar("Hotbar"),
        Inventory("Inventory")
    }

    private val bridgeRange by setting("Bridge Range", 5, 0..5, 1, "The range at which blocks can be placed to help build support for the player", unit = " blocks").group(Group.General)
    private val onlyBelow by setting("Only Below", true, "Restricts bridging to only below the player to avoid place spam if it's impossible to reach the supporting position") { bridgeRange > 0 }.group(Group.General)
    private val descend by setting("Descend", KeyCode.Unbound, "Lower the place position by one to allow the player to lower y level").group(Group.General)
    private val descendAmount by setting("Descend Amount", 1, 1..5, 1, "The amount to lower the place position by when descending", unit = " blocks") { descend != KeyCode.Unbound }.group(Group.General)
    override val buildConfig = BuildSettings(this, Group.Build).apply {
        editTyped(::pathing, ::stayInRange, ::collectDrops) {
            defaultValue(false)
            hide()
        }
    }
    override val rotationConfig = RotationSettings(this, Group.Rotation)
    override val hotbarConfig = HotbarSettings(this, Group.Hotbar)
    override val inventoryConfig = InventorySettings(this, Group.Inventory).apply {
        ::disposables.edit {
            name("Blocks")
            description("Blocks to use as scaffolding")
            groups(Group.General)
        }
        editTyped(::accessShulkerBoxes, ::accessEnderChest, ::accessChests, ::accessStashes) {
            defaultValue(false)
            hide()
        }
    }

    private val pendingActions = ConcurrentLinkedQueue<BuildContext>()

    init {
        listen<TickEvent.Pre> {
            val playerSupport = player.blockPos.down()
            val alreadySupported = blockState(playerSupport).hasSolidTopSurface(world, playerSupport, player)
            if (alreadySupported) return@listen
            val offset = if (isKeyPressed(descend.code)) descendAmount else 0
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
            if (descend.code != mc.options.sneakKey.boundKey.code) return@listen
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
