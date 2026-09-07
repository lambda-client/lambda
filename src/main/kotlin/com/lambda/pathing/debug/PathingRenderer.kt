package com.lambda.pathing.debug

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.search.PlanGraph
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.SearchNodeRole
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import java.awt.Color
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

object PathingRenderer : Loadable {
    private val config get() = PathingService.renderConfig

    /** One snapshot per frame: every read below sees the same instant of the walk. */
    private var telemetry: Telemetry = Telemetry.EMPTY
    private var status: State = State.Idle

    init {
        immediateRenderer("Pathing Debug", depthTest = { PathingService.renderConfig.depthTest }) {
            PlanningDebugChannel.treeWanted = config.enabled && config.renderSearchTree
            if (!config.enabled) return@immediateRenderer
            telemetry = PathingService.telemetry
            status = PathingService.status
            if (config.renderGraph) renderSearchGraph()
            if (config.renderSearchTree) renderSearchTree()
            if (config.renderPlanning) renderPlanningDebug()
            if (config.renderSearchStats) renderSearchStats()
            val path = telemetry.published ?: return@immediateRenderer
            if (config.renderCoarseRoute) renderCoarseRoute(path.route)
            if (config.renderTrajectory) renderTrajectory(path.plan)
            if (config.renderPlanGraph) renderPlanGraph(path.plan)
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

    private fun roleColor(role: SearchNodeRole): Color = when (role) {
        SearchNodeRole.SPINE -> config.spineColor
        SearchNodeRole.BEST -> config.treeBestColor
        SearchNodeRole.OPEN -> config.treeOpenColor
        SearchNodeRole.PARKED -> config.treeParkedColor
        SearchNodeRole.SPENT -> config.treeSpentColor
        SearchNodeRole.INTERIOR -> config.treeInteriorColor
    }

    /** The anchor tree, drawn in [DRAW_ORDER] so the spine is never buried; width ranks like colour. */
    private fun RenderBuilder.renderSearchTree() {
        val tree = PlanningDebugChannel.tree ?: return
        if (tree.edges.isEmpty() && tree.nodes.isEmpty()) return

        val base = maxOf(config.searchTreeWidth, 1)
        fun width(role: SearchNodeRole) = screenWidth(
            when (role) {
                SearchNodeRole.SPINE -> base * 2
                SearchNodeRole.BEST -> (base * 3) / 2
                SearchNodeRole.OPEN -> base
                else -> maxOf(base / 2, 1)
            },
        )

        DRAW_ORDER.forEach { role ->
            val color = roleColor(role)
            tree.edges.forEach { edge ->
                if (edge.role != role) return@forEach
                line(
                    edge.from.add(0.0, TREE_Y, 0.0),
                    edge.to.add(0.0, TREE_Y, 0.0),
                    color,
                    width(role),
                )
            }
        }

        if (config.renderSearchTreeNodes) {
            val size = config.searchTreeNodeSize
            tree.nodes.forEach { node ->
                if (node.role == SearchNodeRole.INTERIOR) return@forEach
                marker(
                    node.position.add(0.0, TREE_Y, 0.0),
                    if (node.role == SearchNodeRole.SPINE) size * 1.8 else size,
                    roleColor(node.role),
                )
            }
        }

        if (config.renderLabels) {
            val counts = tree.nodes.groupingBy { it.role }.eachCount()
            worldText(
                "tree %d anchors drawn%s of %d admitted  |  %s".format(
                    tree.nodes.size,
                    if (tree.truncated) " (capped)" else "",
                    tree.totalAnchors,
                    DRAW_ORDER.reversed().mapNotNull { role ->
                        counts[role]?.let { "${role.name.lowercase()} $it" }
                    }.joinToString("  "),
                ),
                (PlanningDebugChannel.tree?.nodes?.firstOrNull()?.position ?: return)
                    .add(0.0, 2.0, 0.0),
                size = config.labelSize.toFloat(),
                style = RenderBuilder.SDFStyle(
                    color = config.textColor,
                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
                    shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 160)),
                ),
            )
        }
    }

