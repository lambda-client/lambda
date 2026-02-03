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

import com.lambda.config.AutomationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.visibilty.VisibilityChecker
import com.lambda.interaction.managers.rotating.visibilty.VisibilityChecker.findRotation
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.Communication.debug
import com.lambda.util.Communication.info
import com.lambda.util.EntityUtils
import com.lambda.util.Timer
import com.lambda.util.math.dist
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.milliseconds

class AutoMount : Module(
	name = "AutoMount",
	description = "Automatically mounts entities",
	tag = ModuleTag.MOVEMENT
) {
	var autoRemount by setting("Auto Remount", false, description = "Automatically remounts if you get off")
	var autoMountEntities by setting("Auto Mount Entities", true, description = "Automatically mounts nearby entities in range")
	var autoMountEntityList by setting("Auto Mount Entity List",
		mutableListOf(),
		Registries.ENTITY_TYPE.toList()
	) { autoMountEntities }

	var interval by setting("Interval", 50, 1..200, 1, unit = "ms", description = "Interact interval")
	var range by setting("Range", 4.0, 1.0..20.0, 0.1, description = "Mount range")

	override var automationConfig = AutomationConfig(
		name = "AutoMount"
	)

	val intervalTimer = Timer()
	var lastEntity: Entity? = null

	init {
		onEnable {
			intervalTimer.reset()
			lastEntity = null
		}

		listen<TickEvent.Pre> {
			if (!intervalTimer.timePassed(interval.milliseconds)) {
				return@listen
			}
			if (autoMountEntities && player.vehicle == null) {
				runSafeAutomated {
					val entity = fastEntitySearch<Entity>(10.0) {
						autoMountEntityList.contains(it.type) && canRide(it) && it.findRotation(range, player.eyePos) != null
					}.sortedBy { it.squaredDistanceTo(player.pos) }
					entity.firstOrNull()?.let {
						intervalTimer.reset()
						interactEntity(it)
						debug("Mounting ${it.name}")
					}
				}
			}
			if (autoRemount) {
				if (player.vehicle != null) {
					lastEntity = player.vehicle
				} else {
					lastEntity?.let {
						if (it.isRemoved || it.distanceTo(player) > range) {
							lastEntity = null
						} else {
							if (canRide(it)) {
								intervalTimer.reset()
								interactEntity(it)
							}
						}
					}
				}
			}
		}
	}

	private fun SafeContext.interactEntity(entity: Entity) {
		mc.networkHandler?.sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, false, Hand.MAIN_HAND, Vec3d(0.5, 0.5, 0.5)))
		mc.networkHandler?.sendPacket(PlayerInteractEntityC2SPacket.interact(entity, false, Hand.MAIN_HAND))
	}

	private fun SafeContext.canRide(entity: Entity): Boolean {
		return entity.canAddPassenger(player)
	}
}