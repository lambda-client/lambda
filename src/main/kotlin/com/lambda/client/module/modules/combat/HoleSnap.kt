package com.lambda.client.module.modules.combat

import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.commons.extension.toRadian
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.movement.Speed
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.MovementUtils.isCentered
import com.lambda.client.util.MovementUtils.resetMove
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.combat.SurroundUtils
import com.lambda.client.util.combat.SurroundUtils.checkHole
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.threads.safeAsyncListener
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.MovementInputFromOptions
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.InputUpdateEvent
import org.lwjgl.opengl.GL11.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object HoleSnap : Module(
    name = "HoleSnap",
    description = "Move you into the hole nearby",
    category = Category.COMBAT
) {
    private val airStrafe by setting("Air Strafe", true)
    private val disableStrafe by setting("Disable Strafe", true)
    private val range by setting("Range", 2.5f, 0.5f..4.0f, 0.25f)

    private var holePos: BlockPos? = null
    private var stuckTicks = 0

    init {
        onDisable {
            holePos = null
            stuckTicks = 0
        }

        safeListener<RenderWorldEvent>(1) {
            holePos?.let {
                if (player.flooredPosition == it) return@safeListener

                val posFrom = EntityUtils.getInterpolatedPos(player, LambdaTessellator.pTicks())
                val posTo = it.toVec3d(0.5, 0.0, 0.5)
                val buffer = LambdaTessellator.buffer

                glLineWidth(3.0f)
                glDisable(GL_DEPTH_TEST)
                LambdaTessellator.begin(GL_LINES)

                buffer.pos(posFrom.x, posFrom.y, posFrom.z).color(32, 255, 32, 255).endVertex()
                buffer.pos(posTo.x, posTo.y, posTo.z).color(32, 255, 32, 255).endVertex()

                LambdaTessellator.render()
                glLineWidth(1.0f)
                glEnable(GL_DEPTH_TEST)
            }
        }

        listener<PacketEvent.Receive> {
            if (it.packet is SPacketPlayerPosLook) disable()
        }

        listener<InputUpdateEvent>(-69) {
            if (it.movementInput is MovementInputFromOptions && holePos != null) {
                it.movementInput.resetMove()
            }
        }

        safeAsyncListener<PlayerMoveEvent> {
            if (!player.isEntityAlive) return@safeAsyncListener

            val currentSpeed = player.speed

            if (shouldDisable(currentSpeed)) {
                disable()
                return@safeAsyncListener
            }

            getHole()?.let {
                if (disableStrafe && Speed.mode.value == Speed.SpeedMode.STRAFE) Speed.disable()
                if ((airStrafe || player.onGround) && !player.isCentered(it)) {
                    val playerPos = player.positionVector
                    val targetPos = Vec3d(it.x + 0.5, player.posY, it.z + 0.5)

                    val yawRad = RotationUtils.getRotationTo(playerPos, targetPos).x.toRadian()
                    val dist = playerPos.distanceTo(targetPos)
                    val speed = if (player.onGround) min(0.2805, dist / 2.0) else currentSpeed + 0.02

                    player.motionX = -sin(yawRad) * speed
                    player.motionZ = cos(yawRad) * speed

                    if (player.collidedHorizontally) stuckTicks++
                    else stuckTicks = 0
                }
            }
        }
    }

    private fun SafeClientEvent.shouldDisable(currentSpeed: Double) =
        holePos?.let { player.posY < it.y } ?: false
            || stuckTicks > 5 && currentSpeed < 0.1
            || currentSpeed < 0.01 && checkHole(player) != SurroundUtils.HoleType.NONE

    private fun SafeClientEvent.getHole() =
        if (player.ticksExisted % 10 == 0 && player.flooredPosition != holePos) findHole()
        else holePos ?: findHole()

    private fun SafeClientEvent.findHole(): BlockPos? {
        var closestHole = Pair(69.69, BlockPos.ORIGIN)
        val playerPos = player.flooredPosition
        val ceilRange = range.ceilToInt()
        val posList = VectorUtils.getBlockPositionsInArea(playerPos.add(ceilRange, -1, ceilRange), playerPos.add(-ceilRange, -1, -ceilRange))

        for (posXZ in posList) {
            val dist = player.distanceTo(posXZ)
            if (dist > range || dist > closestHole.first) continue

            for (posY in 0..5) {
                val pos = posXZ.add(0, -posY, 0)
                if (!world.isAirBlock(pos.up())) break
                if (checkHole(pos) == SurroundUtils.HoleType.NONE) continue
                closestHole = dist to pos
            }
        }

        return if (closestHole.second != BlockPos.ORIGIN) closestHole.second.also { holePos = it }
        else null
    }
}