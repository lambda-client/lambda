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

package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.util.math.VecUtils.blockPos
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Box

data class StashContainer(
    val chests: Set<ChestContainer>,
    val pos: Box,
) : MaterialContainer(Rank.STASH) {
    override var stacks: List<ItemStack>
        get() = chests.flatMap { it.stacks }
        set(_) {}
    override val name = "Stash at ${pos.center.blockPos.toShortString()}"

    override fun withdraw(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }

    override fun deposit(selection: StackSelection): Task<*> {
        TODO("Not yet implemented")
    }

    override fun available(selection: StackSelection): Int =
        chests.sumOf {
            it.available(selection)
        }
}
