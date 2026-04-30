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

import com.lambda.Lambda.mc
import com.lambda.config.Config
import com.lambda.config.SettingGroup
import com.lambda.util.EntityUtils.blockEntityMap
import com.lambda.util.EntityUtils.bossEntityMap
import com.lambda.util.EntityUtils.decorationEntityMap
import com.lambda.util.EntityUtils.miscEntityMap
import com.lambda.util.EntityUtils.mobEntityMap
import com.lambda.util.EntityUtils.passiveEntityMap
import com.lambda.util.EntityUtils.playerEntityMap
import com.lambda.util.EntityUtils.projectileEntityMap
import com.lambda.util.EntityUtils.vehicleEntityMap
import com.lambda.util.NamedEnum
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.SpawnGroup

class EntitySelectionSettings(
	c: Config,
	vararg baseGroup: NamedEnum,
	prefix: String = "",
	override val visibility: () -> Boolean = { true },
) : EntitySelectionConfig, SettingGroup(c) {
	override val self by c.setting("${prefix}Self", false, "Render own player in third person").group(*baseGroup).index()
	override val enablePlayerEntities by c.setting("${prefix}Enable Player Entities", true).group(*baseGroup).index()
	override val playerEntities by c.setting("${prefix}Player Entities", playerEntityMap.values.toSet(), playerEntityMap.values.toSet(), "Player entities to omit from rendering") { enablePlayerEntities }.group(*baseGroup).index()
	override val enableMobEntities by c.setting("${prefix}Enable Mob Entities", true).group(*baseGroup).index()
	override val mobEntities by c.setting("${prefix}Mob Entities", mobEntityMap.values.toSet(), mobEntityMap.values.toSet(), "Mob entities to omit from rendering") { enableMobEntities }.group(*baseGroup).index()
	override val enablePassiveEntities by c.setting("${prefix}Enable Passive Entities", true).group(*baseGroup).index()
	override val passiveEntities by c.setting("${prefix}Passive Entities", emptySet(), passiveEntityMap.values.toSet(), "Passive entities to omit from rendering") { enablePassiveEntities }.group(*baseGroup).index()
	override val enableVehicleEntities by c.setting("${prefix}Enable Vehicle Entities", true).group(*baseGroup).index()
	override val vehicleEntities by c.setting("${prefix}Vehicle Entities", emptySet(), vehicleEntityMap.values.toSet(), "Vehicle entities to omit from rendering") { enableVehicleEntities }.group(*baseGroup).index()
	override val enableProjectileEntities by c.setting("${prefix}Enable Projectile Entities", true).group(*baseGroup).index()
	override val projectileEntities by c.setting("${prefix}Projectile Entities", emptySet(), projectileEntityMap.values.toSet(), "Projectile entities to omit from rendering") { enableProjectileEntities }.group(*baseGroup).index()
	override val enableBossEntities by c.setting("${prefix}Enable Boss Entities", true).group(*baseGroup).index()
	override val bossEntities by c.setting("${prefix}Boss Entities", bossEntityMap.values.toSet(), bossEntityMap.values.toSet(), "Boss entities to omit from rendering") { enableBossEntities }.group(*baseGroup).index()
	override val enableDecorationEntities by c.setting("${prefix}Enable Decoration Entities", true).group(*baseGroup).index()
	override val decorationEntities by c.setting("${prefix}Decoration Entities", emptySet(), decorationEntityMap.values.toSet(), "Decoration entities to omit from rendering") { enableDecorationEntities }.group(*baseGroup).index()
	override val enableBlockEntities by c.setting("${prefix}Enable Block Entities", true).group(*baseGroup).index()
	override val blockEntities by c.setting("${prefix}Block Entities", emptySet(), blockEntityMap.values.toSet(), "Block entities to omit from rendering") { enableBlockEntities }.group(*baseGroup).index()
	override val enableMiscEntities by c.setting("${prefix}Enable Misc Entities", true).group(*baseGroup).index()
	override val miscEntities by c.setting("${prefix}Misc Entities", emptySet(), miscEntityMap.values.toSet(), "Miscellaneous entities to omit from rendering") { enableMiscEntities }.group(*baseGroup).index()

	fun isSelected(entity: Entity): Boolean {
		val name = entity::class.simpleName
		return if (entity == mc.player) self
		else when (entity.type.spawnGroup) {
			SpawnGroup.MISC ->
				(enableMiscEntities && miscEntityMap[name] in miscEntities) ||
						(enablePlayerEntities && playerEntityMap[name] in playerEntities) ||
						(enableProjectileEntities && projectileEntityMap[name] in projectileEntities) ||
						(enableVehicleEntities && vehicleEntityMap[name] in vehicleEntities) ||
						(enableDecorationEntities && decorationEntityMap[name] in decorationEntities) ||
						(enablePassiveEntities && passiveEntityMap[name] in passiveEntities) ||
						(enableMobEntities && mobEntityMap[name] in mobEntities) ||
						(enableBossEntities && bossEntityMap[name] in bossEntities)
			SpawnGroup.WATER_AMBIENT,
			SpawnGroup.WATER_CREATURE,
			SpawnGroup.AMBIENT,
			SpawnGroup.AXOLOTLS,
			SpawnGroup.CREATURE,
			SpawnGroup.UNDERGROUND_WATER_CREATURE -> (enablePassiveEntities && passiveEntityMap[name] in passiveEntities)
			SpawnGroup.MONSTER ->
				(enableMobEntities && mobEntityMap[name] in mobEntities) ||
						(enableBossEntities && bossEntityMap[name] in bossEntities)
		}
	}

	fun isSelected(blockEntity: BlockEntity) =
		(enableBlockEntities && blockEntityMap[blockEntity.javaClass.simpleName] in blockEntities)
}