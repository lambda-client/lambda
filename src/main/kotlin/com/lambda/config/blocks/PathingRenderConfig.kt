/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.config.blocks

import java.awt.Color

/**
 * How a requested walk is drawn.
 *
 * The gap between the trajectory colour (what the simulator predicted) and the trail
 * colour (where the body went) is simulator error, and is the point of the render.
 */
interface PathingRenderConfig {
    val enabled: Boolean
    val depthTest: Boolean

    val renderCoarseRoute: Boolean
    val renderTrajectory: Boolean
    val renderJumpMarkers: Boolean
    val renderSplices: Boolean
    val renderSegmentCost: Boolean
    val renderTrail: Boolean
    val renderLabels: Boolean

    /** Live planning view: the coarse route as it lands, candidates as they are tried. */
    val renderPlanning: Boolean

    /** Screen-space widths, in pixels. */
    val coarseWidth: Int
    val trajectoryWidth: Int
    val trailWidth: Int
    val labelSize: Double

    val walkColor: Color
    val stepUpColor: Color
    val walkOffColor: Color
    val jumpCandidateColor: Color
    val nodeColor: Color
    val trajectoryColor: Color
    val jumpColor: Color
    val spliceColor: Color
    val cutColor: Color
    val costColor: Color
    val stopColor: Color
    val trailColor: Color
    val rejectColor: Color
    val textColor: Color
}
