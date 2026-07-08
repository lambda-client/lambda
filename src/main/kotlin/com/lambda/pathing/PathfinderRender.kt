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

package com.lambda.pathing

import com.lambda.config.blocks.PathfinderRenderConfig
import com.lambda.core.Loadable
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.pathing.execution.PathExecutorDebugState
import com.lambda.pathing.manager.PathfinderExecutor
import com.lambda.pathing.manager.PathfinderManager
import com.lambda.pathing.manager.TraversalHandle
import com.lambda.pathing.refinement.ShortcutAttemptDebug
import com.lambda.util.extension.tickDeltaF
import com.lambda.util.math.setAlpha
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color
import kotlin.collections.plusAssign

object PathfinderRender : Loadable {

    private val renderConfig: PathfinderRenderConfig get() = PathfinderManager.renderConfig

    init {
        immediateRenderer("Pathfinder Path Renderer", depthTest = { renderConfig.depthTest }) {
            if (!renderConfig.enabled) return@immediateRenderer

            val handle = PathfinderManager.activeTraversal ?: return@immediateRenderer

            if (renderConfig.renderLazyGraph) renderLazyGraph()
            if (renderConfig.renderRefinementDebug) renderRefinementDebug(handle)
            if (renderConfig.renderExecutionDebug) renderExecutionDebug()

            if (!renderConfig.renderPath || !handle.status.shouldRender()) return@immediateRenderer

            if (renderConfig.renderCoarsePath && handle.coarsePath != handle.path) {
                val coarsePoints = handle.coarsePath.map { it.toRenderPoint() }
                renderPath(coarsePoints, renderConfig.coarsePathColor, renderConfig.coarsePathColor, spline = false, width = coarsePathLineWidth())
            }

            val controlPoints = handle.path.map { it.toRenderPoint() }

            if (controlPoints.size < 2) return@immediateRenderer

            val startColor = if (handle.status == TraversalHandle.Status.Partial) renderConfig.partialStartColor else renderConfig.readyStartColor
            val endColor = if (handle.status == TraversalHandle.Status.Partial) renderConfig.partialEndColor else renderConfig.readyEndColor
            renderPath(controlPoints, startColor, endColor, spline = renderConfig.useSplines, width = pathLineWidth())

            if (renderConfig.renderPathNodes) {
                renderPathNodes(controlPoints)
            }

            if (renderConfig.renderMarkers) {
                renderMarker(controlPoints.first(), renderConfig.startMarkerColor)
                renderMarker(controlPoints.last(), renderConfig.goalMarkerColor)
            }
        }
    }

    private fun RenderBuilder.renderLazyGraph() {
        val graph = PathfinderManager.lazyGraphSnapshot(renderConfig.maxGraphNodes, renderConfig.maxGraphEdges)

        if (renderConfig.renderGraphEdges) {
            graph.edges.forEach { edge ->
                line(
                    edge.from.toRenderPoint(),
                    edge.to.toRenderPoint(),
                    renderConfig.graphEdgeColor,
                    graphLineWidth(),
                )
            }
        }

        if (renderConfig.renderGraphNodes) {
            graph.nodes.forEach { node ->
                box(Box.of(node.pos.toRenderPoint(), renderConfig.graphNodeSize, renderConfig.graphNodeSize, renderConfig.graphNodeSize)) {
                    colors(renderConfig.graphNodeColor.withAlpha(0.12), renderConfig.graphNodeColor)
                    lineWidth(graphLineWidth())
                }
            }
        }

        if (renderConfig.renderGraphData) {
            graph.nodes
                .take(renderConfig.maxGraphLabels)
                .forEach { node ->
                    val label = node.label()
                    if (label.isNotEmpty()) {
                        worldText(
                            label,
                            node.pos.toRenderPoint().add(0.0, 0.55, 0.0),
                            size = renderConfig.graphTextSize.toFloat(),
                            style = RenderBuilder.SDFStyle(
	                            color = renderConfig.graphDataColor,
	                            outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
	                            shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 160)),
                            ),
                        )
                    }
                }

