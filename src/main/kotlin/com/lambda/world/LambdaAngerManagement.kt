/*
 * Copyright 2025 Lambda
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

package com.lambda.world

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.minecraft.entity.Entity
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.util.math.Vec3d
import java.util.*

class LambdaAngerManagement: Loadable {

	init {
		listen<PacketEvent.Receive.Pre> { event ->
			if (event.packet is PlaySoundS2CPacket) {
				val uuid = findClosestEntity(event.packet) ?: return@listen // this was not a sound that entities emit when angry
				registerAngryEntity(uuid)
			}
		}
		listen<ConnectionEvent.Disconnect> {
			angryEntities.clear()
		}
		// todo: task every 5 minutes or something to clean up old angry entities
	}

	companion object {
		const val ANGER_DURATION_MS = 60000L
		private val angryEntities = mutableMapOf<UUID, Long>()

		@JvmStatic
		private fun matchLocations(vec3d: Vec3d, radius: Double = 3.0): UUID? {
			val maxDistSq = radius * radius
			return mc.world?.entities
				?.asSequence()
				?.map { it to it.distanceSqTo(vec3d) }
				?.filter { it.second <= maxDistSq }
				?.minByOrNull { it.second }
				?.first?.uuid
		}

		fun Entity.distanceSqTo(vec3d: Vec3d): Double {
			val dx = this.x - vec3d.x
			val dy = this.y - vec3d.y
			val dz = this.z - vec3d.z
			return dx * dx + dy * dy + dz * dz
		}

		@JvmStatic
		private fun findClosestEntity(packet: PlaySoundS2CPacket): UUID? {
			val vec3 = Vec3d(packet.x, packet.y, packet.z)
			val soundId = packet.sound.value().id.path
			return when {
				soundId.contains(".growl") || // (wolf) randomly while angry
				soundId.contains(".stare") || // when it mad when u look at it
				soundId.contains(".scream") || // randomly when hostile
				soundId.contains(".angry") // (zombie pigmen) randomly when angry and when getting angry
				-> matchLocations(vec3)
				else -> null
			}
		}
		/*
		todo: spiders

		things that give them away when they are aggro:
			- head directly towards the player
			- the "jump attack", can be easily detected as normally they don't have
			that much positive y velocity when climbing
			- spiders could be tracked separately and the "aggro score" could be
			decreased if they are not moving as their normal pathing behavior is to
			path randomly around ~5-10 blocks, no jumps, and then stop for a while
		 */

		@JvmStatic
		fun registerAngryEntity(entityUuid: UUID) {
			angryEntities[entityUuid] = System.currentTimeMillis()
		}

		@JvmStatic
		fun isEntityAngry(entityUuid: UUID): Boolean {
			val timestamp = angryEntities[entityUuid] ?: return false
			val isAngry = System.currentTimeMillis() - timestamp < ANGER_DURATION_MS
			if (!isAngry) {
				angryEntities.remove(entityUuid)
			}
			return isAngry
		}


		@JvmStatic
		suspend fun cleanupUnloadedEntities() = coroutineScope {
			async {
				angryEntities.keys.removeIf { uuid ->
					!(mc.world?.entities?.any { it.uuid == uuid } ?: false)
				}
			}
		}

		@JvmStatic
		fun removeAngryEntity(entityUuid: UUID) {
			angryEntities.remove(entityUuid)
		}
	}
}