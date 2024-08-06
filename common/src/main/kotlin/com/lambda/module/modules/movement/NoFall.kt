package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.player.MovementUtils.motion
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.primitives.extension.component1
import com.lambda.util.primitives.extension.component2
import com.lambda.util.primitives.extension.component3
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

object NoFall : Module(
    name = "NoFall",
    description = "Reduces fall damage",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val mode by setting("Mode", Mode.Grim)

    enum class Mode {
        Grim
    }

    init {
        listener<MovementEvent.Post> {
            when (mode) {
                Mode.Grim -> {
                    if (player.fallDistance + player.motionY < 3.0) return@listener

                    val (x, y, z) = player.pos
                    connection.sendPacket(PlayerMoveC2SPacket.Full(x, y + 0.0000000001, z, 0.01f, 90f, false))
                    connection.sendPacket(PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0))
                    connection.sendPacket(PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN))
                    player.motion = Vec3d.ZERO

                    player.fallDistance = 0f
                }
            }
        }
    }

}