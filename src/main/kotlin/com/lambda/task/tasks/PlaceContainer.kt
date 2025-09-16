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

package com.lambda.task.tasks

import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.ManagerUtils
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.interaction.request.rotating.RotationConfig
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.block.ChestBlock
import net.minecraft.entity.mob.ShulkerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class PlaceContainer @Ta5kBuilder constructor(
    val stack: ItemStack,
    val build: BuildConfig = TaskFlowModule.build,
    val rotation: RotationConfig = TaskFlowModule.rotation,
    val interact: InteractionConfig = TaskFlowModule.interaction,
    val inventory: InventoryConfig = TaskFlowModule.inventory
) : Task<BlockPos>() {
    private val startStack: ItemStack = stack.copy()
    override val name: String get() = "Placing container ${startStack.name.string}"

    override fun SafeContext.onStart() {
        val results = BlockPos.iterateOutwards(player.blockPos, 4, 3, 4)
            .map { it.blockPos }
            .asSequence()
            .filter { !ManagerUtils.isPosBlocked(it) }
            .flatMap {
                it.toStructure(TargetState.Stack(startStack))
                    .toBlueprint()
                    .simulate(player.eyePos)
            }

        val options = results.filterIsInstance<PlaceResult.Place>().filter {
            canBeOpened(startStack, it.blockPos, it.context.result.side)
        } + results.filterIsInstance<BuildResult.WrongItemSelection>().filter {
            canBeOpened(startStack, it.blockPos, it.context.result.side)
        }
        val containerPosition = options.filter {
            // ToDo: Check based on if we can move the player close enough rather than y level once the custom pathfinder is merged
            it.blockPos.y == player.blockPos.y
        }.minOrNull()?.blockPos ?: run {
            failure("Couldn't find a valid container placement position for ${startStack.name.string}")
            return@onStart
        }

        containerPosition
            .toStructure(TargetState.Stack(startStack))
            .toBlueprint()
            .build(
                finishOnDone = true,
                collectDrops = false,
                build = build,
                rotation = rotation,
                interact = interact,
                inventory = inventory
            ).finally {
                success(containerPosition)
            }.execute(this@PlaceContainer)
    }

    private fun SafeContext.canBeOpened(
        itemStack: ItemStack,
        blockPos: BlockPos,
        direction: Direction,
    ) = when (itemStack.item) {
        Items.ENDER_CHEST -> {
            !ChestBlock.isChestBlocked(world, blockPos)
        }
        in shulkerBoxes -> {
            val box = ShulkerEntity
                .calculateBoundingBox(0.5f, direction, 0.0f, blockPos.toBottomCenterPos())
                .offset(blockPos)
                .contract(1.0E-6)

            world.isSpaceEmpty(box)
        }
        else -> false
    }
}
