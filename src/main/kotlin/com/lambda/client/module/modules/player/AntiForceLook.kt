package com.lambda.client.module.modules.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.accessor.network.AccessorNetHandlerPlayClient
import net.minecraft.network.play.client.CPacketConfirmTeleport
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketPlayerPosLook

object AntiForceLook : Module(
    name = "AntiForceLook",
    description = "Stops server packets from turning your head",
    category = Category.PLAYER
) {

    init {
        safeListener<PacketEvent.Receive>(priority = -5) {
            if (it.packet is SPacketPlayerPosLook) {
                it.cancel()
                mc.addScheduledTask { handlePosLook(it.packet) }
            }
        }
    }

    private fun SafeClientEvent.handlePosLook(packet: SPacketPlayerPosLook) {
        /** {@see NetHandlerPlayClient#handlePlayerPosLook} **/
        var x = packet.x
        var y = packet.y
        var z = packet.z
        var yaw = packet.yaw
        var pitch = packet.pitch

        if (packet.flags.contains(SPacketPlayerPosLook.EnumFlags.X)) x += player.posX else player.motionX = 0.0
        if (packet.flags.contains(SPacketPlayerPosLook.EnumFlags.Y)) y += player.posY else player.motionY = 0.0
        if (packet.flags.contains(SPacketPlayerPosLook.EnumFlags.Z)) z += player.posZ else player.motionZ = 0.0
        if (packet.flags.contains(SPacketPlayerPosLook.EnumFlags.X_ROT)) pitch += player.rotationPitch
        if (packet.flags.contains(SPacketPlayerPosLook.EnumFlags.Y_ROT)) yaw += player.rotationYaw

        player.setPositionAndRotation(x, y, z,
            // retain current yaw and pitch client-side
            player.rotationYaw,
            player.rotationPitch
        )

        // spoof to server that we are using its rotation
        connection.sendPacket(CPacketConfirmTeleport(packet.teleportId))

        connection.sendPacket(CPacketPlayer.PositionRotation(
            player.posX,
            player.entityBoundingBox.minY,
            player.posZ,
            yaw,
            pitch,
            false)
        )

        val connection = (player.connection as? AccessorNetHandlerPlayClient) ?: return

        if (!connection.isDoneLoadingTerrain) {
            player.prevPosX = player.posX
            player.prevPosY = player.posY
            player.prevPosZ = player.posZ
            connection.isDoneLoadingTerrain = true
            mc.displayGuiScreen(null)
        }
    }
}