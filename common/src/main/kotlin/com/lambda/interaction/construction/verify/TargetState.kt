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

import com.lambda.interaction.material.container.ContainerManager.findDisposable
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.matches
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.item.ItemUtils.block
import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.state.property.Property
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

sealed class TargetState(val type: Type) : StateMatcher {

    enum class Type {
        AIR, SOLID, SUPPORT, STATE, BLOCK, STACK
    }

    data object Air : TargetState(Type.AIR) {
        override fun toString() = "Air"

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld, ignoredProperties: Collection<Property<*>>) =
            state.isAir

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            ItemStack.EMPTY

        override fun isEmpty() = true
    }

    data object Solid : TargetState(Type.SOLID) {
        override fun toString() = "Solid"

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld, ignoredProperties: Collection<Property<*>>) =
            state.isSolidBlock(world, pos)

        override fun getStack(world: ClientWorld, pos: BlockPos) =
            findDisposable()?.stacks?.firstOrNull {
                it.item.block in TaskFlowModule.inventory.disposables
            } ?: ItemStack(Items.NETHERRACK)

        override fun isEmpty() = false
    }

    data class Support(val direction: Direction) : TargetState(Type.SUPPORT) {
        override fun toString() = "Support for ${direction.name}"

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld, ignoredProperties: Collection<Property<*>>) =
            world.getBlockState(pos.offset(direction)).isSolidBlock(world, pos.offset(direction))
                    || state.isSolidBlock(world, pos)

        override fun getStack(world: ClientWorld, pos: BlockPos) =
            findDisposable()?.stacks?.firstOrNull {
                it.item.block in TaskFlowModule.inventory.disposables
            } ?: ItemStack(Items.NETHERRACK)

        override fun isEmpty() = false
    }

    data class State(val blockState: BlockState) : TargetState(Type.STATE) {
        override fun toString() = "State of $blockState"

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld, ignoredProperties: Collection<Property<*>>) =
            state.matches(blockState, ignoredProperties)

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            blockState.block.getPickStack(world, pos, blockState)

        override fun isEmpty() = blockState.isEmpty
    }

    data class Block(val block: net.minecraft.block.Block) : TargetState(Type.BLOCK) {
        override fun toString() = "Block of ${block.name.string.capitalize()}"

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld, ignoredProperties: Collection<Property<*>>) =
            state.block == block

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            block.getPickStack(world, pos, block.defaultState)

        override fun isEmpty() = block.defaultState.isEmpty
    }

    data class Stack(val itemStack: ItemStack) : TargetState(Type.STACK) {
        private val startStack: ItemStack = itemStack.copy()
        override fun toString() = "Stack of ${startStack.item.name.string.capitalize()}"

        private val block = itemStack.item.block

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld, ignoredProperties: Collection<Property<*>>) =
            state.block == block

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            itemStack

        override fun isEmpty() = false
    }
}
