
package com.minato.command.commands

import com.minato.Minato.mc
import com.minato.brigadier.argument.double
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.util.extension.CommandBuilder
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d

object HClipCommand : MinatoCommand(
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