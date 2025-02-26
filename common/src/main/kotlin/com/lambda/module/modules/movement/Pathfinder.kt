/*
 * Copyright 2025 Lambda
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
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.buildLine
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.pathing.Path
import com.lambda.pathing.Pathing.findPathAStar
import com.lambda.pathing.goal.SimpleGoal
import com.lambda.util.math.setAlpha
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.mergeFrom
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toFastVec
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

object Pathfinder : Module(
    name = "Pathfinder",
    description = "Get from A to B",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    // PID Settings
    private val kP by setting("P Gain", 0.5, 0.0..2.0, 0.01)
    private val kI by setting("I Gain", 0.0, 0.0..1.0, 0.01)
    private val kD by setting("D Gain", 0.2, 0.0..1.0, 0.01)
    private val tolerance by setting("Node Tolerance", 0.1, 0.01..1.0, 0.01)

    var path = Path()
    private var currentTarget: Vec3d? = null
    private var integralError = Vec3d.ZERO
    private var lastError = Vec3d.ZERO

    init {
        onEnable {
            path = findPathAStar(
                player.blockPos.toFastVec(),
                SimpleGoal(fastVectorOf(0, 120, 0))
            )
//            currentTarget = Vec3d(0.5, 120.0, 0.5)
            integralError = Vec3d.ZERO
            lastError = Vec3d.ZERO
        }

        listen<RotationEvent.StrafeInput> { event ->
            if (path.nodes.isEmpty()) return@listen

            updateTargetNode()
            currentTarget?.let { target ->
                event.strafeYaw = player.eyePos.rotationTo(target).yaw
                val adjustment = calculatePID(target)
                val yawRad = Math.toRadians(event.strafeYaw)
                val forward = -sin(yawRad)
                val strafe = cos(yawRad)

                val forwardComponent = adjustment.x * forward + adjustment.z * strafe
                val strafeComponent = adjustment.x * strafe - adjustment.z * forward

                val moveInput = buildMovementInput(
                    forward = forwardComponent,
                    strafe = strafeComponent,
                    jump = player.isOnGround && adjustment.y > 0.5
                )
                event.input.mergeFrom(moveInput)
            }
        }

        listen<RenderEvent.StaticESP> { event ->
            path.nodes.zipWithNext { current, next ->
                val currentPos = current.pos.toBlockPos().toCenterPos()
                val nextPos = next.pos.toBlockPos().toCenterPos()
                event.renderer.buildLine(currentPos, nextPos, Color.BLUE.setAlpha(0.25))
            }
        }
    }

    private fun SafeContext.updateTargetNode() {
        path.nodes.firstOrNull()?.let { firstNode ->
            val nodeVec = Vec3d.ofBottomCenter(firstNode.pos.toBlockPos())
            if (player.pos.distanceTo(nodeVec) < tolerance) {
                path.nodes.removeFirst()
                integralError = Vec3d.ZERO
            }
            val next = path.nodes.firstOrNull()?.pos?.toBlockPos() ?: return
            currentTarget = Vec3d.ofBottomCenter(next)
        }
    }

    private fun SafeContext.calculatePID(target: Vec3d): Vec3d {
        val error = target.subtract(player.pos)
        integralError = integralError.add(error)
        val derivativeError = error.subtract(lastError)
        lastError = error

        return error.multiply(kP)
            .add(integralError.multiply(kI))
            .add(derivativeError.multiply(kD))
    }
}