/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import java.awt.Color

class PathingRenderSettings(override val c: Config) : PathingRenderConfig, ConfigBlock {
    @Group(TOGGLES_GROUP)
    override val enabled by c.setting("Render", true, "Draw the planned route and trajectory.")

    @Group(TOGGLES_GROUP)
    override val depthTest by c.setting("Depth Test", false, "Hide the path behind blocks.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderCoarseRoute by c.setting("Coarse Route", true, "The voxel route D* chose.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderTrajectory by c.setting("Trajectory", true, "The certified, simulated walk.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderJumpMarkers by c.setting("Jump Markers", true, "Grounded ticks where jump is pressed.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderSplices by c.setting(
        "Splice Points", true,
        "Frames where one controller hands over to the next.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderCandidates by c.setting(
        "Candidates", true,
        "The continuations the horizon is choosing between right now: the current best, " +
            "and the other lines still alive.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderTrail by c.setting("Live Trail", true, "Where the body actually went.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderLabels by c.setting("Labels", true, "Frames, dependencies, seed parameters, deviation.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderPlanning by c.setting(
        "Live Planning", true,
        "Draw the coarse route the moment D* converges and every candidate rollout as the search tries it.",
    ) { enabled }

    @Group(WIDTH_GROUP)
    override val coarseWidth by c.setting("Coarse Width", 26, 1..150, 1, unit = " px") { enabled && renderCoarseRoute }

    @Group(WIDTH_GROUP)
    override val trajectoryWidth by c.setting("Trajectory Width", 40, 1..150, 1, unit = " px") { enabled && renderTrajectory }

    @Group(WIDTH_GROUP)
    override val trailWidth by c.setting("Trail Width", 20, 1..150, 1, unit = " px") { enabled && renderTrail }

    @Group(WIDTH_GROUP)
    override val labelSize by c.setting("Label Size", 0.22, 0.05..1.0, 0.01) { enabled && renderLabels }

    @Group(COLOR_GROUP)
    override val walkColor by c.setting("Walk Edge", Color(90, 150, 255, 217)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val stepUpColor by c.setting("Step Up Edge", Color(255, 170, 60, 230)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val walkOffColor by c.setting("Walk Off Edge", Color(190, 120, 255, 230)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val jumpCandidateColor by c.setting("Jump Candidate Edge", Color(255, 80, 80, 230)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val nodeColor by c.setting("Route Node", Color(140, 180, 255, 178)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val trajectoryColor by c.setting("Trajectory", Color(80, 255, 190, 242)) { enabled && renderTrajectory }

    @Group(COLOR_GROUP)
    override val jumpColor by c.setting("Jump Marker", Color(255, 210, 70, 242)) { enabled && renderJumpMarkers }

    @Group(COLOR_GROUP)
    override val spliceColor by c.setting("Splice Point", Color(90, 200, 255, 235)) { enabled && renderSplices }

    @Group(COLOR_GROUP)
    override val bestCandidateColor by c.setting("Best Candidate", Color(255, 255, 140, 240)) { enabled && renderCandidates }

    @Group(COLOR_GROUP)
    override val candidateColor by c.setting("Other Candidates", Color(150, 130, 255, 200)) { enabled && renderCandidates }

    @Group(COLOR_GROUP)
    override val stopColor by c.setting("Certified Stop", Color(120, 255, 120, 242)) { enabled && renderTrajectory }

    @Group(COLOR_GROUP)
    override val trailColor by c.setting("Live Trail", Color(255, 105, 180, 242)) { enabled && renderTrail }

    @Group(COLOR_GROUP)
    override val rejectColor by c.setting("Rejected", Color(255, 80, 80, 255)) { enabled && renderLabels }

    @Group(COLOR_GROUP)
    override val textColor by c.setting("Label Text", Color(230, 230, 230, 255)) { enabled && renderLabels }

    private companion object {
        const val TOGGLES_GROUP = "Toggles"
        const val WIDTH_GROUP = "Widths"
        const val COLOR_GROUP = "Colors"
    }
}
