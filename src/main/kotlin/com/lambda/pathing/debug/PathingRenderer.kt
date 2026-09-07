package com.lambda.pathing.debug

import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.graphics.mc.LineDashStyle
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.pathing.api.PathingService
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.search.PlanGraph
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.SearchNodeRole
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.util.math.lerp
import com.lambda.util.math.setAlpha
import java.awt.Color
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * Geometry only; every number lives in the Pathing HUD element.
 *
 * One line language, so the picture reads without a legend:
 *  - solid, thick, mint  : the certified tape the body will follow (grey once walked)
 *  - dashed, movement hue: the coarse skeleton (intent, not yet certified)
 *  - dotted              : optimistic (an anchor tail, an alternate branch)
 *  - thin, faint         : what the search is still weighing (open branches, rejected rollouts)
 *  - diamonds            : discrete decisions (movement changes, junctions, anchors)
 *  - rings               : places (tape end, leg goal, frontier cells)
 */
object PathingRenderer : Loadable {
    private val config get() = PathingService.renderConfig

    /** One snapshot per frame: every read below sees the same instant of the walk. */
    private var telemetry: Telemetry = Telemetry.EMPTY
    private var status: State = State.Idle

    init {
        immediateRenderer("Pathing", depthTest = { PathingService.renderConfig.depthTest }) {
            PlanningDebugChannel.treeWanted = config.enabled && config.renderSearchTree
            PlanningDebugChannel.graphWanted = config.enabled && config.renderGraph
            if (!config.enabled) return@immediateRenderer
            telemetry = PathingService.telemetry
            status = PathingService.status

            // Back to front: field, search, skeleton, plan graph, tape, body trail, goal.
            if (config.renderGraph) renderSearchGraph()
            if (config.renderSearchTree) renderSearchTree()
            if (config.renderPlanning) renderLivePlanning()
            val path = telemetry.published
            if (config.renderCoarseRoute) (path?.route ?: PlanningDebugChannel.coarseRoute)?.let { renderCoarseRoute(it) }
            if (path != null) {
                if (config.renderPlanGraph) renderPlanGraph(path.plan)
                if (config.renderTrajectory) renderTrajectory(path)
            }
            if (config.renderTrail) renderLiveTrail()
            if (config.renderGoal) renderGoal(path)
        }
    }

    // ---- The coarse skeleton -------------------------------------------------------------

    private fun movementColor(movement: MovementId): Color = when (movement) {
        MovementId.WALK -> config.walkColor
        MovementId.STEP_UP -> config.stepUpColor
        MovementId.WALK_OFF -> config.walkOffColor
        MovementId.DROP -> config.dropColor
        MovementId.JUMP -> config.jumpColor
        MovementId.BOUNCE -> config.bounceColor
        MovementId.CLIMB, MovementId.LADDER_CATCH -> config.climbColor
        else -> config.unknownMovementColor
    }

    /**
     * Dashed edges in their movement colour: dashed says "intent", the hue says "how". A
     * diamond marks only where the movement kind changes, so the eye reads "walk, walk,
     * jump, walk" rather than a bead chain. A route cut at an anchor (the ring's edge,
     * unstreamed terrain) ends in a finer dotted tail.
     */
    private fun RenderBuilder.renderCoarseRoute(route: CoarseRoutePlan) {
        val width = px(config.coarseWidth)
        val edges = route.edges
        val truncated = route.goal != telemetry.published?.finalGoal
        edges.forEachIndexed { index, edge ->
            val color = movementColor(edge.movement)
            val from = edge.from.center(COARSE_Y)
            val to = edge.to.center(COARSE_Y)
            val tail = truncated && index == edges.lastIndex
            line(from, to, if (tail) color.scaleAlpha(0.7) else color, width, if (tail) TAIL_DOTS else SKELETON_DASHES)
            val previous = edges.getOrNull(index - 1)
            if (previous == null || previous.movement != edge.movement) {
                diamond(from, MOVEMENT_MARK, color)
            }
        }
    }

    // ---- The certified tape ---------------------------------------------------------------

