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

package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.block.ChestBlock
import net.minecraft.entity.mob.ShulkerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class PlaceContainer @Ta5kBuilder constructor(
    val stack: ItemStack,
) : Task<BlockPos>() {
    override fun SafeContext.onStart() {
        val results = BlockPos.iterateOutwards(player.blockPos, 4, 3, 4)
            .map { it.blockPos }
            .flatMap {
                it.blockPos
                    .toStructure(TargetState.Stack(stack))
                    .toBlueprint()
                    .simulate(player.getCameraPosVec(mc.tickDelta.toFloat()))
            }

//        val res = results.sorted()
//        res

        val succeeds = results.filterIsInstance<PlaceResult.Place>().filter {
            canBeOpened(stack, it.blockPos, it.context.result.side)
        }
        val wrongStacks = results.filterIsInstance<BuildResult.WrongStack>().filter {
            val result = (it.context as? PlaceContext)?.result ?: return@filter false
            canBeOpened(stack, it.blockPos, result.side)
        }
        (succeeds + wrongStacks).minOrNull()?.let { result ->
            build {
                result.blockPos
                    .toStructure(TargetState.Stack(stack))
                    .toBlueprint()
            }.onSuccess { _, _ ->
                success(result.blockPos)
            }.start(this@PlaceContainer)
        } ?: {
            failure("No valid placement found")
        }
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
                // TODO: What order?
                //.calculateBoundingBox(direction, 0.0f, 0.5f)
                .calculateBoundingBox(0.0f, direction, 0.5f)
                .offset(blockPos)
                .contract(1.0E-6)
            world.isSpaceEmpty(box)
        }

        else -> false
    }

    companion object {
        @Ta5kBuilder
        fun placeContainer(
            stack: ItemStack,
        ) = PlaceContainer(stack)
    }
}
