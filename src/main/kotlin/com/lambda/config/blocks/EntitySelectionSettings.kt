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

import com.lambda.Lambda.mc
import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.util.EntityUtils.blockEntityMap
import com.lambda.util.EntityUtils.bossEntityMap
import com.lambda.util.EntityUtils.decorationEntityMap
import com.lambda.util.EntityUtils.miscEntityMap
import com.lambda.util.EntityUtils.mobEntityMap
import com.lambda.util.EntityUtils.passiveEntityMap
import com.lambda.util.EntityUtils.playerEntityMap
import com.lambda.util.EntityUtils.projectileEntityMap
import com.lambda.util.EntityUtils.vehicleEntityMap
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.SpawnGroup

class EntitySelectionSettings(override val c: Config) : ConfigBlock, EntitySelectionConfig {
	override val self by c.setting("Self", false, "Render own player in third person")
	override val enablePlayerEntities by c.setting("Enable Player Entities", true)
	override val playerEntities by c.setting("Player Entities", playerEntityMap.values.toSet(), playerEntityMap.values.toSet(), "Player entities to omit from rendering") { enablePlayerEntities }
	override val enableMobEntities by c.setting("Enable Mob Entities", true)
	override val mobEntities by c.setting("Mob Entities", mobEntityMap.values.toSet(), mobEntityMap.values.toSet(), "Mob entities to omit from rendering") { enableMobEntities }
	override val enablePassiveEntities by c.setting("Enable Passive Entities", true)
	override val passiveEntities by c.setting("Passive Entities", emptySet(), passiveEntityMap.values.toSet(), "Passive entities to omit from rendering") { enablePassiveEntities }
	override val enableVehicleEntities by c.setting("Enable Vehicle Entities", true)
	override val vehicleEntities by c.setting("Vehicle Entities", emptySet(), vehicleEntityMap.values.toSet(), "Vehicle entities to omit from rendering") { enableVehicleEntities }
	override val enableProjectileEntities by c.setting("Enable Projectile Entities", true)
	override val projectileEntities by c.setting("Projectile Entities", emptySet(), projectileEntityMap.values.toSet(), "Projectile entities to omit from rendering") { enableProjectileEntities }
	override val enableBossEntities by c.setting("Enable Boss Entities", true)
	override val bossEntities by c.setting("Boss Entities", bossEntityMap.values.toSet(), bossEntityMap.values.toSet(), "Boss entities to omit from rendering") { enableBossEntities }
	override val enableDecorationEntities by c.setting("Enable Decoration Entities", true)
	override val decorationEntities by c.setting("Decoration Entities", emptySet(), decorationEntityMap.values.toSet(), "Decoration entities to omit from rendering") { enableDecorationEntities }
	override val enableBlockEntities by c.setting("Enable Block Entities", true)
	override val blockEntities by c.setting("Block Entities", emptySet(), blockEntityMap.values.toSet(), "Block entities to omit from rendering") { enableBlockEntities }
	override val enableMiscEntities by c.setting("Enable Misc Entities", true)
	override val miscEntities by c.setting("Misc Entities", emptySet(), miscEntityMap.values.toSet(), "Miscellaneous entities to omit from rendering") { enableMiscEntities }

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