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

import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.buildLine
import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.request.rotation.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.pathing.Path
import com.lambda.pathing.Pathing.findPathAStar
import com.lambda.pathing.PathingSettings
import com.lambda.pathing.goal.SimpleGoal
import com.lambda.threading.runConcurrent
import com.lambda.util.Communication.info
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
import kotlin.system.measureTimeMillis

object Pathfinder : Module(
    name = "Pathfinder",
    description = "Get from A to B",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val pathing = PathingSettings(this)
    private val rotation = RotationSettings(this)

    var path: Path? = null
    private var currentTarget: Vec3d? = null
    private var integralError = Vec3d.ZERO
    private var lastError = Vec3d.ZERO
    private var calculating = false

    init {
        onEnable {
            integralError = Vec3d.ZERO
            lastError = Vec3d.ZERO
        }

        listen<TickEvent.Pre> {
            if (calculating) return@listen
            calculating = true

            runConcurrent {
                val took = measureTimeMillis {
                    path = findPathAStar(
                        player.blockPos.toFastVec(),
                        //                        SimpleGoal(fastVectorOf(0, 78, 0)),
                        SimpleGoal(fastVectorOf(0, 120, 0)),
                        pathing.cutoffTimeout
                    )
                }
                info("Found path of length ${path?.moves?.size} in $took ms")
                println("Path: ${path?.toString()}")
                calculating = false
            }
        }

//        listen<RotationEvent.StrafeInput> { event ->
//            updateTargetNode()
//            currentTarget?.let { target ->
//                event.strafeYaw = player.eyePos.rotationTo(target).yaw
//                val adjustment = calculatePID(target)
//                val yawRad = Math.toRadians(event.strafeYaw)
//                val forward = -sin(yawRad)
//                val strafe = cos(yawRad)
//
//                val forwardComponent = adjustment.x * forward + adjustment.z * strafe
//                val strafeComponent = adjustment.x * strafe - adjustment.z * forward
//
//                val moveInput = buildMovementInput(
//                    forward = forwardComponent,
//                    strafe = strafeComponent,
//                    jump = player.isOnGround && adjustment.y > 0.5
//                )
//                event.input.mergeFrom(moveInput)
//            }
//        }
//
//        onRotate {
//            val nextTarget = path?.moves?.getOrNull(2)?.pos?.toBlockPos() ?: return@onRotate
//            val part = player.eyePos.rotationTo(Vec3d.ofBottomCenter(nextTarget))
//            val targetRotation = Rotation(part.yaw, player.pitch.toDouble())
//
//            lookAt(targetRotation).requestBy(rotation)
//        }

        listen<RenderEvent.StaticESP> { event ->
            path?.moves?.zipWithNext { current, next ->
                val currentPos = current.pos.toBlockPos().toCenterPos()
                val nextPos = next.pos.toBlockPos().toCenterPos()
                event.renderer.buildLine(currentPos, nextPos, Color.GREEN)
            }
        }
    }

    private fun SafeContext.updateTargetNode() {
        path?.moves?.firstOrNull()?.let { firstNode ->
            val nodeVec = Vec3d.ofBottomCenter(firstNode.pos.toBlockPos())
            if (player.pos.distanceTo(nodeVec) < pathing.tolerance) {
                path?.moves?.removeFirst()
                integralError = Vec3d.ZERO
            }
            val next = path?.moves?.firstOrNull()?.pos?.toBlockPos() ?: return
            currentTarget = Vec3d.ofBottomCenter(next)
        } ?: run {
            currentTarget = null
        }
    }

    private fun SafeContext.calculatePID(target: Vec3d): Vec3d {
        val error = target.subtract(player.pos)
        integralError = integralError.add(error)
        val derivativeError = error.subtract(lastError)
        lastError = error

        return error.multiply(pathing.kP)
            .add(integralError.multiply(pathing.kI))
            .add(derivativeError.multiply(pathing.kD))
    }
}