    private fun RenderBuilder.renderTrajectory(path: PublishedPath) {
        val plan = path.plan
        val points = ArrayList<Vec3d>(plan.frames.size + 1)
        points += plan.initialState.position.add(0.0, TAPE_Y, 0.0)
        plan.frames.forEach { points += it.state.position.add(0.0, TAPE_Y, 0.0) }
        if (points.size < 2) return

        val cursor = (status as? State.Executing)?.frame?.coerceIn(0, points.lastIndex) ?: 0
        val width = px(config.trajectoryWidth)

        // Behind the body: what has been walked, thinner and neutral.
        if (cursor > 0) polyline(points.subList(0, cursor + 1), config.executedColor, px(config.trajectoryWidth / 2))

        // Ahead of the body: full strength at the body, fading toward the tape's end so
        // depth reads at a glance.
        val ahead = points.subList(cursor, points.size)
        val span = (ahead.size - 1).coerceAtLeast(1)
        for (i in 0 until ahead.size - 1) {
            lineGradient(
                ahead[i], config.trajectoryColor.fade(i.toDouble() / span),
                ahead[i + 1], config.trajectoryColor.fade((i + 1).toDouble() / span),
                width,
            )
        }

        if (config.renderJumpMarkers) {
            plan.frames.forEachIndexed { index, frame ->
                if (index >= cursor && plan.tape[index].jump) {
                    tick(frame.state.position.add(0.0, TAPE_Y, 0.0), JUMP_TICK_HEIGHT, config.jumpMarkerColor)
                }
            }
        }

        if (config.renderSplices) {
            path.spliceFrames.forEach { frame ->
                plan.frames.getOrNull(frame - 1)?.let { at ->
                    diamond(at.state.position.add(0.0, TAPE_Y + 0.02, 0.0), SPLICE_MARK, config.spliceColor)
                }
            }
        }

        // The tape's end: a certified stop is one full ring, a partial tape's brake point a
        // dashed double ring (the search is still extending it).
        val end = points.last()
        if (path.partial) {
            ring(end, 0.42, config.partialStopColor, px(RING_WIDTH), SKELETON_DASHES)
            ring(end, 0.26, config.partialStopColor.setAlpha(0.6), px(RING_WIDTH * 2 / 3))
        } else {
            ring(end, 0.38, config.stopColor, px(RING_WIDTH))
        }
    }

    private fun RenderBuilder.renderLiveTrail() {
        val trail = telemetry.trail
        if (trail.size < 2) return
        polyline(trail.map { it.add(0.0, TRAIL_Y, 0.0) }, config.trailColor, px(config.trailWidth))
    }

    /** The final goal: a ring on the ground and a beacon. A truncated route's cut gets a dashed ring. */
    private fun RenderBuilder.renderGoal(path: PublishedPath?) {
        val goal = path?.finalGoal ?: return
        val at = goal.center(GOAL_Y)
        ring(at, 0.5, config.goalColor, px(RING_WIDTH + 4))
        ring(at, 0.34, config.goalColor.setAlpha(0.45), px(RING_WIDTH * 2 / 3))
        lineGradient(at, config.goalColor, at.add(0.0, BEACON_HEIGHT, 0.0), config.goalColor.setAlpha(0.0), px(RING_WIDTH))
        if (path.route.goal != goal) {
            ring(path.route.goal.center(GOAL_Y), 0.3, config.partialStopColor.setAlpha(0.8), px(RING_WIDTH * 2 / 3), SKELETON_DASHES)
        }
    }

    // ---- Live planning --------------------------------------------------------------------

    private fun RenderBuilder.renderLivePlanning() {
        val refining = telemetry.published != null
        val width = px(maxOf(config.trajectoryWidth / 4, 1))
        PlanningDebugChannel.attempts.forEach { attempt ->
            if (attempt.points.size < 2) return@forEach
            val color = if (attempt.certified) config.attemptColor else config.rejectColor.setAlpha(0.3)
            polyline(attempt.points.map { it.add(0.0, ATTEMPT_Y, 0.0) }, if (refining) color.scaleAlpha(0.5) else color, width)
        }
        if (config.renderCandidates) {
            PlanningDebugChannel.candidateLines.forEach { candidate ->
                if (candidate.points.size < 2) return@forEach
                polyline(
                    candidate.points.map { it.add(0.0, ATTEMPT_Y + 0.05, 0.0) },
                    if (candidate.best) config.bestCandidateColor else config.candidateColor,
                    px(if (candidate.best) maxOf(config.trajectoryWidth / 2, 1) else maxOf(config.trajectoryWidth / 5, 1)),
                    if (candidate.best) null else OPTIMISTIC_DOTS,
                )
            }
        }
    }

    // ---- The plan graph -------------------------------------------------------------------

