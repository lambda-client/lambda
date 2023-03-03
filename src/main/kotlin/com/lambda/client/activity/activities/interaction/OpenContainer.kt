package com.lambda.client.activity.activities.interaction

import com.lambda.client.activity.Activity
import com.lambda.client.activity.activities.types.AttemptActivity
import com.lambda.client.activity.activities.types.RotatingActivity
import com.lambda.client.activity.activities.types.TimeoutActivity
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.onMainThreadSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getHitVec
import com.lambda.client.util.world.getHitVecOffset
import kotlinx.coroutines.launch
import net.minecraft.block.BlockContainer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.server.SPacketWindowItems
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

class OpenContainer(
    private val containerPos: BlockPos,
    override var rotation: Vec2f? = null,
    override val timeout: Long = 1000L,
    override val maxAttempts: Int = 3,
    override var usedAttempts: Int = 0,
) : RotatingActivity, TimeoutActivity, AttemptActivity, Activity() {
    override fun SafeClientEvent.onInitialize() {
        val currentState = world.getBlockState(containerPos)

        if (currentState.block !is BlockContainer) {
            failedWith(BlockNotContainerException(currentState))
            return
        }

        val diff = player.getPositionEyes(1f).subtract(containerPos.toVec3dCenter())
        val normalizedVec = diff.normalize()

        val side = EnumFacing.getFacingFromVector(normalizedVec.x.toFloat(), normalizedVec.y.toFloat(), normalizedVec.z.toFloat())
        val hitVecOffset = getHitVecOffset(side)

        rotation = getRotationTo(getHitVec(containerPos, side))

        connection.sendPacket(CPacketPlayerTryUseItemOnBlock(containerPos, side, EnumHand.MAIN_HAND, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat()))
        player.swingArm(EnumHand.MAIN_HAND)
    }

    init {
        safeListener<PacketEvent.PostReceive> {
            if (it.packet !is SPacketWindowItems) return@safeListener

            defaultScope.launch {
                onMainThreadSafe {
                    success()
                }
            }
        }
    }

    class BlockNotContainerException(blockState: Any) : Exception("Block $blockState is not a container")
}