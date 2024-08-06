package com.lambda.module.modules.combat

import com.lambda.context.SafeContext
import com.lambda.event.events.AttackEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.primitives.extension.component1
import com.lambda.util.primitives.extension.component2
import com.lambda.util.primitives.extension.component3
import com.lambda.util.primitives.extension.rotation
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object Criticals : Module(
    name = "Criticals",
    description = "Forces your hits to be critical",
    defaultTags = setOf(ModuleTag.COMBAT)
) {
    private val mode by setting("Mode", Mode.Grim)

    enum class Mode {
        Grim
    }

    init {
        listener<AttackEvent.Pre> {
            when (mode) {
                Mode.Grim -> {
                    if (player.isOnGround) posPacket(0.00000001, rotation = player.rotation)
                    posPacket(-0.000000001, rotation = player.eyePos.rotationTo(it.entity.boundingBox.center))

                    connection.sendPacket(PlayerInteractItemC2SPacket(Hand.OFF_HAND, 0))
                    connection.sendPacket(PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN))
                }
            }
        }
    }

    private fun SafeContext.posPacket(yOffset: Double, ground: Boolean = false, rotation: Rotation?) {
        val (x, y, z) = player.pos

        val packet = rotation?.let {
            PlayerMoveC2SPacket.Full(x, y + yOffset, z, it.yawF, it.pitchF, ground)
        } ?: PlayerMoveC2SPacket.PositionAndOnGround(x, y + yOffset, z, ground)

        connection.sendPacket(packet)
    }
}