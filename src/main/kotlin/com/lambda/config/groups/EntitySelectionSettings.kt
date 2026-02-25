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
import com.lambda.config.Configurable
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
	prefix: String = "",
	c: Configurable,
	vararg baseGroup: NamedEnum,
	override val visibility: () -> Boolean = { true },
) : EntitySelectionConfig, SettingGroup(c) {
	override val self by c.setting("${prefix}Self", false, "Render own player in third person").group(*baseGroup).index()
	override val playerEntities by c.setting("${prefix}Player Entities", playerEntityMap.values.toSet(), playerEntityMap.values.toSet(), "Player entities to omit from rendering").group(*baseGroup).index()
	override val mobEntities by c.setting("${prefix}Mob Entities", mobEntityMap.values.toSet(), mobEntityMap.values.toSet(), "Mob entities to omit from rendering").group(*baseGroup).index()
	override val passiveEntities by c.setting("${prefix}Passive Entities", emptySet(), passiveEntityMap.values.toSet(), "Passive entities to omit from rendering").group(*baseGroup).index()
	override val vehicleEntities by c.setting("${prefix}Vehicle Entities", emptySet(), vehicleEntityMap.values.toSet(), "Vehicle entities to omit from rendering").group(*baseGroup).index()
	override val projectileEntities by c.setting("${prefix}Projectile Entities", emptySet(), projectileEntityMap.values.toSet(), "Projectile entities to omit from rendering").group(*baseGroup).index()
	override val bossEntities by c.setting("${prefix}Boss Entities", emptySet(), bossEntityMap.values.toSet(), "Boss entities to omit from rendering").group(*baseGroup).index()
	override val decorationEntities by c.setting("${prefix}Decoration Entities", emptySet(), decorationEntityMap.values.toSet(), "Decoration entities to omit from rendering").group(*baseGroup).index()
	override val blockEntities by c.setting("${prefix}Block Entities", emptySet(), blockEntityMap.values.toSet(), "Block entities to omit from rendering").group(*baseGroup).index()
	override val miscEntities by c.setting("${prefix}Misc Entities", emptySet(), miscEntityMap.values.toSet(), "Miscellaneous entities to omit from rendering").group(*baseGroup).index()

	fun isSelected(entity: Entity): Boolean {
		val name = entity::class.simpleName
		return if (entity == mc.player && !self) false
		else when (entity.type.spawnGroup) {
			SpawnGroup.MISC ->
				miscEntityMap[name] in miscEntities ||
						playerEntityMap[name] in playerEntities ||
						projectileEntityMap[name] in projectileEntities ||
						vehicleEntityMap[name] in vehicleEntities ||
						decorationEntityMap[name] in decorationEntities ||
						passiveEntityMap[name] in passiveEntities ||
						mobEntityMap[name] in mobEntities ||
						bossEntityMap[name] in bossEntities
			SpawnGroup.WATER_AMBIENT,
			SpawnGroup.WATER_CREATURE,
			SpawnGroup.AMBIENT,
			SpawnGroup.AXOLOTLS,
			SpawnGroup.CREATURE,
			SpawnGroup.UNDERGROUND_WATER_CREATURE -> passiveEntityMap[name] in passiveEntities
			SpawnGroup.MONSTER -> mobEntityMap[name] in mobEntities
		}
	}

	fun isSelected(blockEntity: BlockEntity) =
		blockEntityMap[blockEntity.javaClass.simpleName] in blockEntities
}