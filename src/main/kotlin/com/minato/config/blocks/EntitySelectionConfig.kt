
package com.minato.config.blocks

interface EntitySelectionConfig {
	val self: Boolean
	val enablePlayerEntities: Boolean
	val playerEntities: Collection<String>
	val enableMobEntities: Boolean
	val mobEntities: Collection<String>
	val enablePassiveEntities: Boolean
	val passiveEntities: Collection<String>
	val enableVehicleEntities: Boolean
	val vehicleEntities: Collection<String>
	val enableProjectileEntities: Boolean
	val projectileEntities: Collection<String>
	val enableBossEntities: Boolean
	val bossEntities: Collection<String>
	val enableDecorationEntities: Boolean
	val decorationEntities: Collection<String>
	val enableBlockEntities: Boolean
	val blockEntities: Collection<String>
	val enableMiscEntities: Boolean
	val miscEntities: Collection<String>
}