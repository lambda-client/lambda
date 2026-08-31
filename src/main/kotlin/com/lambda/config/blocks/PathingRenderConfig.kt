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
    val renderCandidates: Boolean
    val renderTrail: Boolean
    val renderLabels: Boolean

    /** Live planning view: the coarse route as it lands, candidates as they are tried. */
    val renderPlanning: Boolean

    /**
     * The trajectory search's own anchor tree, and the counters driving it.
     *
     * The coarse graph shows where the planner may go; this shows what it is actually
     * doing with that freedom -- which line the body is committed to, which branches are
     * still arguing, and how much of the budget each is costing.
     */
    val renderPlanGraph: Boolean
    val renderPlanGraphLabels: Boolean

    val renderSearchTree: Boolean
    val renderSearchTreeNodes: Boolean
    val renderSearchStats: Boolean

    /** The search graph itself: every cell D* touched, coloured by cost to the goal. */
    val renderGraph: Boolean
    val renderGraphFrontier: Boolean

    /** The moves between cells, not just the cells: the flow the search would follow. */
    val renderGraphEdges: Boolean

    /** Every expanded move, not only the one the search picked out of each cell. */
    val renderGraphAllEdges: Boolean

    /** Screen-space widths, in pixels. */
    val coarseWidth: Int
    val trajectoryWidth: Int
    val trailWidth: Int
    val labelSize: Double
    val graphNodeSize: Double
    val graphEdgeWidth: Int
    val searchTreeWidth: Int
    val searchTreeNodeSize: Double
    val junctionSize: Double

    /**
     * How much of the search graph the view is allowed to take, per publish.
     *
     * Budgets rather than a preference: the graph runs to tens of thousands of cells on
     * a long path and the render is per-frame, so something has to bound it. What is
     * drawn against the total is reported in the label, so raising these to look further
     * is a decision the view makes visible rather than one it hides.
     */
    val graphRadius: Double
    val graphCellBudget: Int
    val graphEdgeBudget: Int

    val walkColor: Color
    val stepUpColor: Color
    val walkOffColor: Color
    val dropColor: Color
    val unknownMovementColor: Color
    val jumpCandidateColor: Color
    val nodeColor: Color
    val trajectoryColor: Color
    val jumpColor: Color
    val spliceColor: Color
    val bestCandidateColor: Color
    val candidateColor: Color
    val stopColor: Color
    val trailColor: Color
    val rejectColor: Color
    val textColor: Color

    /** Graph cells are shaded between these by cost to the goal; near means cheap. */
    val graphNearColor: Color
    val graphFarColor: Color
    val graphUnreachableColor: Color
    val graphFrontierColor: Color

    /** Expanded moves the search did not pick; the picked ones take the cell's shade. */
    val graphEdgeColor: Color

    /** Cells holding the optimistic step into terrain the client has not streamed. */
    val graphAnchorColor: Color

    /** Junctions a branch may rejoin at, and the ones it may not. */
    val junctionColor: Color
    val unsettledJunctionColor: Color

    /** Spine segments shade between these by frames spent per block covered. */
    val segmentFastColor: Color
    val segmentSlowColor: Color
    val alternateColor: Color

    /** Anchor-tree roles: the committed line, the best rival, and the live frontier. */
    val spineColor: Color
    val treeBestColor: Color
    val treeOpenColor: Color
    val treeParkedColor: Color
    val treeSpentColor: Color
    val treeInteriorColor: Color
}
