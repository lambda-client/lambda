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

@file:Suppress("unused")

package com.lambda.config.blocks

import com.lambda.context.SafeContext
import com.lambda.util.math.dist
import net.minecraft.entity.ItemEntity

interface ItemPickupConfig {
	val pickupDrops: Boolean
	val maxLifetime: Int
	val maxDropEntities: Int
	val maxDistance: Double

	context(safeContext: SafeContext)
	fun shouldPickup(items: Collection<ItemEntity>) =
		pickupDrops && (items.size >= maxDropEntities ||
				items.any {
					it.itemAge / 20 >= maxLifetime ||
							it dist safeContext.player >= maxDistance
				})

	companion object {
		val IGNORE_DROPS =
			object : ItemPickupConfig {
				override val pickupDrops = false
				override val maxLifetime = Int.MAX_VALUE
				override val maxDropEntities = Int.MAX_VALUE
				override val maxDistance = Double.MAX_VALUE
			}

		val DEFAULT =
			object : ItemPickupConfig {
				override val pickupDrops = true
				override val maxLifetime = 180
				override val maxDropEntities = 50
				override val maxDistance = 20.0
			}
	}
}