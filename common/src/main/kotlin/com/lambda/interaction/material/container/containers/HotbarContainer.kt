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

package com.lambda.interaction.material.container.containers

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.interaction.material.ContainerTask
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.transfer.SlotTransfer.Companion.deposit
import com.lambda.interaction.material.container.MaterialContainer
import com.lambda.util.player.SlotUtils.hotbar
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack

object HotbarContainer : MaterialContainer(Rank.HOTBAR) {
    override var stacks: List<ItemStack>
        get() = mc.player?.hotbar ?: emptyList()
        set(_) {}

    override val description = buildText { literal("Hotbar") }

    class HotbarDeposit @Ta5kBuilder constructor(val selection: StackSelection) : ContainerTask() {
        override val name: String get() = "Depositing $selection into hotbar"

        override fun SafeContext.onStart() {
            val handler = player.currentScreenHandler
            deposit(handler, selection).finally {
                delayedFinish()
            }.execute(this@HotbarDeposit)
        }
    }

    override fun deposit(selection: StackSelection) = HotbarDeposit(selection)
}
