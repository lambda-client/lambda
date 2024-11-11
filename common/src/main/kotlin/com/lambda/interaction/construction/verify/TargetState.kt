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

package com.lambda.interaction.construction.verify

import com.lambda.interaction.material.ContainerManager.findDisposable
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.item.ItemUtils.block
import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.state.property.Properties
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

sealed class TargetState(val type: Type) : StateMatcher {

    enum class Type {
        AIR, SOLID, SUPPORT, STATE, BLOCK, STACK
    }

    data object Air : TargetState(Type.AIR) {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.isAir

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            ItemStack.EMPTY
    }

    data object Solid : TargetState(Type.SOLID) {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.isSolidBlock(world, pos)

        override fun getStack(world: ClientWorld, pos: BlockPos) =
            findDisposable()?.stacks?.firstOrNull {
                it.item.block in TaskFlow.disposables
            } ?: ItemStack(Items.NETHERRACK)
    }

    data class Support(val direction: Direction) : TargetState(Type.SUPPORT) {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            pos.offset(direction).blockState(world).isSolidBlock(world, pos.offset(direction))
                    || state.isSolidBlock(world, pos)

        override fun getStack(world: ClientWorld, pos: BlockPos) =
            findDisposable()?.stacks?.firstOrNull {
                it.item.block in TaskFlow.disposables
            } ?: ItemStack(Items.NETHERRACK)
    }

    data class State(val blockState: BlockState) : TargetState(Type.STATE) {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.block == blockState.block && state.properties.all {
                it in TaskFlow.defaultIgnoreTags || state[it] == blockState[it]
            }
        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            blockState.block.getPickStack(world, pos, blockState)
    }

    data class Block(val block: net.minecraft.block.Block) : TargetState(Type.BLOCK) {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.block == block

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            block.getPickStack(world, pos, block.defaultState)
    }

    data class Stack(val itemStack: ItemStack) : TargetState(Type.STACK) {
        private val block = itemStack.item.block

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.block == block

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            itemStack
    }
}
