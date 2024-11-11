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
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.Task.Companion.failTask
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.util.Communication.info
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.util.math.BlockPos

object EnderChestContainer : MaterialContainer(Rank.ENDER_CHEST) {
    override var stacks = emptyList<ItemStack>()
    override val name = "EnderChest"
    private var placePos: BlockPos? = null

//    override fun prepare(): Task<*> {
//        TODO("Not yet implemented")
//    }
//        findBlock(Blocks.ENDER_CHEST).onSuccess { pos ->
//            moveIntoEntityRange(pos)
//            placePos = pos
//        }.onFailure {
//            acquireStack(Items.ENDER_CHEST.select()).onSuccess { _, stack ->
//                placeContainer(stack).onSuccess { _, pos ->
//                    placePos = pos
//                }
//            }
//        }

    override fun withdraw(selection: StackSelection): Task<*> {
        info("Not yet implemented")
        return emptyTask()
    }

    override fun deposit(selection: StackSelection): Task<*> {
        info("Not yet implemented")
        return emptyTask()
    }
}