    /**
     * The certified plan as its decision graph: a diamond at every junction a repair or a
     * shortcut may cut at, dotted alternates where the improver found a rival span, and
     * (opt-in) the spans themselves shaded by pace on top of the tape.
     */
    private fun RenderBuilder.renderPlanGraph(plan: TrajectoryPlan) {
        val graph = PlanGraph.of(plan) ?: return
        val width = px(maxOf(config.trajectoryWidth * 2 / 3, 1))

        if (config.renderPlanGraphPace) {
            graph.spine.forEach { segment ->
                val points = ArrayList<Vec3d>(segment.frameCount + 1)
                points += segment.entry.position.add(0.0, PLAN_GRAPH_Y, 0.0)
                for (frame in segment.startFrame until segment.endFrame) {
                    plan.frames.getOrNull(frame)?.let { points += it.state.position.add(0.0, PLAN_GRAPH_Y, 0.0) }
                }
                if (points.size < 2) return@forEach
                val blocks = segment.entry.position.distanceTo(segment.exit.position)
                val perBlock = if (blocks > 0.05) segment.frameCount / blocks else FRAMES_PER_BLOCK_WORST
                val heat = ((perBlock - FRAMES_PER_BLOCK_IDEAL) / (FRAMES_PER_BLOCK_WORST - FRAMES_PER_BLOCK_IDEAL)).coerceIn(0.0, 1.0)
                polyline(points, lerp(heat, config.segmentFastColor, config.segmentSlowColor), width)
            }
        }

        graph.alternates.forEach { alternate ->
            alternate.segments.forEach { segment ->
                line(
                    segment.entry.position.add(0.0, PLAN_GRAPH_Y + 0.05, 0.0),
                    segment.exit.position.add(0.0, PLAN_GRAPH_Y + 0.05, 0.0),
                    config.alternateColor, width, OPTIMISTIC_DOTS,
                )
            }
        }

        graph.junctions.forEach { junction ->
            val color = if (junction.settled) config.junctionColor else config.unsettledJunctionColor
            val size = if (junction.settled) config.junctionSize else config.junctionSize * 0.7
            diamond(junction.state.position.add(0.0, PLAN_GRAPH_Y, 0.0), size, color)
        }
    }

    // ---- The anchor tree ------------------------------------------------------------------

    private fun roleColor(role: SearchNodeRole): Color = when (role) {
        SearchNodeRole.SPINE -> config.spineColor
        SearchNodeRole.BEST -> config.treeBestColor
        SearchNodeRole.OPEN -> config.treeOpenColor
        SearchNodeRole.PARKED -> config.treeParkedColor
        SearchNodeRole.SPENT -> config.treeSpentColor
        SearchNodeRole.INTERIOR -> config.treeInteriorColor
    }

    /**
     * The plan DAG as the search holds it: every branch off the committed tape, drawn along
     * the rollout that produced it (never as a chord between anchors). Weakest claim first
     * so nothing buries the live options: interior scaffolding faint, spent branches thin,
     * parked ones dotted, open ones solid, the best rival dashed. The spine is the tape
     * itself and is drawn only when the tape layer is off.
     */
    private fun RenderBuilder.renderSearchTree() {
        val tree = PlanningDebugChannel.tree ?: return
        val base = maxOf(config.searchTreeWidth, 1)
        fun width(role: SearchNodeRole) = px(
            when (role) {
                SearchNodeRole.SPINE -> base * 2
                SearchNodeRole.BEST -> base * 3 / 2
                SearchNodeRole.OPEN -> base
                SearchNodeRole.PARKED -> maxOf(base * 2 / 3, 1)
                else -> maxOf(base / 2, 1)
            },
        )
        fun dash(role: SearchNodeRole): LineDashStyle? = when (role) {
            SearchNodeRole.BEST -> RIVAL_DASHES
            SearchNodeRole.PARKED -> OPTIMISTIC_DOTS
            else -> null
        }
        TREE_DRAW_ORDER.forEach { role ->
            if (role == SearchNodeRole.SPINE && config.renderTrajectory) return@forEach
            val color = roleColor(role)
            tree.edges.forEach { edge ->
                if (edge.role != role) return@forEach
                if (edge.trace.isEmpty()) {
                    // No rollout behind this edge: a chord is honest only when it is short.
                    if (edge.from.distanceTo(edge.to) <= MAX_CHORD_BLOCKS) {
                        line(edge.from.add(0.0, TREE_Y, 0.0), edge.to.add(0.0, TREE_Y, 0.0), color.scaleAlpha(0.5), width(role), dash(role))
                    }
                    return@forEach
                }
                val points = ArrayList<Vec3d>(edge.trace.size + 2)
                points += edge.from.add(0.0, TREE_Y, 0.0)
                edge.trace.forEach { points += it.add(0.0, TREE_Y, 0.0) }
                points += edge.to.add(0.0, TREE_Y, 0.0)
                polyline(points, color, width(role), dash(role))
            }
        }
        if (config.renderSearchTreeNodes) {
            tree.nodes.forEach { node ->
                when (node.role) {
                    SearchNodeRole.OPEN, SearchNodeRole.BEST -> diamond(node.position.add(0.0, TREE_Y, 0.0), config.searchTreeNodeSize, roleColor(node.role))
                    SearchNodeRole.PARKED -> diamond(node.position.add(0.0, TREE_Y, 0.0), config.searchTreeNodeSize * 0.7, roleColor(node.role))
                    else -> Unit
                }
            }
        }
    }

