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

package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.Timer
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.registry.Registries
import kotlin.time.Duration.Companion.milliseconds

class AutoMount : Module(
	name = "AutoMount",
	description = "Automatically mounts entities",
	tag = ModuleTag.MOVEMENT
) {
	var autoRemount by setting("Auto Remount", false, description = "Automatically remounts if you get off")
	var autoMountEntities by setting("Auto Mount Entities", true, description = "Automatically mounts nearby entities in range")
	var autoMountEntityList by setting("Auto Mount Entity List", mutableListOf<EntityType<*>>(), mutableListOf(Registries.ENTITY_TYPE.toList())) { autoMountEntities }

	var interval by setting("Interval", 50, 1..200, 1, unit = "ms", description = "Interact interval")
	var range by setting("Range", 5.0, 1.0..10.0, 0.1, description = "Mount range")

	val intervalTimer = Timer()
	var lastEntity: Entity? = null

	init {
		listen<TickEvent.Pre> {
			if (!intervalTimer.timePassed(interval.milliseconds)) {
				return@listen
			}
			if (autoMountEntities && !player.isRiding) {
				val entity = fastEntitySearch<Entity>(range) {
					autoMountEntityList.contains(it.type) && canRide(it) && it.distanceTo(player) <= range
				}
				entity.firstOrNull()?.let {
					intervalTimer.reset()
					player.startRiding(it)
				}
			}
			if (autoRemount) {
				if (player.isRiding) {
					lastEntity = player.vehicle
				} else {
					lastEntity?.let {
						if (it.isRemoved || it.distanceTo(player) > range) {
							lastEntity = null
						} else {
							if (canRide(it)) {
								intervalTimer.reset()
								player.startRiding(it)
							}
						}
					}
				}
			}
		}
	}

	private fun SafeContext.canRide(entity: Entity): Boolean {
		return entity.canAddPassenger(player)
	}

	private fun Entity.canAddPassenger(other: Entity): Boolean {
		return this.canAddPassenger(other)
	}
}