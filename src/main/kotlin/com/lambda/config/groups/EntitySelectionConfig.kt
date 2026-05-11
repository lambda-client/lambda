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