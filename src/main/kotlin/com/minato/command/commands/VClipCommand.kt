
package com.minato.command.commands

import com.minato.Minato.mc
import com.minato.brigadier.argument.double
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.util.extension.CommandBuilder

object VClipCommand : MinatoCommand(
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