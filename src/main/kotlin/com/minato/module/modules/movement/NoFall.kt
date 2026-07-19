
package com.minato.module.modules.movement

import com.minato.event.events.MovementEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.PacketUtils.sendPacket
import com.minato.util.math.component1
import com.minato.util.math.component2
import com.minato.util.math.component3
import com.minato.util.player.MovementUtils.motion
import com.minato.util.player.MovementUtils.motionY
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

@Suppress("unused")
object NoFall : Module(
    name = "NoFall",
    description = "Reduces fall damage",
    tag = ModuleTag.MOVEMENT,
) {
    private val mode by setting("Mode", Mode.Grim)

    enum class Mode {
        Grim
    }

    init {
        listen<MovementEvent.Player.Post> {
            when (mode) {
                Mode.Grim -> {
                    if (player.fallDistance + player.motionY < 3.0) return@listen

                    val (x, y, z) = player.pos
                    connection.sendPacket(PlayerMoveC2SPacket.Full(x, y + 0.0000000001, z, 0.01f, 90f, false, true)) // TODO: Check this after update
                    connection.sendPacket(PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0, player.yaw, player.pitch)) // TODO: This is wrong, fix it
                    connection.sendPacket {
                        PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM,
                            BlockPos.ORIGIN,
                            Direction.DOWN
                        )
                    }
                    player.motion = Vec3d.ZERO

                    player.fallDistance = 0.0
                }
            }
        }
    }

}
