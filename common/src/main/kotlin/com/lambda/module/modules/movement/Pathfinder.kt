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
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.interaction.request.rotation.Rotation
import com.lambda.interaction.request.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotation.RotationManager.onRotate
import com.lambda.interaction.request.rotation.visibilty.lookAt
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.pathing.Path
import com.lambda.pathing.Pathing.findPathAStar
import com.lambda.pathing.Pathing.thetaStarClearance
import com.lambda.pathing.PathingConfig
import com.lambda.pathing.PathingSettings
import com.lambda.pathing.dstar.DStarLite
import com.lambda.pathing.dstar.LazyGraph
import com.lambda.pathing.goal.SimpleGoal
import com.lambda.pathing.move.MoveFinder.moveOptions
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.util.Communication.info
import com.lambda.util.Formatting.string
import com.lambda.util.math.setAlpha
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.mergeFrom
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toFastVec
import net.minecraft.util.math.Box
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
    enum class Page {
        Pathing, Rotation
    }

    private val page by setting("Page", Page.Pathing)
    private val pathing = PathingSettings(this) { page == Page.Pathing }
    private val rotation = RotationSettings(this) { page == Page.Rotation }

    private val target = fastVectorOf(0, 91, -4)
    private var coarsePath = Path()
    private var refinedPath = Path()
    private var currentTarget: Vec3d? = null
    private var integralError = Vec3d.ZERO
    private var lastError = Vec3d.ZERO
    private var calculating = false

    init {
        onEnable {
            integralError = Vec3d.ZERO
            lastError = Vec3d.ZERO
            calculating = false
            coarsePath = Path()
            refinedPath = Path()
            currentTarget = null
        }

        listen<TickEvent.Pre> {
            updateTargetNode()

            if (calculating) return@listen

            updatePaths()
        }

        listen<RotationEvent.StrafeInput> { event ->
            currentTarget?.let { target ->
                event.strafeYaw = player.eyePos.rotationTo(target).yaw
                val adjustment = calculatePID(target)
                val yawRad = Math.toRadians(event.strafeYaw)
                val forward = -sin(yawRad)
                val strafe = cos(yawRad)

                val forwardComponent = adjustment.x * forward + adjustment.z * strafe
//                val strafeComponent = adjustment.x * strafe - adjustment.z * forward

                val moveInput = buildMovementInput(
                    forward = forwardComponent,
                    strafe = 0.0/*strafeComponent*/,
                    jump = player.isOnGround && adjustment.y > 0.5
                )
                event.input.mergeFrom(moveInput)
            }
        }

        onRotate {
            val currentTarget = currentTarget ?: return@onRotate
            val part = player.eyePos.rotationTo(currentTarget)
            val targetRotation = Rotation(part.yaw, player.pitch.toDouble())

            lookAt(targetRotation).requestBy(rotation)
        }

        listen<MovementEvent.Sprint> {
            if (refinedPath.moves.isEmpty()) return@listen

            player.isSprinting = pathing.allowSprint
            it.sprint = pathing.allowSprint
        }

        listen<RenderEvent.StaticESP> { event ->
//            longPath.render(event.renderer, Color.YELLOW)
            refinedPath.render(event.renderer, Color.GREEN)
            event.renderer.buildFilled(Box(target.toBlockPos()), Color.PINK.setAlpha(0.25))
        }
    }

    private fun SafeContext.updateTargetNode() {
        refinedPath.moves.firstOrNull()?.let { current ->
            if (player.pos.distanceTo(current.bottomPos) < pathing.tolerance) {
                refinedPath.moves.removeFirst()
                integralError = Vec3d.ZERO
            }
            currentTarget = refinedPath.moves.firstOrNull()?.bottomPos
        } ?: run {
            currentTarget = null
        }
    }

    private fun SafeContext.updatePaths() {
        val goal = SimpleGoal(target)
        when (pathing.algorithm) {
            PathingConfig.PathingAlgorithm.A_STAR -> {
                runConcurrent {
                    calculating = true
                    val long: Path
                    val aStar = measureTimeMillis {
                        long = findPathAStar(player.blockPos.toFastVec(), goal, pathing)
                    }
                    val short: Path
                    val thetaStar = measureTimeMillis {
                        short = if (pathing.pathRefining) {
                            thetaStarClearance(long, pathing)
                        } else long
                    }
                    info("A* (Length: ${long.length().string} Nodes: ${long.size} T: $aStar ms) and Theta* (Length: ${short.length().string} Nodes: ${short.size} T: $thetaStar ms)")
                    println("Long: $long | Short: $short")
                    short.moves.removeFirstOrNull()
                    coarsePath = long
                    refinedPath = short
                    //            calculating = false
                }
            }
            PathingConfig.PathingAlgorithm.D_STAR_LITE -> {
                runConcurrent {

                }
            }
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