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

package com.lambda.command.commands

import com.lambda.Lambda.mc
import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.util.extension.CommandBuilder
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d

object HClipCommand : LambdaCommand(
	name = "hclip",
	usage = "hclip <distance>",
	description = "Teleports the player forward a specified distance"
) {
	override fun CommandBuilder.create() {
		required(double("distance")) { distance ->
			execute {
				val player = mc.player ?: return@execute
				val dir = Vec3d.fromPolar(Vec2f(player.pitch, player.yaw)).normalize()
				val distance = distance().value()
				val xBlocks = dir.x * distance
				val zBlocks = dir.z * distance
				player.vehicle?.let { vehicle ->
					vehicle.setPos(vehicle.x + xBlocks, vehicle.y, vehicle.z + zBlocks)
				}
				player.setPos(player.x + xBlocks, player.y, player.z + zBlocks)
			}
		}
	}
}