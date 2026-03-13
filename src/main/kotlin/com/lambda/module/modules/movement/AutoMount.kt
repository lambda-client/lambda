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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.managers.rotating.visibilty.VisibilityChecker.findRotation
import com.lambda.interaction.managers.rotating.visibilty.lookAtEntity
import com.lambda.interaction.managers.rotating.visibilty.lookAtHit
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.Communication.debug
import com.lambda.util.Communication.info
import com.lambda.util.Timer
import com.lambda.util.world.fastEntitySearch
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.milliseconds

class AutoMount : Module(
	name = "AutoMount",
	description = "Automatically mounts entities",
	tag = ModuleTag.MOVEMENT
) {
	var autoRemount by setting("Auto Remount", false, description = "Automatically remounts if you get off")
	var autoMountEntities by setting("Auto Mount Entities", true, description = "Automatically mounts nearby entities in range")
	var autoMountEntityList by setting("Auto Mount Entity List", mutableListOf(), Registries.ENTITY_TYPE.toList()) { autoMountEntities }

	var interval by setting("Interval", 50, 1..200, 1, unit = "ms", description = "Interact interval")
	var range by setting("Range", 4.0, 1.0..20.0, 0.1, description = "Mount range")
	var rotate by setting("Rotate", false, description = "Rotate to the entity when mounting")
	var packetUnCrouch by setting("Packet Crouch", true, description = "Send the mount packet with the crouch flag false")
	var debug by setting("Debug", false, description = "Print debug messages")

	val intervalTimer = Timer()
	var lastEntity: Entity? = null

	init {
		setDefaultAutomationConfig("AutoMount") {
			applyEdits {
				hideAllGroupsExcept(rotationConfig, interactConfig)
			}
		}

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
						interactEntity(it)
						if (debug) info("Mounting ${it.name}")
					}
				}
			}
			if (!autoRemount) {
				return@listen
			}
			if (player.vehicle?.isRemoved == false) {
				lastEntity = player.vehicle
			}
			lastEntity?.let {
				if (it.isRemoved || it.distanceTo(player) > range) {
					lastEntity = null
					return@let
				}
				if (canRide(it)) {
					runSafeAutomated {
						interactEntity(it)
					}
				}
			}
		}
	}

	private fun AutomatedSafeContext.interactEntity(entity: Entity) {
		intervalTimer.reset()
		if (rotate) {
			lookAtEntity(entity)?.let {
				val done = rotationRequest {
					lookAtHit(it.hit)
				}.submit().done
				if (!done) {
					if (debug) debug("Rotation request failed to submit")
					return
				}
				val hitResult = it.hit as? EntityHitResult ?: return@let
				// Send packet to manually set sneak state
				val vec3d = hitResult.getPos().subtract(entity.x, entity.y, entity.z)
				val crouch = if (packetUnCrouch) false else player.isSneaking
				connection.sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, crouch, Hand.MAIN_HAND, vec3d))
				return
			} ?: run {
				if (debug) debug("Not rotation found to mount entity")
				return
			}
			if (debug) debug("Invalid entity rotation")
		} else {
			connection.sendPacket(PlayerInteractEntityC2SPacket.interactAt(entity, false, Hand.MAIN_HAND, Vec3d(0.5, 0.5, 0.5)))
			connection.sendPacket(PlayerInteractEntityC2SPacket.interact(entity, false, Hand.MAIN_HAND))
		}
	}

	private fun SafeContext.canRide(entity: Entity) = entity.canAddPassenger(player)
}