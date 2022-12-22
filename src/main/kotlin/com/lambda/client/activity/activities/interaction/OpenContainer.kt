package com.lambda.client.activity.activities.interaction

import com.lambda.client.LambdaMod
import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.InstantActivity
import com.lambda.client.activity.activities.SetState
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getHitVecOffset
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.server.SPacketOpenWindow
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

class OpenContainer(private val containerPos: BlockPos) : InstantActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val diff = player.getPositionEyes(1f).subtract(containerPos.toVec3dCenter())
        val normalizedVec = diff.normalize()

        val side = EnumFacing.getFacingFromVector(normalizedVec.x.toFloat(), normalizedVec.y.toFloat(), normalizedVec.z.toFloat())
        val hitVecOffset = getHitVecOffset(side)

        val rotation = getRotationTo(getHitVec(containerPos, side))

        connection.sendPacket(CPacketPlayer.Rotation(rotation.x, rotation.y, player.onGround))
        connection.sendPacket(CPacketPlayerTryUseItemOnBlock(containerPos, side, EnumHand.MAIN_HAND, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat()))
        player.swingArm(EnumHand.MAIN_HAND)
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            when (it.packet) {
                is SPacketOpenWindow -> {
                    addSubActivities(SetState(ActivityStatus.SUCCESS))
                }
            }
        }
    }
}