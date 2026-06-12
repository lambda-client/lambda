/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.movement

import com.lambda.context.SafeContext
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.motionY
import com.lambda.util.player.MovementUtils.setSpeed
import net.minecraft.block.Blocks
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Full
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

@Suppress("unused")
object Jesus : Module(
    name = "Jesus",
    description = "Allows to walk on water",
    tag = ModuleTag.MOVEMENT,
) {
    private val mode by setting("Mode", Mode.Ncp)

    // Dolphin
    private val dolphinStrength by setting("Dolphin Strength", 0.1, 0.01..0.2, 0.01) { mode == Mode.NcpDolphin }

    // NCP New
    private val slowDown by setting("Slow Down", true) { mode == Mode.NcpNew }

    private val fullShape = VoxelShapes.fullCube()
    private var goUp = true
    private var swimmingTicks = 0

    private var prevPos = Vec3d.ZERO

    enum class Mode(override val displayName: String, val collision: Boolean) : NamedEnum {
        Ncp("NCP", true),
        NcpDolphin("NCP Dolphin", false),
        NcpNew("NCP New", true)
    }

    private var shouldWork = false

    init {
        listen<PlayerPacketEvent.Send> { event ->
            if (!shouldWork || !waterAt(-0.0001)) return@listen

            if (!player.isOnGround) return@listen

            when (mode) {
                Mode.Ncp -> {
                    val offset = if (player.age % 2 == 0) 0.001 else 0.002
                    event.lowerPosition(offset)
                }
                Mode.NcpNew -> event.lowerPosition(0.02 + 0.0001 * swimmingTicks)
                else -> {}
            }
        }

        listen<MovementEvent.Player.Pre> {
            if (!shouldWork) return@listen

            goUp = waterAt(0.0001)
            val collidingWater = waterAt(-0.0001)

            when (mode) {
                Mode.Ncp -> {
                    if (!collidingWater || !player.isOnGround) return@listen
                    setSpeed(Speed.NCP_BASE_SPEED * isInputting.toInt())
                }

                Mode.NcpDolphin -> {
                    if (goUp) {
                        player.motionY = dolphinStrength

                        if (!waterAt(0.2)) {
                            setSpeed(Speed.NCP_BASE_SPEED * isInputting.toInt())
                        } else player.motionY = 0.18
                    }
                }

                Mode.NcpNew -> {
                    if (!collidingWater) {
                        swimmingTicks = 0
                        return@listen
                    }

                    if (++swimmingTicks < 15) {
                        if (player.isOnGround) {
                            setSpeed(Speed.NCP_BASE_SPEED * isInputting.toInt())
                        }

                        return@listen
                    }

                    swimmingTicks = 0

                    if (slowDown) setSpeed(0.0)
                    player.motionY = 0.08000001
                }
            }
        }

        listen<WorldEvent.Collision> { event ->
            if (!shouldWork || goUp || !mode.collision) return@listen

            if (event.state.block == Blocks.WATER) {
                event.shape = fullShape
            }
        }

        listen<MovementEvent.InputUpdate> {
            if (!shouldWork || !goUp || mode == Mode.NcpDolphin) return@listen
            it.input.jump()
        }

        listen<TickEvent.Pre> {
            shouldWork = !player.abilities.flying && !player.isElytraFlying && !player.input.playerInput.sneak
        }

        onEnable {
            goUp = false
            swimmingTicks = 0
            shouldWork = false
        }
    }

    private fun PlayerPacketEvent.Send.lowerPosition(amount: Double) {
        with(packet) {
            val newPos = Vec3d(x, y - amount, z)
            when(this) {
                is Full -> packet = Full(newPos, yaw, pitch, isOnGround, horizontalCollision())
                is PositionAndOnGround -> packet = PositionAndOnGround(newPos, isOnGround, horizontalCollision())
                else -> if (newPos != prevPos) packet = PositionAndOnGround(newPos, isOnGround, horizontalCollision())
            }

            prevPos = newPos
        }
    }

    private fun SafeContext.waterAt(yOffset: Double): Boolean {
        val b = player.boundingBox
        val y = b.minY + yOffset

        for (xref in 0..1) {
            for (zref in 0..1) {
                val x = if (xref == 0) b.minX else b.maxX
                val z = if (zref == 0) b.minZ else b.maxZ

                val pos = BlockPos.ofFloored(x, y, z)
                val state = world.getBlockState(pos)
                if (state.block != Blocks.WATER) return false
            }
        }

        return true
    }
}
