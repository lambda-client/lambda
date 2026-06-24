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

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.context.SafeContext
import com.lambda.interaction.handlers.FriendHandler.isFriend
import com.lambda.util.EntityUtils.EntityGroup
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.entityColor
import com.lambda.util.math.dist
import com.lambda.util.math.lerp
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import java.awt.Color

class EntityColorSettings(override val c: Config) : ConfigBlock, EntityColorsConfig {
	override val useNaturalColors by c.setting("Use Natural Colors", false, "Uses an average color from the entities texture")
	override val playerColor by c.setting("Player Color", Color(255, 50, 50)) { !useNaturalColors }
	override val playerDistanceGradient by c.setting("Player Distance Gradient", true)
	override val playerDistanceColorFar by c.setting("Player Far Color", Color.GREEN) { playerDistanceGradient }
	override val playerDistanceColorClose by c.setting("Player Close Color", Color.RED) { playerDistanceGradient }
	override val separateFriendColor by c.setting("Separate Friend Color", true) { useNaturalColors }
	override val friendColor by c.setting("Friend Color", Color(0, 255, 255)) { !useNaturalColors || separateFriendColor }
	override val mobColor by c.setting("Mob Color", Color(255, 70, 50)) { !useNaturalColors }
	override val passiveColor by c.setting("Passive Color", Color(0, 255, 0)) { !useNaturalColors }
	override val vehicleColor by c.setting("Vehicle Color", Color(200, 150, 100)) { !useNaturalColors }
	override val projectileColor by c.setting("Projectile Color", Color(200, 200, 200)) { !useNaturalColors }
	override val bossColor by c.setting("Boss Color", Color(255, 100, 0)) { !useNaturalColors }
	override val decorationColor by c.setting("Decoration Color", Color(100, 100, 255)) { !useNaturalColors }
	override val blockColor by c.setting("Block Color", Color(200, 200, 200)) { !useNaturalColors }
	override val miscColor by c.setting("Misc Color", Color(255, 0, 255)) { !useNaturalColors }

	context(safeContext: SafeContext)
	fun getColor(entity: Entity): Color {
		val group = entity.entityGroup
		return if (useNaturalColors && !hasSpecialCase(entity, group)) entityColor(entity)
		else when (group) {
			EntityGroup.Player ->
				when {
					entity is PlayerEntity && entity.isFriend && separateFriendColor -> friendColor
					else ->
						if (playerDistanceGradient)
							lerp(
								entity.dist(safeContext.player) / 60.0,
								playerDistanceColorClose,
								playerDistanceColorFar
							)
						else playerColor
				}
			EntityGroup.Mob -> mobColor
			EntityGroup.Passive -> passiveColor
			EntityGroup.Vehicle -> vehicleColor
			EntityGroup.Projectile -> projectileColor
			EntityGroup.Boss -> bossColor
			EntityGroup.Decoration -> decorationColor
			else -> miscColor
		}
	}

	context(safeContext: SafeContext)
	fun getColor(blockEntity: BlockEntity): Color =
		if (useNaturalColors) safeContext.blockColor(blockEntity.cachedState, blockEntity.pos)
		else blockColor

	private fun hasSpecialCase(entity: Entity, group: EntityGroup) =
		group == EntityGroup.Player &&
				((entity is OtherClientPlayerEntity && entity.isFriend && separateFriendColor) || playerDistanceGradient)
}