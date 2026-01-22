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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.threading.runSafeConcurrent
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.distSq
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.minecraft.entity.mob.PiglinActivity
import net.minecraft.entity.mob.PiglinEntity
import net.minecraft.entity.mob.ZombifiedPiglinEntity
import net.minecraft.entity.passive.BeeEntity
import net.minecraft.entity.passive.PolarBearEntity
import net.minecraft.entity.passive.WolfEntity
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket
import net.minecraft.util.math.Vec3d
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LambdaAngerManagement: Loadable {

	/*
	for future reference, these are the entities that use this
	enderman
	zombified piglin
	bee
	iron golem
	wolf
	polar bear

    private static final UniformIntProvider ANGER_TIME_RANGE = TimeHelper.betweenSeconds(20, 39);

	though because zombified piglins tell their friends with a delay and the anger time is counted
	for each entity separately they will not stop attacking, this seems to be a bug in the game

	 */

	private var i = 0

	init {
		listen<PacketEvent.Receive.Pre> { event ->
			if (event.packet is PlaySoundS2CPacket) {
				val uuid = findClosestEntity(event.packet) ?: return@listen // this was not a sound that entities emit when angry
				registerAngryEntity(uuid)
			}
		}
		listen<TickEvent.Pre> {
			if (i++ % 10 != 0) return@listen
			i = 0
			runSafeConcurrent {
				mc.world?.entities?.forEach { entity ->
					when (entity) {
						is BeeEntity -> {
							if (entity.hasAngerTime()) {
								registerAngryEntity(entity.uuid)
							}
						}
						is WolfEntity -> {
							if (entity.hasAngerTime()) {
								registerAngryEntity(entity.uuid)
							}
						}
						is ZombifiedPiglinEntity -> {
							if (entity.isAttacking) {
								registerAngryEntity(entity.uuid)
							}
						}
						is PiglinEntity -> {
							if (entity.activity == PiglinActivity.ATTACKING_WITH_MELEE_WEAPON ||
								entity.activity == PiglinActivity.CROSSBOW_CHARGE
							) {
								registerAngryEntity(entity.uuid)
							}
						}
						is PolarBearEntity -> {
							if (entity.getWarningAnimationProgress(mc.tickDelta) > 0f) {
								registerAngryEntity(entity.uuid)
							}
						}
					}
				} ?: return@runSafeConcurrent
				cleanupUnloadedEntities()
			}
		}
		listen<ConnectionEvent.Disconnect> {
			angryEntities.clear()
		}
		// todo: task every 5 minutes or something to clean up old angry entities
	}

	companion object {
		const val ANGER_DURATION_MS = 30000L
		private val angryEntities = ConcurrentHashMap<UUID, Long>()

		@JvmStatic
		private fun matchLocations(vec3d: Vec3d, radius: Double = 3.0): UUID? {
			val maxDistSq = radius * radius
			return mc.world?.entities
				?.asSequence()
				?.map { it to it.distSq(vec3d) }
				?.filter { it.second <= maxDistSq }
				?.minByOrNull { it.second }
				?.first?.uuid
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