    // ---- The coarse graph -----------------------------------------------------------------

    /**
     * The lazy graph as what it is: transitions. Every edge D* materialised is a short
     * stroke in its movement colour, faint; the edge the descent takes out of each cell is
     * bright and runs brighter toward the goal, so the flow reads. Cells are not drawn
     * unless asked for (a cost carpet underneath). Frontier cells (still queued) are ringed,
     * optimistic anchors get a diamond and a short beacon.
     */
    private fun RenderBuilder.renderSearchGraph() {
        val sample = PlanningDebugChannel.graph ?: return
        if (sample.nodes.isEmpty()) return
        val span = sample.dearest - sample.cheapest
        fun shade(cost: Double): Color = when {
            !cost.isFinite() -> config.graphUnreachableColor
            span <= 0.0 -> config.graphNearColor
            else -> lerp((cost - sample.cheapest) / span, config.graphNearColor, config.graphFarColor)
        }

        val size = config.graphNodeSize
        if (config.renderGraphCells) {
            sample.nodes.forEach { node ->
                tile(node.pos.add(0.0, GRAPH_Y, 0.0), size, shade(node.cost), if (node.cost.isFinite()) TILE_ALPHA else TILE_ALPHA * 0.6)
            }
        }

        if (config.renderGraphEdges) {
            val base = maxOf(config.graphEdgeWidth, 1)
            val faint = px(maxOf(base / 2, 1))
            val bright = px(base)
            // Explored transitions first, the descent on top so it is never buried.
            sample.edges.forEach { edge ->
                if (edge.policy) return@forEach
                val color = edge.movement?.let(::movementColor) ?: config.unknownMovementColor
                line(
                    edge.from.add(0.0, GRAPH_EDGE_Y, 0.0), edge.to.add(0.0, GRAPH_EDGE_Y, 0.0),
                    color.setAlpha(EXPLORED_EDGE_ALPHA), faint,
                )
            }
            sample.edges.forEach { edge ->
                if (!edge.policy) return@forEach
                val color = edge.movement?.let(::movementColor) ?: config.unknownMovementColor
                lineGradient(
                    edge.from.add(0.0, GRAPH_EDGE_Y + 0.01, 0.0), color.setAlpha(0.35),
                    edge.to.add(0.0, GRAPH_EDGE_Y + 0.01, 0.0), color,
                    bright,
                )
            }
        }

        sample.nodes.forEach { node ->
            val at = node.pos.add(0.0, GRAPH_EDGE_Y + 0.02, 0.0)
            when {
                node.anchor -> {
                    diamond(at, size * 0.7, config.graphAnchorColor)
                    lineGradient(at, config.graphAnchorColor, at.add(0.0, ANCHOR_BEACON_HEIGHT, 0.0), config.graphAnchorColor.setAlpha(0.0), px(RING_WIDTH * 2 / 3))
                }
                node.frontier && config.renderGraphFrontier -> ring(at, size * 0.35, config.graphFrontierColor, px(RING_WIDTH / 2))
            }
        }
    }

    // ---- Primitives -----------------------------------------------------------------------

    /** A flat square on the ground plane. */
    private fun RenderBuilder.tile(pos: Vec3d, size: Double, color: Color, alpha: Double) {
        val h = size * 0.5
        filledQuad(
            Vec3d(pos.x - h, pos.y, pos.z - h), Vec3d(pos.x - h, pos.y, pos.z + h),
            Vec3d(pos.x + h, pos.y, pos.z + h), Vec3d(pos.x + h, pos.y, pos.z - h),
            color.setAlpha(alpha),
        )
    }

