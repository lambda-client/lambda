/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.debug

import com.lambda.core.Loadable
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.pathing.PathingManager
import com.lambda.pathing.coarse.CoarseMoveKind
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.trajectory.TrajectoryPlan
import com.lambda.util.math.setAlpha
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * Read-only view of [PathingManager]. It holds no state and drives nothing.
 *
 * It renders what the planner *believed* (the coarse polyline and the simulated
 * trajectory) against what the body *did* (the live trail). The gap between the
 * green and the pink line is simulator error, and it should stay at float noise.
 */
object PathingRenderer : Loadable {
    /** Live config of whoever requested the current path -- read, never cached. */
    private val config get() = PathingManager.renderConfig

    init {
        immediateRenderer("Pathing Debug", depthTest = { PathingManager.renderConfig.depthTest }) {
            if (!config.enabled) return@immediateRenderer
            if (config.renderPlanning) renderPlanningDebug()
            val path = PathingManager.published ?: return@immediateRenderer
            if (config.renderCoarseRoute) renderCoarseRoute(path.route)
            if (config.renderTrajectory) renderTrajectory(path.plan)
            if (config.renderTrail) renderLiveTrail()
            if (config.renderLabels) renderLabels(path)
        }
    }

    /**
     * The plan as it is being built: the coarse route the instant D* converges (and
     * after every reroute), and the newest candidate rollouts the seed search tried.
     * Candidates that certified a stop are drawn in the trajectory colour, cut ones in
     * the reject colour -- watching where the red ends is watching the search think.
     */
    private fun RenderBuilder.renderPlanningDebug() {
        val published = PathingManager.published
        PlanningDebugChannel.coarseRoute?.let { route ->
            // Once a plan publishes, its own coarse render takes over.
            if (published == null || route !== published.route) renderCoarseRoute(route)
        }

        // Candidates are drawn while refining too, not only before the first plan. That
        // phase is most of a long walk, and hiding it is why an improver making hundreds
        // of attempts looked like it was doing nothing at all. They are dimmed once a tape
        // is running, so the line the body is actually following stays the bright one.
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

        // Where the improver is currently cutting into the tape. Newest brightest, so a
        // glance says both where it is looking now and where it has been looking.
        if (config.renderSplices) {
            val cuts = PlanningDebugChannel.cuts
            cuts.forEachIndexed { index, position ->
                val freshness = (index + 1).toDouble() / cuts.size
                marker(
                    position.add(0.0, TRAJECTORY_Y + 0.45, 0.0),
                    0.10 + 0.10 * freshness,
                    config.cutColor.setAlpha(0.25 + 0.6 * freshness),
                )
            }
        }
    }

    private fun RenderBuilder.renderCoarseRoute(route: CoarseRoutePlan) {
        route.edges.forEach { edge ->
            val color = when (edge.kind) {
                CoarseMoveKind.WALK -> config.walkColor
                CoarseMoveKind.STEP_UP -> config.stepUpColor
                CoarseMoveKind.WALK_OFF -> config.walkOffColor
                CoarseMoveKind.JUMP_CANDIDATE -> config.jumpCandidateColor
            }
            line(edge.from.center(COARSE_Y), edge.to.center(COARSE_Y), color, screenWidth(config.coarseWidth))
        }

        route.nodes.forEach { node -> marker(node.center(COARSE_Y), 0.14, config.nodeColor) }
    }

    /**
     * The certified tape. Where it departs from the coarse line is the lattice
     * error the trajectory layer exists to remove.
     */
    private fun RenderBuilder.renderTrajectory(plan: TrajectoryPlan) {
        val points = buildList {
            add(plan.initialState.position.add(0.0, TRAJECTORY_Y, 0.0))
            plan.frames.forEach { add(it.state.position.add(0.0, TRAJECTORY_Y, 0.0)) }
        }

        // Split the line where the body actually is. Everything behind the cursor is
        // history and can no longer be improved; everything ahead is what refinement is
        // still allowed to replace. Seeing which is which is the whole point of watching
        // an anytime planner work.
        val walked = (PathingManager.status as? PathingManager.Status.Executing)?.frame
        if (walked != null && walked in 1 until points.size) {
            polyline(points.take(walked + 1), config.trailColor, screenWidth(config.trajectoryWidth))
            polyline(points.drop(walked), config.trajectoryColor, screenWidth(config.trajectoryWidth))
        } else {
            polyline(points, config.trajectoryColor, screenWidth(config.trajectoryWidth))
        }

        // Every grounded launch the search chose. A STEP_UP with no marker here was
        // walked, not jumped -- which for a one-block rise means something is wrong.
        if (config.renderJumpMarkers) {
            plan.frames.forEachIndexed { index, frame ->
                if (plan.tape[index].jump) {
                    marker(frame.state.position.add(0.0, TRAJECTORY_Y, 0.0), 0.20, config.jumpColor)
                }
            }
        }

        // Where one controller hands the tape to the next. These are the only frames a
        // refinement or a splice may cut at, so they are the shape of what can still
        // change -- a stretch with no splice markers is a stretch nothing can improve.
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

        // Where a safe partial plan brakes to its certified stop. A tape is always safe
        // run to its end, and this is that end: if refinement never arrives, the body
        // stops here rather than running out of inputs mid-stride.
        val path = PathingManager.published
        if (path != null && path.partial) {
            plan.frames.lastOrNull()?.let { last ->
                marker(last.state.position.add(0.0, TRAJECTORY_Y + 0.35, 0.0), 0.34, config.rejectColor)
            }
        }
    }

    /** Where the body actually went. Overlaps the prediction when the sim is honest. */
    private fun RenderBuilder.renderLiveTrail() {
        val trail = PathingManager.liveTrail
        if (trail.size < 2) return
        polyline(trail.map { it.add(0.0, LIVE_Y, 0.0) }, config.trailColor, screenWidth(config.trailWidth))
    }

    private fun RenderBuilder.renderLabels(path: PathingManager.PublishedPath) {
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
                buildString {
                    parameters.stepUpJumpLeadDistance?.let { append("  jumpLead=%.2f".format(it)) }
                    parameters.gapLaunchFrames.takeIf { it.isNotEmpty() }?.let {
                        append("  gapLaunches=${it.joinToString()}")
                    }
                },
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

    private fun RenderBuilder.marker(pos: Vec3d, size: Double, color: Color) {
        box(Box.of(pos, size, size * 0.35, size)) {
            colors(color.setAlpha(0.25), color)
            lineWidth(screenWidth(10))
        }
    }

    /**
     * Line width is **negative for screen-space** (roughly pixels at 1:20000); a
     * positive value is world-space thickness in *blocks*, which renders a
     * multi-block tube and swallows the whole path.
     */
    private fun screenWidth(pixels: Int): Float = -pixels * 0.00005f

    private fun Stance.center(yOffset: Double) = Vec3d(x + 0.5, y + yOffset, z + 0.5)

    private const val COARSE_Y = 0.06
    private const val TRAJECTORY_Y = 0.10
    private const val LIVE_Y = 0.14
}
