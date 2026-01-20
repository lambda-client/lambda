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

package com.lambda.module.modules.render

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.friend.FriendManager.isFriend
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.EntityUtils.EntityGroup
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.extension.prevPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.dist
import com.lambda.util.math.lerp
import net.minecraft.client.network.OtherClientPlayerEntity
import org.joml.component1
import org.joml.component2
import java.awt.Color

object Tracers : Module(
	name = "Tracers",
	description = "Draws lines to entities within the world",
	tag = ModuleTag.RENDER
) {
	private val friendColor by setting("Friend Color", Color.BLUE)
	private val width by setting("Width", 1, 1..50, 1)
	private val entities by setting("Entities", setOf(EntityGroup.Player, EntityGroup.Mob, EntityGroup.Boss), EntityGroup.entries)
	private val playerDistanceGradient by setting("Player Distance Gradient", true) { EntityGroup.Player in entities }
	private val playerDistanceColorFar by setting("Player Far Color", Color.GREEN) { EntityGroup.Player in entities && playerDistanceGradient }
	private val playerDistanceColorClose by setting("Player Close Color", Color.RED) { EntityGroup.Player in entities && playerDistanceGradient }
	private val playerColor by setting("Players", Color.RED) { EntityGroup.Player in entities && !playerDistanceGradient }
	private val mobColor by setting("Mobs", Color(255, 80, 0, 255)) { EntityGroup.Mob in entities }
	private val passiveColor by setting("Passives", Color.BLUE) { EntityGroup.Passive in entities }
	private val projectileColor by setting("Projectiles", Color.LIGHT_GRAY) { EntityGroup.Projectile in entities }
	private val vehicleColor by setting("Vehicles", Color.WHITE) { EntityGroup.Vehicle in entities }
	private val decorationColor by setting("Decorations", Color.PINK) { EntityGroup.Decoration in entities }
	private val bossColor by setting("Bosses", Color(255, 0, 255, 255)) { EntityGroup.Boss in entities }
	private val miscColor by setting("Miscellaneous", Color.magenta) { EntityGroup.Misc in entities }

	val renderer = ImmediateRenderer("Tracers")

	init {
		listen<RenderEvent.Render> {
			renderer.tick()
			renderer.shapes {
				world.entities.forEach { entity ->
					val entityGroup = entity.entityGroup
					if (entityGroup !in entities) return@forEach
					val color = if (entity is OtherClientPlayerEntity) {
						if (entity.isFriend) friendColor
						else {
							if (playerDistanceGradient) {
								val distance = player dist entity
								lerp(distance / 60.0, playerDistanceColorClose, playerDistanceColorFar)
							} else playerColor
						}
					} else when (entityGroup) {
						EntityGroup.Player -> playerColor
						EntityGroup.Mob -> mobColor
						EntityGroup.Passive -> passiveColor
						EntityGroup.Projectile -> projectileColor
						EntityGroup.Vehicle -> vehicleColor
						EntityGroup.Decoration -> decorationColor
						EntityGroup.Boss -> bossColor
						else -> miscColor
					}
					val (toX, toY) = RenderMain.worldToScreenNormalized(lerp(mc.tickDelta, entity.prevPos, entity.pos)) ?: return@forEach
					screenLine(0.5f, 0.5f, toX, toY, color, width * 0.0001f)
				}
			}
			renderer.upload()
			renderer.render()
			renderer.renderScreen()
		}
	}
}