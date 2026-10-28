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

import com.lambda.config.Config
import com.lambda.config.ConfigBlock

class ItemPickupSettings(override val c: Config) : ConfigBlock, ItemPickupConfig {
	override val pickupDrops by c.setting("Pickup Drops", true, "Picks up items")
	override val maxLifetime by c.setting("Max Lifetime", 180, 0..300, 1, "Maximum age before a drop triggers pickups", "s") { pickupDrops }
	override val maxDropEntities by c.setting("Max Drop Entities", 50, 1..300, 1, "Maximum drop entities before triggering pickups") { pickupDrops }
	override val maxDistance by c.setting("Max Distance", 20.0, 1.0..100.0, 0.1, "Maximum distance from a drop before triggering pickups", " blocks") { pickupDrops }
}