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

package com.lambda.config.blocks

import java.awt.Color

/**
 * How a walk is drawn in the world. Text never appears in the world: every number lives
 * in the Pathing HUD element, the world shows geometry only.
 *
 * Reading the picture: the coarse route is the plan's skeleton (one colour per movement
 * kind), the trajectory is the certified tape (dim behind the body, bright ahead), the
 * trail is where the body actually went. Trail and trajectory separating is simulator error.
 */
interface PathingRenderConfig {
    val enabled: Boolean
    val depthTest: Boolean

    // Layers, in the order a reader needs them.
    val renderCoarseRoute: Boolean
    val renderTrajectory: Boolean
    val renderJumpMarkers: Boolean
    val renderSplices: Boolean
    val renderTrail: Boolean
    val renderGoal: Boolean

    /** Live planning view: the coarse route as it lands and every rollout as the search tries it. */
    val renderPlanning: Boolean
    val renderCandidates: Boolean

    /** The certified plan as its junction graph: spans shaded by pace, junctions where a repair may cut. */
    val renderPlanGraph: Boolean
    val renderPlanGraphPace: Boolean

    /** The trajectory search's live anchor tree: committed spine, best rival, open branches. */
    val renderSearchTree: Boolean
    val renderSearchTreeNodes: Boolean

    /** The coarse search graph: every cell D* touched, shaded by cost to the goal. */
    val renderGraph: Boolean
    val renderGraphFrontier: Boolean
    val renderGraphEdges: Boolean
    val renderGraphCells: Boolean

    /** Screen-space widths, in pixels, and marker sizes in blocks. */
    val coarseWidth: Int
    val trajectoryWidth: Int
    val trailWidth: Int
    val searchTreeWidth: Int
    val graphEdgeWidth: Int
    val graphNodeSize: Double
    val searchTreeNodeSize: Double
    val junctionSize: Double

    /** Budgets for the graph view, per publish; what is drawn against the total is in the HUD. */
    val graphRadius: Double
    val graphCellBudget: Int
    val graphEdgeBudget: Int

    // Movement kinds, shared by the coarse route in the world and the tape profile in the HUD.
    val walkColor: Color
    val stepUpColor: Color
    val walkOffColor: Color
    val dropColor: Color
    val jumpColor: Color
    val bounceColor: Color
    val climbColor: Color
    val unknownMovementColor: Color

    // The tape.
    val trajectoryColor: Color
    val executedColor: Color
    val trailColor: Color
    val jumpMarkerColor: Color
    val spliceColor: Color
    val stopColor: Color
    val partialStopColor: Color
    val goalColor: Color

    // Live planning.
    val attemptColor: Color
    val rejectColor: Color
    val bestCandidateColor: Color
    val candidateColor: Color

    // The coarse graph.
    val graphNearColor: Color
    val graphFarColor: Color
    val graphUnreachableColor: Color
    val graphFrontierColor: Color
    val graphAnchorColor: Color

    // The plan graph.
    val junctionColor: Color
    val unsettledJunctionColor: Color
    val segmentFastColor: Color
    val segmentSlowColor: Color

    // The anchor tree.
    val spineColor: Color
    val treeBestColor: Color
    val treeOpenColor: Color
    val treeParkedColor: Color
    val treeSpentColor: Color
    val treeInteriorColor: Color
}