            if (renderConfig.renderGraphEdgeCost) {
                graph.edges
                    .take(renderConfig.maxGraphLabels)
                    .forEach { edge ->
                        val mid = edge.from.toRenderPoint().add(edge.to.toRenderPoint()).multiply(0.5).add(0.0, 0.18, 0.0)
                        worldText(
                            "%.2f".format(edge.cost),
                            mid,
                            size = (renderConfig.graphTextSize * 0.8).toFloat(),
                            style = RenderBuilder.SDFStyle(
	                            color = renderConfig.graphDataColor.withAlpha(0.78),
	                            outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 200), 0.10f),
	                            shadow = null,
                            ),
                        )
                    }
            }
        }
    }

    private fun RenderBuilder.renderRefinementDebug(handle: TraversalHandle) {
        val attempts = handle.lastRefinementDebug.recentAttempts.takeLast(renderConfig.maxRefinementDebugAttempts)
        attempts.forEach { attempt ->
            val color = when {
                attempt.accepted && renderConfig.renderAcceptedShortcuts -> renderConfig.acceptedShortcutColor
                !attempt.accepted && renderConfig.renderRejectedShortcuts -> renderConfig.rejectedShortcutColor
                else -> null
            } ?: return@forEach

            val from = attempt.from.toRenderPoint().add(0.0, 0.02, 0.0)
            val to = attempt.to.toRenderPoint().add(0.0, 0.02, 0.0)
            line(from, to, color, refinementDebugLineWidth())

            if (renderConfig.renderShortcutReasons) {
                val mid = from.add(to).multiply(0.5).add(0.0, 0.08, 0.0)
                worldText(
                    attempt.shortLabel(),
                    mid,
                    size = 0.18f,
                    style = RenderBuilder.SDFStyle(
	                    color = renderConfig.shortcutReasonColor,
	                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
	                    shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 140)),
                    ),
                )
            }
        }
    }

    private fun RenderBuilder.renderExecutionDebug() {
        val state = PathfinderExecutor.state
        if (!state.active) return
        val playerPos = interpolatedPlayerPos(state)?.add(0.0, 0.04, 0.0)

        if (renderConfig.renderExecutionTrail) {
            val samples = PathfinderExecutor.recentSamples.takeLast(renderConfig.maxExecutionSamples)
            if (samples.size >= 2) {
                val lastIndex = samples.lastIndex.coerceAtLeast(1)
                for (i in 0 until samples.lastIndex) {
                    val alphaScale = (i + 1).toDouble() / lastIndex
                    val color = renderConfig.executionTrailColor.fade(alphaScale)
                    line(
                        samples[i].playerPosition.add(0.0, 0.03, 0.0),
                        samples[i + 1].playerPosition.add(0.0, 0.03, 0.0),
                        color,
                        executionTrailLineWidth(),
                    )
                }
                playerPos?.let {
                    line(
                        samples.last().playerPosition.add(0.0, 0.03, 0.0),
                        it.add(0.0, -0.01, 0.0),
                        renderConfig.executionTrailColor,
                        executionTrailLineWidth(),
                    )
                }
            }
        }

        if (renderConfig.renderActiveSegment) {
            val from = state.segmentStart?.add(0.0, 0.04, 0.0)
            val to = state.segmentEnd?.add(0.0, 0.04, 0.0)
            if (from != null && to != null) {
                line(from, to, renderConfig.executionSegmentColor, executionDebugLineWidth())
            }
        }

        if (renderConfig.renderLookaheadPoint) {
            state.lookaheadPoint?.let { lookahead ->
                box(Box.of(lookahead.add(0.0, 0.04, 0.0), renderConfig.lookaheadPointSize, renderConfig.lookaheadPointSize, renderConfig.lookaheadPointSize)) {
                    colors(renderConfig.lookaheadPointColor.withAlpha(0.16), renderConfig.lookaheadPointColor)
                    lineWidth(executionDebugLineWidth())
                }
            }
        }

        if (renderConfig.renderProjectedPoint) {
            state.projectedPoint?.let { projected ->
                box(Box.of(projected.add(0.0, 0.04, 0.0), renderConfig.projectedPointSize, renderConfig.projectedPointSize, renderConfig.projectedPointSize)) {
                    colors(renderConfig.projectedPointColor.withAlpha(0.16), renderConfig.projectedPointColor)
                    lineWidth(executionDebugLineWidth())
                }
            }
        }

        if (renderConfig.renderProjectionLine) {
            val projected = state.projectedPoint?.add(0.0, 0.04, 0.0)
            if (playerPos != null && projected != null) {
                line(playerPos, projected, renderConfig.projectionLineColor, executionDebugLineWidth())
            }
        }

        if (renderConfig.renderDesiredVector) {
            val lookahead = state.lookaheadPoint?.add(0.0, 0.04, 0.0)
            if (playerPos != null && lookahead != null) {
                line(playerPos, lookahead, renderConfig.desiredVectorColor, executionDebugLineWidth())
            }
        }

        if (renderConfig.renderVelocityVector) {
            val velocity = state.playerVelocity
            if (playerPos != null && velocity != null && velocity.lengthSquared() > 1.0E-6) {
                line(playerPos, playerPos.add(velocity.multiply(renderConfig.executionVectorScale)), renderConfig.velocityVectorColor, executionDebugLineWidth())
            }
        }

        if (renderConfig.renderMoveDeltaVector) {
            val moveDelta = state.moveDelta
            if (playerPos != null && moveDelta != null && moveDelta.lengthSquared() > 1.0E-6) {
                line(playerPos, playerPos.add(moveDelta.multiply(renderConfig.executionVectorScale)), renderConfig.moveDeltaColor, executionDebugLineWidth())
            }
        }

        if (renderConfig.renderExecutionText && playerPos != null) {
            val text = buildString {
                appendLine("${state.status} ${if (state.segmentIndex >= 0) "${state.segmentIndex + 1}/${state.segmentCount}" else "-"} ${state.segmentType ?: ""}".trim())
                appendLine("left ${"%.2f".format(state.remainingDistance)} drift ${"%.2f".format(state.lateralError)} vert ${"%.2f".format(state.verticalError)}")
                appendLine("f ${"%.2f".format(state.commandedForward)} s ${"%.2f".format(state.commandedStrafe)} t ${"%.2f".format(state.throttle)}")
                append("yaw p ${state.playerYaw.formatAngle()} d ${state.desiredYaw.formatAngle()} b ${state.movementBasisYaw.formatAngle()}")
            }
            worldText(
                text,
                playerPos.add(0.0, 0.55, 0.0),
                size = 0.18f,
                style = RenderBuilder.SDFStyle(
	                color = renderConfig.executionTextColor,
	                outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
	                shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 140)),
                ),
            )
        }
    }

    private fun TraversalHandle.Status.shouldRender() = when (this) {
        TraversalHandle.Status.Ready -> true
        TraversalHandle.Status.Partial -> renderConfig.renderPartial
        TraversalHandle.Status.Planning,
        TraversalHandle.Status.Succeeded,
        TraversalHandle.Status.Failed,
        TraversalHandle.Status.Cancelled -> false
    }

    private fun RenderBuilder.renderPath(
	    controlPoints: List<Vec3d>,
	    startColor: Color,
	    endColor: Color,
	    spline: Boolean,
	    width: Float,
    ) {
        val pathPoints = if (spline && controlPoints.size >= 3) {
            controlPoints.tightSplinePoints(renderConfig.splineSegments, renderConfig.splineTension)
        } else {
            controlPoints
        }

        if (pathPoints.size < 2) return

        val lastSegment = (pathPoints.size - 1).coerceAtLeast(1)

        for (i in 0 until pathPoints.lastIndex) {
            lineGradient(
                pathPoints[i],
                colorLerp(i.toDouble() / lastSegment, startColor, endColor),
                pathPoints[i + 1],
                colorLerp((i + 1).toDouble() / lastSegment, startColor, endColor),
                width,
            )
        }
    }

    private fun RenderBuilder.renderMarker(point: Vec3d, color: Color) {
        box(Box.of(point, renderConfig.markerSize, renderConfig.markerHeight, renderConfig.markerSize)) {
            colors(color.withAlpha(0.18), color)
            lineWidth(pathLineWidth())
        }
    }

    private fun RenderBuilder.renderPathNodes(points: List<Vec3d>) {
        points.forEach { point ->
            box(Box.of(point, renderConfig.pathNodeSize, renderConfig.pathNodeSize, renderConfig.pathNodeSize)) {
                colors(renderConfig.pathNodeColor.withAlpha(0.10), renderConfig.pathNodeColor)
                lineWidth(pathNodeLineWidth())
            }
        }
    }

    private fun FastVector.toRenderPoint(): Vec3d = Vec3d.ofBottomCenter(toBlockPos()).add(0.0, renderConfig.yOffset, 0.0)

    private fun interpolatedPlayerPos(state: PathExecutorDebugState): Vec3d? {
        val player = MinecraftClient.getInstance().player ?: return state.playerPosition
        return player.getLerpedPos(MinecraftClient.getInstance().tickDeltaF)
    }

    private fun pathLineWidth() = -renderConfig.screenWidth * 0.00005f

    private fun coarsePathLineWidth() = -renderConfig.coarseScreenWidth * 0.00005f

    private fun pathNodeLineWidth() = -10 * 0.00005f

    private fun graphLineWidth() = -renderConfig.graphScreenWidth * 0.00005f

    private fun refinementDebugLineWidth() = -renderConfig.refinementDebugWidth * 0.00005f

    private fun executionDebugLineWidth() = -renderConfig.executionDebugWidth * 0.00005f

    private fun executionTrailLineWidth() = -renderConfig.executionTrailWidth * 0.00005f

    private fun PathfinderManager.LazyGraphSnapshot.Node.label(): String = buildList {
        if (start) add("START")
        if (goal) add("GOAL")
        if (renderConfig.renderGraphPositions) add(pos.toBlockPos().toShortString())
        if (renderConfig.renderGraphG) add("g=${g.formatCost()}")
        if (renderConfig.renderGraphRhs) add("rhs=${rhs.formatCost()}")
        if (renderConfig.renderGraphKey) add("k=$key")
        if (renderConfig.renderGraphQueue && queued) add("QUEUED")
    }.joinToString("\n")

    private fun Double.formatCost() = if (isInfinite()) "\u221E" else "%.2f".format(this)

    private fun Double?.formatAngle(): String = if (this == null) "-" else "%.1f".format(this)

    private fun Color.fade(alphaScale: Double): Color =
        setAlpha((alpha * alphaScale.coerceIn(0.0, 1.0)).toInt().coerceIn(0, 255))

    private fun Color.withAlpha(alpha: Double): Color =
	    Color(red, green, blue, (alpha.coerceIn(0.0, 1.0) * 255.0).toInt().coerceIn(0, 255))

    private fun Color.withAlpha(alpha: Int): Color =
	    Color(red, green, blue, alpha.coerceIn(0, 255))

    private fun colorLerp(value: Double, start: Color, end: Color): Color {
        val t = value.coerceIn(0.0, 1.0)
        fun channel(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return Color(
	        channel(start.red, end.red),
	        channel(start.green, end.green),
	        channel(start.blue, end.blue),
	        channel(start.alpha, end.alpha),
        )
    }

    private fun ShortcutAttemptDebug.shortLabel(): String =
        "${profile}: ${if (accepted) "ok" else reason}"

    private fun List<Vec3d>.tightSplinePoints(segmentsPerSection: Int, tension: Double): List<Vec3d> {
        if (size < 3) return this

        val segments = segmentsPerSection.coerceAtLeast(1)
        val tangentScale = (1.0 - tension.coerceIn(0.0, 1.0)) * 0.5
        val result = ArrayList<Vec3d>((size - 1) * segments + 1)

        for (index in 0 until lastIndex) {
            val p0 = getOrNull(index - 1) ?: this[index]
            val p1 = this[index]
            val p2 = this[index + 1]
            val p3 = getOrNull(index + 2) ?: p2

            val m1 = p2.subtract(p0).multiply(tangentScale).clampedTo(p1.distanceTo(p2))
            val m2 = p3.subtract(p1).multiply(tangentScale).clampedTo(p1.distanceTo(p2))

            if (index == 0) result += p1
            for (segment in 1..segments) {
                result += cubicHermite(p1, p2, m1, m2, segment.toDouble() / segments)
            }
        }

        return result
    }

    private fun cubicHermite(p1: Vec3d, p2: Vec3d, m1: Vec3d, m2: Vec3d, t: Double): Vec3d {
        val t2 = t * t
        val t3 = t2 * t
        val h00 = 2.0 * t3 - 3.0 * t2 + 1.0
        val h10 = t3 - 2.0 * t2 + t
        val h01 = -2.0 * t3 + 3.0 * t2
        val h11 = t3 - t2

        return Vec3d(
	        h00 * p1.x + h10 * m1.x + h01 * p2.x + h11 * m2.x,
	        h00 * p1.y + h10 * m1.y + h01 * p2.y + h11 * m2.y,
	        h00 * p1.z + h10 * m1.z + h01 * p2.z + h11 * m2.z,
        )
    }

    private fun Vec3d.clampedTo(segmentLength: Double): Vec3d {
        val maxLength = (segmentLength * MAX_TANGENT_SEGMENT_RATIO).coerceAtLeast(0.0)
        val length = length()
        return if (length > maxLength && length > 0.0) normalize().multiply(maxLength) else this
    }

    private const val MAX_TANGENT_SEGMENT_RATIO = 0.35
}
