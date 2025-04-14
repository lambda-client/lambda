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
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.RotationEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.gl.Matrices
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
import com.lambda.pathing.move.NodeType
import com.lambda.pathing.move.TraverseMove
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeConcurrent
import com.lambda.util.Communication.info
import com.lambda.util.Formatting.string
import com.lambda.util.math.setAlpha
import com.lambda.util.player.MovementUtils.buildMovementInput
import com.lambda.util.player.MovementUtils.mergeFrom
import com.lambda.util.world.FastVector
import com.lambda.util.world.WorldUtils.hasSupport
import com.lambda.util.world.WorldUtils.isPathClear
import com.lambda.util.world.dist
import com.lambda.util.world.fastVectorOf
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toFastVec
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import kotlinx.coroutines.delay
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.system.measureTimeMillis

object Pathfinder : Module(
    name = "Pathfinder",
    description = "Get from A to B",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    private val targetPos by setting("Target", BlockPos(0, 78, 0))
    private val pathing = PathingSettings(this)

    private val target: FastVector get() = targetPos.toFastVec()
    val graph = LazyGraph { origin ->
        runSafe {
            moveOptions(origin, ::heuristic, pathing).associate { it.pos to it.cost }
        } ?: emptyMap()
    }
    private val dStar = DStarLite(graph, fastVectorOf(0, 0, 0), target, ::heuristic)
    private var coarsePath = Path()
    private var refinedPath = Path()
    private var currentTarget: Vec3d? = null
    private var integralError = Vec3d.ZERO
    private var lastError = Vec3d.ZERO
    private var needsUpdate = false
    private var currentStart = BlockPos.ORIGIN.toFastVec()

    private fun heuristic(u: FastVector): Double =
        (abs(u.x) + abs(u.y) + abs(u.z)).toDouble()

    private fun heuristic(u: FastVector, v: FastVector): Double =
        (abs(u.x - v.x) + abs(u.y - v.y) + abs(u.z - v.z)).toDouble()

    init {
        onEnable {
            integralError = Vec3d.ZERO
            lastError = Vec3d.ZERO
            coarsePath = Path()
            refinedPath = Path()
            currentTarget = null
            graph.clear()
            dStar.initialize()
            needsUpdate = true
            currentStart = player.blockPos.toFastVec()
            startBackgroundThread()
        }

        onDisable {
            graph.clear()
        }

        listen<TickEvent.Pre> {
            val playerPos = player.blockPos
            val currentPos = playerPos.toFastVec()
            if (player.isOnGround && hasSupport(playerPos) && currentPos dist currentStart > pathing.tolerance) {
                currentStart = currentPos
                needsUpdate = true
            }
            if (pathing.moveAlongPath) updateTargetNode()
//            info("${isPathClear(playerPos, targetPos)}")
        }

//        listen<WorldEvent.BlockUpdate.Client> {
//            val pos = it.pos.toFastVec()
//            graph.markDirty(pos)
//            info("Updated block at ${it.pos} to ${it.newState.block.name.string} rescheduled D*Lite.")
//        }

        listen<RotationEvent.StrafeInput> { event ->
            if (!pathing.moveAlongPath) return@listen

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
            if (!pathing.moveAlongPath) return@onRotate

            val currentTarget = currentTarget ?: return@onRotate
            val part = player.eyePos.rotationTo(currentTarget)
            val targetRotation = Rotation(part.yaw, player.pitch.toDouble())

            lookAt(targetRotation).requestBy(pathing.rotation)
        }

        listen<MovementEvent.Sprint> {
            if (!pathing.moveAlongPath) return@listen
            if (refinedPath.moves.isEmpty()) return@listen

            player.isSprinting = pathing.allowSprint
            it.sprint = pathing.allowSprint
        }

        listen<RenderEvent.StaticESP> { event ->
            if (pathing.renderCoarsePath) coarsePath.render(event.renderer, Color.YELLOW)
            if (pathing.renderRefinedPath) refinedPath.render(event.renderer, Color.GREEN)
            if (pathing.renderGoal) event.renderer.buildFilled(Box(target.toBlockPos()), Color.PINK.setAlpha(0.25))
            if (pathing.renderGraph) graph.render(event.renderer, pathing.maxRenderObjects)
        }

        listen<RenderEvent.World> {
            if (!pathing.renderGraph) return@listen

            Matrices.push {
                val c = mc.gameRenderer.camera.pos.negate()
                translate(c.x, c.y, c.z)
                graph.buildDebugInfoRenderer(pathing.maxRenderObjects)
            }
        }
    }

    private fun startBackgroundThread() {
        runSafeConcurrent {
            while (isEnabled) {
                if (!needsUpdate) {
                    delay(50L)
                    continue
                }
                needsUpdate = false
                updatePaths()
            }
        }
    }

    private fun SafeContext.updateTargetNode() {
        currentTarget = refinedPath.moves.firstOrNull()?.let { current ->
            if (player.pos.distanceTo(current.bottomPos) < pathing.tolerance) {
                refinedPath.moves.removeFirst()
                integralError = Vec3d.ZERO
            }
            refinedPath.moves.firstOrNull()?.bottomPos
        }
    }

    private fun SafeContext.updatePaths() {
        val goal = SimpleGoal(target)
        when (pathing.algorithm) {
            PathingConfig.PathingAlgorithm.A_STAR -> updateAStar(currentStart, goal)
            PathingConfig.PathingAlgorithm.D_STAR_LITE -> updateDStar(currentStart, goal)
        }
    }

    private fun SafeContext.updateAStar(start: FastVector, goal: SimpleGoal) {
        val long: Path
        val aStar = measureTimeMillis {
            long = findPathAStar(start, goal, pathing)
        }
        val short: Path
        val thetaStar = measureTimeMillis {
            short = if (pathing.refinePath) {
                thetaStarClearance(long, pathing)
            } else long
        }
        info("A* (Length: ${long.length().string} Nodes: ${long.size} T: $aStar ms) and \u03b8* (Length: ${short.length().string} Nodes: ${short.size} T: $thetaStar ms)")
//        println("Long: $long | Short: $short")
        coarsePath = long
        refinedPath = short
    }

    private fun SafeContext.updateDStar(start: FastVector, goal: SimpleGoal) {
        val long: Path
        val dStar = measureTimeMillis {
            dStar.updateStart(start)
            dStar.computeShortestPath(pathing.cutoffTimeout)
            val nodes = dStar.path.map { TraverseMove(it, 0.0, NodeType.OPEN, 0.0, 0.0) }
            long = Path(ArrayDeque(nodes))
        }
        val short: Path
        val thetaStar = measureTimeMillis {
            short = if (pathing.refinePath) {
                thetaStarClearance(long, pathing)
            } else long
        }
        info("Lazy D* Lite (Length: ${long.length().string} Nodes: ${long.size} Graph Size: ${graph.size} T: $dStar ms) and \u03b8* (Length: ${short.length().string} Nodes: ${short.size} T: $thetaStar ms)")
//        println("Long: $long | Short: $short")
        coarsePath = long
        refinedPath = short
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