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

package com.lambda.interaction.construction.verify

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import net.minecraft.block.BlockState
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Property
import net.minecraft.util.math.BlockPos

interface StateMatcher {
    context(safeContext: SafeContext)
    fun matches(
        state: BlockState,
        pos: BlockPos,
        ignoredProperties: Collection<Property<*>> = emptySet()
    ): Boolean

    context(automatedSafeContext: AutomatedSafeContext)
    fun getStack(pos: BlockPos, state: BlockState): ItemStack
    context(automatedSafeContext: AutomatedSafeContext)
    fun getState(pos: BlockPos, state: BlockState): BlockState
    fun isEmpty(): Boolean
}
