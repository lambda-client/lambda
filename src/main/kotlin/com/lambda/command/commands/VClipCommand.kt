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

package com.lambda.command.commands

import com.lambda.Lambda.mc
import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.util.extension.CommandBuilder

object VClipCommand : LambdaCommand(
    name = "vclip",
    usage = "vclip <distance>",
    description = "Teleports the player up a specified distance"
) {
    override fun CommandBuilder.create() {
        required(double("distance")) { distance ->
            execute {
                val player = mc.player ?: return@execute
                val distance = distance().value()
                player.vehicle?.let { vehicle ->
                    vehicle.setPos(vehicle.x, vehicle.y + distance, vehicle.z)
                }
                player.setPos(player.x, player.y + distance, player.z)
            }
        }
    }
}