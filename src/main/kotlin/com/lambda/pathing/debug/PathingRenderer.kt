package com.lambda.pathing.debug

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.pathing.PathingManager
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.center
import com.lambda.pathing.trajectory.PublishedPath
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import java.awt.Color
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

object PathingRenderer : Loadable {
    private val config get() = PathingManager.renderConfig

    init {
        immediateRenderer("Pathing Debug", depthTest = { PathingManager.renderConfig.depthTest }) {
            if (!config.enabled) return@immediateRenderer
            if (config.renderGraph) renderSearchGraph()
            if (config.renderPlanning) renderPlanningDebug()
            val path = PathingManager.published ?: return@immediateRenderer
            if (config.renderCoarseRoute) renderCoarseRoute(path.route)
            if (config.renderTrajectory) renderTrajectory(path.plan)
            if (config.renderTrail) renderLiveTrail()
            if (config.renderLabels) renderLabels(path)
        }
    }

    private fun RenderBuilder.renderSearchGraph() {
        val sample = PlanningDebugChannel.graph ?: return
        if (sample.nodes.isEmpty()) return

        val span = sample.dearest - sample.cheapest
        val size = config.graphNodeSize

        fun shade(cost: Double): Color = when {
            !cost.isFinite() -> config.graphUnreachableColor
            span <= 0.0 -> config.graphNearColor
            else -> lerp((cost - sample.cheapest) / span, config.graphNearColor, config.graphFarColor)
        }

        if (config.renderGraphEdges) {
            val width = screenWidth(maxOf(config.graphEdgeWidth, 1))
            val faint = screenWidth(maxOf(config.graphEdgeWidth / 2, 1))
            sample.edges.forEach { edge ->
                if (!edge.policy) {
                    if (!config.renderGraphAllEdges) return@forEach
                    line(
                        edge.from.add(0.0, EDGE_Y, 0.0),
                        edge.to.add(0.0, EDGE_Y, 0.0),
                        config.graphEdgeColor,
                        faint,
                    )
                    return@forEach
                }

                lineGradient(
                    edge.from.add(0.0, EDGE_Y, 0.0), shade(edge.fromCost).setAlpha(EDGE_TAIL_ALPHA),
                    edge.to.add(0.0, EDGE_Y, 0.0), shade(edge.toCost),
                    width,
                )
            }
        }

        sample.nodes.forEach { node ->
            val frontier = node.frontier && config.renderGraphFrontier
            val color = when {
                frontier -> config.graphFrontierColor
                node.anchor -> config.graphAnchorColor
                else -> shade(node.cost)
            }
            cell(
                node.pos.add(0.0, GRAPH_Y, 0.0),
                if (frontier) size * FRONTIER_SCALE else size,
                color,
                outlined = frontier || node.anchor,
            )
        }

        if (config.renderLabels) {
            val frontier = sample.nodes.count { it.frontier }
            val unreachable = sample.nodes.count { !it.cost.isFinite() }
            val edges = if (config.renderGraphEdges) {
                "  edges %d/%d".format(sample.edges.size, sample.totalEdges)
            } else ""
            worldText(
                "graph %d cells  %d drawn%s  frontier %d  unreachable %d  cost %.0f..%.0f ticks".format(
                    sample.total, sample.nodes.size, edges,
                    frontier, unreachable, sample.cheapest, sample.dearest,
                ),
                sample.nodes.first().pos.add(0.0, 1.6, 0.0),
                size = config.labelSize.toFloat(),
                style = RenderBuilder.SDFStyle(
                    color = config.textColor,
                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
                    shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 160)),
                ),
            )
        }
    }

    private fun RenderBuilder.renderPlanningDebug() {
        val published = PathingManager.published
        PlanningDebugChannel.coarseRoute?.let { route ->

            if (published == null || route !== published.route) renderCoarseRoute(route)
        }

        val refining = published != null
        val width = screenWidth(maxOf(config.trajectoryWidth / 2, 1))
        PlanningDebugChannel.attempts.forEach { attempt ->
            if (attempt.points.size < 2) return@forEach
            val color = if (attempt.certified) config.trajectoryColor else config.rejectColor
            val alpha = when {
                attempt.certified -> if (refining) 0.40 else 0.65
                else -> if (refining) 0.15 else 0.30
            }
            polyline(attempt.points.map { it.add(0.0, TRAJECTORY_Y, 0.0) }, color.setAlpha(alpha), width)
        }

        if (config.renderCandidates) {
            PlanningDebugChannel.candidateLines.forEach { candidate ->
                if (candidate.points.size < 2) return@forEach
                polyline(
                    candidate.points.map { it.add(0.0, TRAJECTORY_Y + 0.08, 0.0) },
                    if (candidate.best) config.bestCandidateColor
                    else config.candidateColor.setAlpha(0.35),
                    screenWidth(if (candidate.best) maxOf(config.trajectoryWidth / 2, 1) else 8),
                )
            }
        }
    }

    private fun edgeColor(movement: MovementId) = when (movement) {
        MovementId.WALK -> config.walkColor
        MovementId.STEP_UP -> config.stepUpColor
        MovementId.WALK_OFF -> config.walkOffColor
        MovementId.DROP -> config.dropColor
        MovementId.JUMP -> config.jumpCandidateColor
        else -> config.unknownMovementColor
    }

    private fun RenderBuilder.renderCoarseRoute(route: CoarseRoutePlan) {
        route.edges.forEach { edge ->
            val color = edgeColor(edge.movement)
            line(edge.from.center(COARSE_Y), edge.to.center(COARSE_Y), color, screenWidth(config.coarseWidth))
        }

        route.nodes.forEach { node -> marker(node.center(COARSE_Y), 0.14, config.nodeColor) }
    }

    private fun RenderBuilder.renderTrajectory(plan: TrajectoryPlan) {
        val points = buildList {
            add(plan.initialState.position.add(0.0, TRAJECTORY_Y, 0.0))
            plan.frames.forEach { add(it.state.position.add(0.0, TRAJECTORY_Y, 0.0)) }
        }

        val walked = (PathingManager.status as? PathingManager.Status.Executing)?.frame
        if (walked != null && walked in 1 until points.size) {
            polyline(points.take(walked + 1), config.trailColor, screenWidth(config.trajectoryWidth))
            polyline(points.drop(walked), config.trajectoryColor, screenWidth(config.trajectoryWidth))
        } else {
            polyline(points, config.trajectoryColor, screenWidth(config.trajectoryWidth))
        }

        if (config.renderJumpMarkers) {
            plan.frames.forEachIndexed { index, frame ->
                if (plan.tape[index].jump) {
                    marker(frame.state.position.add(0.0, TRAJECTORY_Y, 0.0), 0.20, config.jumpColor)
                }
            }
        }

        val committedEnd = PathingManager.published?.spliceFrames?.lastOrNull()
        if (config.renderSplices && committedEnd != null && committedEnd < points.size - 1) {
            polyline(
                points.subList(committedEnd, points.size),
                config.stopColor.setAlpha(0.45),
                screenWidth(maxOf(config.trajectoryWidth / 2, 1)),
            )
        }

        if (config.renderSplices) {
            PathingManager.published?.spliceFrames?.forEach { frame ->
                plan.frames.getOrNull(frame - 1)?.let { at ->
                    marker(at.state.position.add(0.0, TRAJECTORY_Y + 0.22, 0.0), 0.13, config.spliceColor)
                }
            }
        }

        plan.frames.lastOrNull()?.let { last ->
            marker(last.state.position.add(0.0, TRAJECTORY_Y, 0.0), 0.28, config.stopColor)
        }

        val path = PathingManager.published
        if (path != null && path.partial) {
            plan.frames.lastOrNull()?.let { last ->
                marker(last.state.position.add(0.0, TRAJECTORY_Y + 0.35, 0.0), 0.34, config.rejectColor)
            }
        }
    }

    private fun RenderBuilder.renderLiveTrail() {
        val trail = PathingManager.liveTrail
        if (trail.size < 2) return
        polyline(trail.map { it.add(0.0, LIVE_Y, 0.0) }, config.trailColor, screenWidth(config.trailWidth))
    }

    private fun RenderBuilder.renderLabels(path: PublishedPath) {
        val plan = path.plan
        val anchor = plan.initialState.position.add(0.0, 1.2, 0.0)
        val parameters = path.parameters

        val lines = listOf(
            "frames %d  route %d nodes  deps %d  controls %d%s".format(
                plan.tape.frameCount,
                path.route.nodes.size,
                plan.dependencies.size,
                path.controlSegments,
                path.spliceFrames.takeIf { it.isNotEmpty() }?.let { "  splices=${it.joinToString()}" }.orEmpty(),
            ),
            "sprint=%s look=%d brake=%.2f%s".format(
                parameters.sprint,
                parameters.lookAheadNodes,
                parameters.brakeDistance,
                parameters.stepUpJumpLeadDistance?.let { "  jumpLead=%.2f".format(it) }.orEmpty(),
            ),
            "coarse lower bound %.1f ticks  |  search %d attempts in %d ms".format(
                path.route.lowerBoundTicks, path.attempts, path.planMillis,
            ),
            statusLabel(),
        )

        lines.forEachIndexed { index, text ->
            worldText(
                text,
                anchor.add(0.0, (lines.size - index) * 0.26, 0.0),
                size = config.labelSize.toFloat(),
                style = RenderBuilder.SDFStyle(
                    color = if (index == lines.lastIndex) statusColor() else config.textColor,
                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
                    shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 160)),
                ),
            )
        }
    }

    private fun statusLabel(): String = when (val status = PathingManager.status) {
        is PathingManager.Status.Idle -> "idle"
        is PathingManager.Status.Settling -> "settling ${status.goal}"
        is PathingManager.Status.Planning -> "planning ${status.goal}"
        is PathingManager.Status.Aligning ->
            "aligning trajectory  yaw error %.1f°".format(status.yawError)
        is PathingManager.Status.Executing -> {
            val path = PathingManager.published
            val kind = if (path?.partial == true) "safe partial" else "full"
            val improvements = PathingManager.adopted.takeIf { it > 0 }
                ?.let { "  improved x$it" }.orEmpty()
            val late = PathingManager.rejectedImprovements.takeIf { it > 0 }
                ?.let { "  late x$it" }.orEmpty()
            "walking %s tape  %d/%d  dev %.2e%s%s".format(
                kind, status.frame, status.frames, PathingManager.maxDeviation, improvements, late,
            )
        }

        is PathingManager.Status.Complete ->
            "complete: %d frames, max deviation %.2e".format(status.frames, PathingManager.maxDeviation)

        is PathingManager.Status.Failed -> "failed: ${status.reason}"
    }

    private fun statusColor(): Color = when (PathingManager.status) {
        is PathingManager.Status.Executing -> config.trailColor
        is PathingManager.Status.Complete -> config.stopColor
        is PathingManager.Status.Failed -> config.rejectColor
        else -> config.textColor
    }

    private fun RenderBuilder.cell(pos: Vec3d, size: Double, color: Color, outlined: Boolean) {
        val half = size * 0.5
        val x = pos.x
        val y = pos.y
        val z = pos.z
        filledQuad(
            Vec3d(x - half, y, z - half),
            Vec3d(x - half, y, z + half),
            Vec3d(x + half, y, z + half),
            Vec3d(x + half, y, z - half),
            color.setAlpha(CELL_FILL_ALPHA),
        )
        if (!outlined) return
        polyline(
            listOf(
                Vec3d(x - half, y, z - half),
                Vec3d(x - half, y, z + half),
                Vec3d(x + half, y, z + half),
                Vec3d(x + half, y, z - half),
                Vec3d(x - half, y, z - half),
            ),
            color,
            screenWidth(6),
        )
    }

    private fun RenderBuilder.marker(pos: Vec3d, size: Double, color: Color) {
        box(Box.of(pos, size, size * 0.35, size)) {
            colors(color.setAlpha(0.25), color)
            lineWidth(screenWidth(10))
        }
    }

    private fun screenWidth(pixels: Int): Float = -pixels * 0.00005f

    private fun Stance.center(yOffset: Double): Vec3d {
        val world = mc.world
        val support = world?.let {
            val pos = BlockPos(x, y - 1, z)
            SnapshotSimulationEnvironment.coarseVoxelOf(it.getBlockState(pos).getCollisionShape(it, pos))
        }
        return Vec3d(x + 0.5, y + (support?.surfaceOffset ?: 0.0) + yOffset, z + 0.5)
    }

    private const val GRAPH_Y = 0.02

    private const val CELL_FILL_ALPHA = 0.42

    private const val FRONTIER_SCALE = 1.6

    private const val EDGE_Y = 0.04

    private const val EDGE_TAIL_ALPHA = 0.10

    private const val COARSE_Y = 0.06
    private const val TRAJECTORY_Y = 0.10
    private const val LIVE_Y = 0.14
}
