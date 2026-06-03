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

package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.context.SafeContext
import com.lambda.friend.FriendManager.isFriend
import com.lambda.util.EntityUtils
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.NamedEnum
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.entityColor
import com.lambda.util.math.dist
import com.lambda.util.math.lerp
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.Entity
import java.awt.Color

class EntityColorSettings(
	c: Configurable,
	vararg baseGroup: NamedEnum,
	prefix: String = "",
	override val visibility: () -> Boolean = { true },
) : EntityColorsConfig, SettingGroup(c) {
	override val useNaturalColors by c.setting("${prefix}Use Natural Colors", false, "Uses an average color from the entities texture").group(*baseGroup).index()
	override val playerColor by c.setting("${prefix}Player Color", Color(255, 50, 50)) { !useNaturalColors }.group(*baseGroup).index()
	override val playerDistanceGradient by c.setting("${prefix}Player Distance Gradient", true).group(*baseGroup).index()
	override val playerDistanceColorFar by c.setting("${prefix}Player Far Color", Color.GREEN) { playerDistanceGradient }.group(*baseGroup).index()
	override val playerDistanceColorClose by c.setting("${prefix}Player Close Color", Color.RED) { playerDistanceGradient }.group(*baseGroup).index()
	override val separateFriendColor by c.setting("${prefix}Separate Friend Color", true) { useNaturalColors }.group(*baseGroup).index()
	override val friendColor by c.setting("${prefix}Friend Color", Color(0, 255, 255)) { !useNaturalColors || separateFriendColor }.group(*baseGroup).index()
	override val mobColor by c.setting("${prefix}Mob Color", Color(255, 70, 50)) { !useNaturalColors }.group(*baseGroup).index()
	override val passiveColor by c.setting("${prefix}Passive Color", Color(0, 255, 0)) { !useNaturalColors }.group(*baseGroup).index()
	override val vehicleColor by c.setting("${prefix}Vehicle Color", Color(200, 150, 100)) { !useNaturalColors }.group(*baseGroup).index()
	override val projectileColor by c.setting("${prefix}Projectile Color", Color(200, 200, 200)) { !useNaturalColors }.group(*baseGroup).index()
	override val bossColor by c.setting("${prefix}Boss Color", Color(255, 100, 0)) { !useNaturalColors }.group(*baseGroup).index()
	override val decorationColor by c.setting("${prefix}Decoration Color", Color(100, 100, 255)) { !useNaturalColors }.group(*baseGroup).index()
	override val blockColor by c.setting("${prefix}Block Color", Color(200, 200, 200)) { !useNaturalColors }.group(*baseGroup).index()
	override val miscColor by c.setting("${prefix}Misc Color", Color(255, 0, 255)) { !useNaturalColors }.group(*baseGroup).index()

	context(safeContext: SafeContext)
	fun getColor(entity: Entity): Color {
		val group = entity.entityGroup
		return if (useNaturalColors && !hasSpecialCase(entity, group)) entityColor(entity)
		else when (group) {
			EntityUtils.EntityGroup.Player ->
				when {
					entity is OtherClientPlayerEntity && entity.isFriend && separateFriendColor -> friendColor
					else ->
						if (playerDistanceGradient)
							lerp(
								entity.dist(safeContext.player) / 60.0,
								playerDistanceColorClose,
								playerDistanceColorFar
							)
						else playerColor
				}
			EntityUtils.EntityGroup.Mob -> mobColor
			EntityUtils.EntityGroup.Passive -> passiveColor
			EntityUtils.EntityGroup.Vehicle -> vehicleColor
			EntityUtils.EntityGroup.Projectile -> projectileColor
			EntityUtils.EntityGroup.Boss -> bossColor
			EntityUtils.EntityGroup.Decoration -> decorationColor
			else -> miscColor
		}
	}

	context(safeContext: SafeContext)
	fun getColor(blockEntity: BlockEntity): Color =
		if (useNaturalColors) safeContext.blockColor(blockEntity.cachedState, blockEntity.pos)
		else blockColor

	private fun hasSpecialCase(entity: Entity, group: EntityUtils.EntityGroup) =
		group == EntityUtils.EntityGroup.Player &&
				((entity is OtherClientPlayerEntity && entity.isFriend && separateFriendColor) || playerDistanceGradient)
}