    /** Live search counters at the body; runway (certified frames ahead of the cursor) turns red when starving. */
    private fun RenderBuilder.renderSearchStats() {
        val stats = PlanningDebugChannel.stats ?: return
        val origin = mc.player?.pos ?: return

        val runway = if (stats.publishedFrame >= 0 && stats.cursorFrame >= 0) {
            stats.publishedFrame - stats.cursorFrame
        } else null
        val mergeRate = if (stats.admitted > 0) 100.0 * stats.merged / stats.admitted else 0.0

        val lines = listOf(
            "expansions %,d   window %,d/%,d   %s".format(
                stats.expansions, stats.windowExpansions, stats.windowBudget,
                stats.bestScore?.let { "best %d".format(it) } ?: "no incumbent",
            ),
            "temp %.2f   guide expansions %d   restarts %d".format(
                stats.temperature, stats.guideExpansions, stats.restarts,
            ),
            "open %d   parked %d   blocked %d   spent %d".format(
                stats.open, stats.parked, stats.blocked, stats.spent,
            ),
            "beam %,d admitted   %,d merged (%.0f%%)".format(stats.admitted, stats.merged, mergeRate),
            "cursor %d   published %d   root %d   horizon %s%s".format(
                stats.cursorFrame, stats.publishedFrame, stats.rootFrame,
                if (stats.horizonEnd == Int.MAX_VALUE) "-" else stats.horizonEnd.toString(),
                runway?.let { "   runway %d".format(it) }.orEmpty(),
            ),
        )

        lines.forEachIndexed { index, text ->
            worldText(
                text,
                origin.add(0.0, STATS_Y + (lines.size - index) * 0.26, 0.0),
                size = config.labelSize.toFloat(),
                style = RenderBuilder.SDFStyle(
                    color = if (runway != null && runway < STARVING_RUNWAY_FRAMES) config.rejectColor
                    else config.textColor,
                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
                    shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 160)),
                ),
            )
        }
    }

    private fun RenderBuilder.renderPlanningDebug() {
        val published = telemetry.published
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

        val walked = (status as? State.Executing)?.frame
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

        val committedEnd = telemetry.published?.spliceFrames?.lastOrNull()
        if (config.renderSplices && committedEnd != null && committedEnd < points.size - 1) {
            polyline(
                points.subList(committedEnd, points.size),
                config.stopColor.setAlpha(0.45),
                screenWidth(maxOf(config.trajectoryWidth / 2, 1)),
            )
        }

        if (config.renderSplices) {
            telemetry.published?.spliceFrames?.forEach { frame ->
                plan.frames.getOrNull(frame - 1)?.let { at ->
                    marker(at.state.position.add(0.0, TRAJECTORY_Y + 0.22, 0.0), 0.13, config.spliceColor)
                }
            }
        }

        plan.frames.lastOrNull()?.let { last ->
            marker(last.state.position.add(0.0, TRAJECTORY_Y, 0.0), 0.28, config.stopColor)
        }

        val path = telemetry.published
        if (path != null && path.partial) {
            plan.frames.lastOrNull()?.let { last ->
                marker(last.state.position.add(0.0, TRAJECTORY_Y + 0.35, 0.0), 0.34, config.rejectColor)
            }
        }
    }

    /**
     * The certified plan as its junction graph: one run per decision, shaded by frames per
     * block between [FRAMES_PER_BLOCK_IDEAL] and [FRAMES_PER_BLOCK_WORST]; junctions mark
     * where a shortcut may cut in.
     */
    private fun RenderBuilder.renderPlanGraph(plan: TrajectoryPlan) {
        val graph = PlanGraph.of(plan) ?: return
        val width = screenWidth(maxOf(config.trajectoryWidth, 1))

        graph.spine.forEachIndexed { index, segment ->
            val points = ArrayList<Vec3d>(segment.frameCount + 1)
            points += segment.entry.position.add(0.0, GRAPH_LANE_Y, 0.0)
            for (frame in segment.startFrame until segment.endFrame) {
                plan.frames.getOrNull(frame)?.let { points += it.state.position.add(0.0, GRAPH_LANE_Y, 0.0) }
            }
            if (points.size < 2) return@forEachIndexed

            val blocks = segment.entry.position.distanceTo(segment.exit.position)
            val perBlock = if (blocks > 0.05) segment.frameCount / blocks else FRAMES_PER_BLOCK_WORST
            val heat = ((perBlock - FRAMES_PER_BLOCK_IDEAL) /
                (FRAMES_PER_BLOCK_WORST - FRAMES_PER_BLOCK_IDEAL)).coerceIn(0.0, 1.0)
            polyline(points, lerp(heat, config.segmentFastColor, config.segmentSlowColor), width)
        }

        graph.alternates.forEach { alternate ->
            alternate.segments.forEach { segment ->
                line(
                    segment.entry.position.add(0.0, GRAPH_LANE_Y + 0.06, 0.0),
                    segment.exit.position.add(0.0, GRAPH_LANE_Y + 0.06, 0.0),
                    config.alternateColor,
                    width,
                )
            }
        }

        graph.junctions.forEach { junction ->
            marker(
                junction.state.position.add(0.0, GRAPH_LANE_Y, 0.0),
                if (junction.settled) config.junctionSize else config.junctionSize * 0.7,
                if (junction.settled) config.junctionColor else config.unsettledJunctionColor,
            )
        }

        if (!config.renderPlanGraphLabels) return

        val worst = graph.improvementTargets().take(WORST_SPANS_SHOWN)
        val flagged = worst.map { it.from }.toSet()
        graph.junctions.forEach { junction ->
            if (junction.index % JUNCTION_LABEL_STRIDE != 0 && junction.index !in flagged) return@forEach
            worldText(
                "J%d".format(junction.index),
                junction.state.position.add(0.0, GRAPH_LANE_Y + 0.45, 0.0),
                size = (config.labelSize * 0.8).toFloat(),
                style = RenderBuilder.SDFStyle(
                    color = if (junction.settled) config.junctionColor else config.unsettledJunctionColor,
                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
                ),
            )
        }
        worst.forEach { span ->
            val at = graph.junctions[span.from].state.position
            worldText(
                "J%d..J%d  %d frames  %.1f f/block".format(
                    span.from, span.to, span.frames, span.framesPerBlock,
                ),
                at.add(0.0, GRAPH_LANE_Y + 0.75, 0.0),
                size = (config.labelSize * 0.9).toFloat(),
                style = RenderBuilder.SDFStyle(
                    color = config.segmentSlowColor,
                    outline = RenderBuilder.SDFOutline(Color(0, 0, 0, 220), 0.12f),
                    shadow = RenderBuilder.SDFShadow(Color(0, 0, 0, 160)),
                ),
            )
        }
    }

    private fun RenderBuilder.renderLiveTrail() {
        val trail = telemetry.trail
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

    private fun statusLabel(): String = when (val status = status) {
        is State.Idle -> "idle"
        is State.Settling -> "settling ${status.goal}"
        is State.Planning -> "planning ${status.goal}"
        is State.Aligning ->
            "aligning trajectory  yaw error %.1f°".format(status.yawError)
        is State.Executing -> {
            val path = telemetry.published
            val kind = if (path?.partial == true) "safe partial" else "full"
            val improvements = telemetry.adopted.takeIf { it > 0 }
                ?.let { "  improved x$it" }.orEmpty()
            val late = telemetry.rejectedImprovements.takeIf { it > 0 }
                ?.let { "  late x$it" }.orEmpty()
            "walking %s tape  %d/%d  dev %.2e%s%s".format(
                kind, status.frame, status.frames, telemetry.maxDeviation, improvements, late,
            )
        }

        is State.Complete ->
            "complete: %d frames, max deviation %.2e".format(status.frames, telemetry.maxDeviation)

        is State.Failed -> "failed: ${status.reason}"
    }

    private fun statusColor(): Color = when (status) {
        is State.Executing -> config.trailColor
        is State.Complete -> config.stopColor
        is State.Failed -> config.rejectColor
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
            BlockPhysicsCapture.coarseVoxelOf(it.getBlockState(pos).getCollisionShape(it, pos))
        }
        return Vec3d(x + 0.5, y + (support?.surfaceOffset ?: 0.0) + yOffset, z + 0.5)
    }

    /** Weakest claim first: the spine is drawn last so nothing covers it. */
    private val DRAW_ORDER = listOf(
        SearchNodeRole.INTERIOR,
        SearchNodeRole.SPENT,
        SearchNodeRole.PARKED,
        SearchNodeRole.OPEN,
        SearchNodeRole.BEST,
        SearchNodeRole.SPINE,
    )

    /** Certified frames left in front of the cursor before a walk is about to stand still. */
    private const val STARVING_RUNWAY_FRAMES = 20

    /** Frames per block at a clean sprint, and where a stretch reads as wasted. */
    private const val FRAMES_PER_BLOCK_IDEAL = 3.6
    private const val FRAMES_PER_BLOCK_WORST = 14.0

    private const val WORST_SPANS_SHOWN = 3
    private const val JUNCTION_LABEL_STRIDE = 5

    private const val GRAPH_LANE_Y = 0.18

    private const val TREE_Y = 0.08

    private const val STATS_Y = 1.2

    private const val GRAPH_Y = 0.02

    private const val CELL_FILL_ALPHA = 0.42

    private const val FRONTIER_SCALE = 1.6

    private const val EDGE_Y = 0.04

    private const val EDGE_TAIL_ALPHA = 0.10

    private const val COARSE_Y = 0.06
    private const val TRAJECTORY_Y = 0.10
    private const val LIVE_Y = 0.14
}
