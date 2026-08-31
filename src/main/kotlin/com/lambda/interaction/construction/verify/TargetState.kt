/*
 * Copyright 2026 Lambda
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

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.handler.handlers.findDisposable
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.emptyState
import com.lambda.util.BlockUtils.isEmpty
import com.lambda.util.BlockUtils.matches
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.item.ItemUtils.block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.state.property.Property
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

sealed class TargetState : StateMatcher {
    data object Empty : TargetState() {
        override fun toString() = "Empty"

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = state.isEmpty

        context(_: AutomatedSafeContext)
        override fun getStack(pos: BlockPos): ItemStack = ItemStack.EMPTY

        context(automatedSafeContext: AutomatedSafeContext)
        override fun getState(pos: BlockPos) = automatedSafeContext.blockState(pos).emptyState

        override fun isEmpty() = true
    }

    data object Air : TargetState() {
        override fun toString() = "Air"

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = state.isAir

        context(_: AutomatedSafeContext)
        override fun getStack(pos: BlockPos): ItemStack = ItemStack.EMPTY

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = Blocks.AIR.defaultState

        override fun isEmpty() = true
    }

    data class Solid(val replace: Collection<net.minecraft.block.Block>) : TargetState() {
        override fun toString() = "Solid"

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = with(safeContext) { state.isSolidBlock(world, pos) && state.block !in replace }

        context(automatedSafeContext: AutomatedSafeContext)
        override fun getStack(pos: BlockPos) =
            with(automatedSafeContext) {
                findDisposable()?.stacks?.firstOrNull {
                    it.item in inventoryConfig.disposables && it.item.block !in replace
                } ?: ItemStack(Items.NETHERRACK)
            }

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = getStack(pos).item.block.defaultState

        override fun isEmpty() = false
    }

    data class Support(val direction: Direction) : TargetState() {
        override fun toString() = "Support for ${direction.name}"

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = with(safeContext) {
            world.getBlockState(pos.offset(direction)).isSolidBlock(world, pos.offset(direction))
                    || state.isSolidBlock(world, pos)
        }

        context(automatedSafeContext: AutomatedSafeContext)
        override fun getStack(pos: BlockPos) =
            with(automatedSafeContext) {
                findDisposable()?.stacks?.firstOrNull {
                    it.item in inventoryConfig.disposables
                } ?: ItemStack(Items.NETHERRACK)
            }

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = getStack(pos).item.block.defaultState

        override fun isEmpty() = false
    }

    data class State(val blockState: BlockState) : TargetState() {
        override fun toString() = "State of $blockState"

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = state.matches(blockState, ignoredProperties)

        context(automatedSafeContext: AutomatedSafeContext)
        override fun getStack(pos: BlockPos): ItemStack =
			blockState.block.getPickStack(automatedSafeContext.world, pos, blockState, true)

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = blockState

        override fun isEmpty() = blockState.isEmpty
    }

    data class Block(val block: net.minecraft.block.Block) : TargetState() {
        override fun toString() = "Block of ${block.name.string.capitalize()}"

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = state.block == block

        context(automatedSafeContext: AutomatedSafeContext)
        override fun getStack(pos: BlockPos): ItemStack =
            block.getPickStack(automatedSafeContext.world, pos, block.defaultState, true)

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = block.defaultState

        override fun isEmpty() = block.defaultState.isEmpty
    }

    data class Stack(val itemStack: ItemStack) : TargetState() {
        override fun toString() = "Stack of ${itemStack.item.name.string.capitalize()}"

        private val block = itemStack.item.block

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = state.block == block

        context(_: AutomatedSafeContext)
        override fun getStack(pos: BlockPos): ItemStack = itemStack

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = block.defaultState

        override fun isEmpty() = block.defaultState.isEmpty
    }

    data class SpecificStack(val itemStack: ItemStack) : TargetState() {
        override fun toString() = "Specific stack of ${itemStack.item.name.string.capitalize()}"

        private val block = itemStack.item.block

        context(safeContext: SafeContext)
        override fun matches(
            state: BlockState,
            pos: BlockPos,
            ignoredProperties: Collection<Property<*>>
        ) = state.block == block

        context(_: AutomatedSafeContext)
        override fun getStack(pos: BlockPos) = itemStack

        context(_: AutomatedSafeContext)
        override fun getState(pos: BlockPos): BlockState = block.defaultState

        override fun isEmpty() = block.defaultState.isEmpty
    }
}
