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

package com.lambda.interaction.inventory.container.containers

import com.lambda.Lambda.mc
import com.lambda.interaction.inventory.container.Container
import com.lambda.interaction.inventory.container.ContainerRank
import com.lambda.util.player.SlotUtils.armorSlots
import com.lambda.util.player.SlotUtils.armorStacks
import com.lambda.util.text.buildText
import com.lambda.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot

object ArmorContainer : Container(ContainerRank.Armor) {
	override val slots: List<Slot>
		get() = mc.player?.armorSlots ?: emptyList()
	override var stacks: List<ItemStack>
		get() = mc.player?.armorStacks ?: emptyList()
		set(_) {}

	override val description = buildText { literal("Armor") }

	override val isAccessed get() = mc.player?.currentScreenHandler?.syncId == 0
}