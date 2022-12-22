package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.world.getHitVec
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

class LookAtBlock(private val blockPos: BlockPos) : Activity() {
    override fun SafeClientEvent.onInitialize() {
        val diff = player.getPositionEyes(1f).subtract(blockPos.toVec3dCenter())
        val normalizedVec = diff.normalize()

        val side = EnumFacing.getFacingFromVector(normalizedVec.x.toFloat(), normalizedVec.y.toFloat(), normalizedVec.z.toFloat())

        val rotation = getRotationTo(getHitVec(blockPos, side))

        connection.sendPacket(CPacketPlayer.Rotation(rotation.x, rotation.y, player.onGround))
    }
}