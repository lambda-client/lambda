/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.construction.context

import com.lambda.config.groups.BuildConfig
import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.request.rotation.RotationRequest
import com.lambda.util.world.raycast.RayCastUtils.distanceTo
import net.minecraft.block.BlockState
import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

data class BreakContext(
    override val pov: Vec3d,
    override val result: BlockHitResult,
    override val rotation: RotationRequest,
    override var checkedState: BlockState,
    override val targetState: TargetState,
    override var hotbarIndex: Int,
    var instantBreak: Boolean,
) : BuildContext {
    private val baseColor = Color(222, 0, 0, 25)
    private val sideColor = Color(222, 0, 0, 100)

    override val expectedPos: BlockPos
        get() = result.blockPos

    override val distance: Double by lazy {
        result.distanceTo(pov)
    }

    fun exposedSides(ctx: SafeContext) =
        Direction.entries.filter {
            ctx.world.isAir(result.blockPos.offset(it))
        }

    override val expectedState: BlockState = checkedState.fluidState.blockState

    override fun compareTo(other: BuildContext): Int {
        return when (other) {
            is BreakContext -> compareBy<BreakContext> {
                it.rotation.target.angleDistance
            }.compare(this, other)

            else -> 1
        }
    }

    override fun shouldRotate(config: BuildConfig) = config.breakSettings.rotateForBreak

    override fun SafeContext.buildRenderer() {
        withState(checkedState, expectedPos, baseColor, DirectionMask.ALL.exclude(result.side))
        withState(checkedState, expectedPos, sideColor, result.side)
    }

    fun startBreakPacket(sequence: Int, connection: ClientPlayNetworkHandler) =
        breakPacket(Action.START_DESTROY_BLOCK, sequence, connection)

    fun stopBreakPacket(sequence: Int, connection: ClientPlayNetworkHandler) =
        breakPacket(Action.STOP_DESTROY_BLOCK, sequence, connection)

    fun abortBreakPacket(sequence: Int, connection: ClientPlayNetworkHandler) =
        breakPacket(Action.ABORT_DESTROY_BLOCK, sequence, connection)

    private fun breakPacket(action: Action, sequence: Int, connection: ClientPlayNetworkHandler) =
        connection.sendPacket(
            PlayerActionC2SPacket(
                action,
                expectedPos,
                result.side,
                sequence
            )
        )
}
