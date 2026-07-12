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

package com.lambda.interaction.inventory.container.containers.external

import com.lambda.interaction.inventory.container.Container
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos

class PlacedShulkerBoxContainer(
	val blockPos: BlockPos,
	val
) : Container(Rank.PlacedShulkerBox) {
	override val slots: List<Slot>
		get() = TODO("Not yet implemented")
	override var stacks: List<ItemStack>
		get() = TODO("Not yet implemented")
		set(value) {}
	override val description: Text
		get() = TODO("Not yet implemented")
}