    /** A flat diamond: the marker for a discrete decision (movement change, junction, anchor). */
    private fun RenderBuilder.diamond(pos: Vec3d, size: Double, color: Color) {
        val h = size * 0.5
        filledQuad(
            Vec3d(pos.x, pos.y, pos.z - h), Vec3d(pos.x - h, pos.y, pos.z),
            Vec3d(pos.x, pos.y, pos.z + h), Vec3d(pos.x + h, pos.y, pos.z),
            color.setAlpha(0.6),
        )
        polyline(
            listOf(
                Vec3d(pos.x, pos.y, pos.z - h), Vec3d(pos.x - h, pos.y, pos.z),
                Vec3d(pos.x, pos.y, pos.z + h), Vec3d(pos.x + h, pos.y, pos.z), Vec3d(pos.x, pos.y, pos.z - h),
            ),
            color, px(RING_WIDTH / 2),
        )
    }

    /** A horizontal ring on the ground plane: a place. */
    private fun RenderBuilder.ring(pos: Vec3d, radius: Double, color: Color, width: Float, dash: LineDashStyle? = null) =
        circleLine(pos, radius, color, width, segments = 28, dashStyle = dash)

    /** A short vertical stroke: an event at a point along the tape. */
    private fun RenderBuilder.tick(pos: Vec3d, height: Double, color: Color) =
        lineGradient(pos, color, pos.add(0.0, height, 0.0), color.setAlpha(0.15), px(RING_WIDTH * 2 / 3))

    private fun px(pixels: Int): Float = -pixels * 0.00005f

    private fun Color.fade(t: Double): Color = setAlpha((alpha / 255.0) * (1.0 - AHEAD_FADE * t.coerceIn(0.0, 1.0)))

    private fun Color.scaleAlpha(factor: Double): Color = setAlpha((alpha / 255.0) * factor)

    private fun Stance.center(yOffset: Double): Vec3d {
        val world = mc.world
        val support = world?.let {
            val pos = BlockPos(x, y - 1, z)
            BlockPhysicsCapture.coarseVoxelOf(it.getBlockState(pos).getCollisionShape(it, pos))
        }
        return Vec3d(x + 0.5, y + (support?.surfaceOffset ?: 0.0) + yOffset, z + 0.5)
    }

    private val TREE_DRAW_ORDER = listOf(
        SearchNodeRole.INTERIOR, SearchNodeRole.SPENT, SearchNodeRole.PARKED,
        SearchNodeRole.OPEN, SearchNodeRole.BEST, SearchNodeRole.SPINE,
    )

    // The dash vocabulary. Lengths are in blocks.
    private val SKELETON_DASHES = LineDashStyle(dashLength = 0.45f, gapLength = 0.2f)
    private val TAIL_DOTS = LineDashStyle(dashLength = 0.15f, gapLength = 0.2f)
    private val OPTIMISTIC_DOTS = LineDashStyle(dashLength = 0.2f, gapLength = 0.2f)
    private val RIVAL_DASHES = LineDashStyle(dashLength = 0.3f, gapLength = 0.15f)

    /** Frames per block at a clean sprint, and where a span reads as wasted. */
    private const val FRAMES_PER_BLOCK_IDEAL = 3.6
    private const val FRAMES_PER_BLOCK_WORST = 14.0

    /** How much the tape ahead fades toward its end. */
    private const val AHEAD_FADE = 0.5

    private const val TILE_ALPHA = 0.34
    private const val EXPLORED_EDGE_ALPHA = 0.28

    /** A tree edge without a rollout trace is drawn as a chord only up to this length. */
    private const val MAX_CHORD_BLOCKS = 3.0

    private const val RING_WIDTH = 14
    private const val MOVEMENT_MARK = 0.2
    private const val SPLICE_MARK = 0.12
    private const val JUMP_TICK_HEIGHT = 0.4
    private const val BEACON_HEIGHT = 6.0
    private const val ANCHOR_BEACON_HEIGHT = 1.5

    // Draw heights above the ground plane, back to front.
    private const val GRAPH_Y = 0.02
    private const val GRAPH_EDGE_Y = 0.04
    private const val TREE_Y = 0.06
    private const val ATTEMPT_Y = 0.08
    private const val COARSE_Y = 0.07
    private const val PLAN_GRAPH_Y = 0.16
    private const val TAPE_Y = 0.11
    private const val TRAIL_Y = 0.13
    private const val GOAL_Y = 0.03
}
