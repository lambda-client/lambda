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
        "Candidates", false,
        "The continuations the horizon is choosing between right now: the current best, " +
            "and the other lines still alive.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderTrail by c.setting("Live Trail", true, "Where the body actually went.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderLabels by c.setting("Labels", true, "Frames, dependencies, seed parameters, deviation.") { enabled }

    @Group(TOGGLES_GROUP)
    override val renderPlanning by c.setting(
        "Live Planning", false,
        "Draw the coarse route the moment D* converges and every candidate rollout as the search tries it.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderPlanGraph by c.setting(
        "Plan Graph", false,
        "The certified plan as its junction graph: one edge per decision, shaded by how " +
            "many frames it spends per block covered, with the junctions a shortcut is " +
            "allowed to rejoin at picked out.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderPlanGraphLabels by c.setting(
        "Plan Graph Labels", true,
        "Number the junctions and name the spans worth shortening.",
    ) { enabled && renderPlanGraph }

    @Group(TOGGLES_GROUP)
    override val renderSearchTree by c.setting(
        "Search Tree", false,
        "The trajectory search's live anchor tree: the committed spine, the best rival " +
            "line, and every branch still open, parked or spent.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderSearchTreeNodes by c.setting(
        "Search Tree Nodes", true,
        "Mark the anchors themselves, not just the lines between them.",
    ) { enabled && renderSearchTree }

    @Group(TOGGLES_GROUP)
    override val renderSearchStats by c.setting(
        "Search Stats", false,
        "Live counters above the body: expansions, budget, difficulty ladders, frontier " +
            "sizes, beam merge rate, and how far the tape is ahead of the cursor.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderGraph by c.setting(
        "Search Graph", false,
        "Every cell the coarse search touched, shaded by how far it still is from the goal. " +
            "Shown even when no route is found, which is when it says the most.",
    ) { enabled }

    @Group(TOGGLES_GROUP)
    override val renderGraphFrontier by c.setting(
        "Graph Frontier", true,
        "Pick out the cells still queued -- the boundary the search would grow next.",
    ) { enabled && renderGraph }

    @Group(TOGGLES_GROUP)
    override val renderGraphEdges by c.setting(
        "Graph Edges", true,
        "Draw the move out of each cell the search would actually take. Chained together " +
            "these are the route D* believes in, which is what makes the field readable.",
    ) { enabled && renderGraph }

    @Group(TOGGLES_GROUP)
    override val renderGraphAllEdges by c.setting(
        "All Graph Edges", false,
        "Also draw every other move the expansion costed. Dense, but it is what shows a " +
            "cell that is connected to nothing.",
    ) { enabled && renderGraph && renderGraphEdges }

    @Group(WIDTH_GROUP)
    override val graphNodeSize by c.setting("Graph Cell Size", 0.24, 0.05..0.8, 0.01) { enabled && renderGraph }

    @Group(WIDTH_GROUP)
    override val graphEdgeWidth by c.setting("Graph Edge Width", 8, 1..80, 1, unit = " px") {
        enabled && renderGraph && renderGraphEdges
    }

    @Group(BUDGET_GROUP)
    override val graphRadius by c.setting(
        "Graph View Radius", 48.0, 8.0..256.0, 1.0, unit = " blocks",
    ) { enabled && renderGraph }

    @Group(BUDGET_GROUP)
    override val graphCellBudget by c.setting("Graph Cell Budget", 3072, 64..65536, 64) {
        enabled && renderGraph
    }

    @Group(BUDGET_GROUP)
    override val graphEdgeBudget by c.setting("Graph Edge Budget", 12288, 64..262144, 256) {
        enabled && renderGraph && renderGraphEdges
    }

    @Group(WIDTH_GROUP)
    override val coarseWidth by c.setting("Coarse Width", 26, 1..150, 1, unit = " px") { enabled && renderCoarseRoute }

    @Group(WIDTH_GROUP)
    override val trajectoryWidth by c.setting("Trajectory Width", 40, 1..150, 1, unit = " px") { enabled && renderTrajectory }

    @Group(WIDTH_GROUP)
    override val trailWidth by c.setting("Trail Width", 20, 1..150, 1, unit = " px") { enabled && renderTrail }

    @Group(WIDTH_GROUP)
    override val searchTreeWidth by c.setting("Search Tree Width", 16, 1..150, 1, unit = " px") {
        enabled && renderSearchTree
    }

    @Group(WIDTH_GROUP)
    override val searchTreeNodeSize by c.setting("Search Tree Node Size", 0.08, 0.01..0.5, 0.01) {
        enabled && renderSearchTree && renderSearchTreeNodes
    }

    @Group(WIDTH_GROUP)
    override val junctionSize by c.setting("Junction Size", 0.16, 0.02..0.6, 0.01) {
        enabled && renderPlanGraph
    }

    @Group(WIDTH_GROUP)
    override val labelSize by c.setting("Label Size", 0.22, 0.05..1.0, 0.01) { enabled && renderLabels }

    @Group(COLOR_GROUP)
    override val walkColor by c.setting("Walk Edge", Color(90, 150, 255, 217)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val stepUpColor by c.setting("Step Up Edge", Color(255, 170, 60, 230)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val walkOffColor by c.setting("Walk Off Edge", Color(190, 120, 255, 230)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val dropColor by c.setting("Drop Edge", Color(120, 210, 190, 230)) { enabled && renderCoarseRoute }

    @Group(COLOR_GROUP)
    override val unknownMovementColor by c.setting("Other Edge", Color(200, 200, 200, 200)) { enabled && renderCoarseRoute }

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

    @Group(COLOR_GROUP)
    override val graphNearColor by c.setting("Graph Near Goal", Color(70, 240, 160, 190)) { enabled && renderGraph }

    @Group(COLOR_GROUP)
    override val graphFarColor by c.setting("Graph Far From Goal", Color(60, 90, 200, 170)) { enabled && renderGraph }

    @Group(COLOR_GROUP)
    override val graphUnreachableColor by c.setting("Graph Unreachable", Color(120, 120, 120, 120)) { enabled && renderGraph }

    @Group(COLOR_GROUP)
    override val graphFrontierColor by c.setting("Graph Frontier", Color(255, 170, 40, 235)) { enabled && renderGraphFrontier }

    @Group(COLOR_GROUP)
    override val graphEdgeColor by c.setting("Graph Other Edge", Color(150, 160, 190, 90)) {
        enabled && renderGraph && renderGraphAllEdges
    }

    @Group(COLOR_GROUP)
    override val graphAnchorColor by c.setting("Graph Optimistic Anchor", Color(255, 110, 220, 220)) {
        enabled && renderGraph
    }

    @Group(COLOR_GROUP)
    override val junctionColor by c.setting("Junction", Color(120, 230, 255, 240)) { enabled && renderPlanGraph }

    @Group(COLOR_GROUP)
    override val unsettledJunctionColor by c.setting("Junction (airborne)", Color(255, 120, 90, 200)) {
        enabled && renderPlanGraph
    }

    @Group(COLOR_GROUP)
    override val segmentFastColor by c.setting("Segment Fast", Color(90, 240, 150, 235)) { enabled && renderPlanGraph }

    @Group(COLOR_GROUP)
    override val segmentSlowColor by c.setting("Segment Slow", Color(255, 90, 90, 235)) { enabled && renderPlanGraph }

    @Group(COLOR_GROUP)
    override val alternateColor by c.setting("Alternate", Color(255, 220, 120, 240)) { enabled && renderPlanGraph }

    @Group(COLOR_GROUP)
    override val spineColor by c.setting("Tree Spine", Color(80, 255, 190, 245)) { enabled && renderSearchTree }

    @Group(COLOR_GROUP)
    override val treeBestColor by c.setting("Tree Best Rival", Color(255, 255, 140, 240)) { enabled && renderSearchTree }

    @Group(COLOR_GROUP)
    override val treeOpenColor by c.setting("Tree Open", Color(255, 170, 40, 220)) { enabled && renderSearchTree }

    @Group(COLOR_GROUP)
    override val treeParkedColor by c.setting("Tree Parked", Color(150, 130, 255, 190)) { enabled && renderSearchTree }

    @Group(COLOR_GROUP)
    override val treeSpentColor by c.setting("Tree Spent", Color(120, 120, 130, 150)) { enabled && renderSearchTree }

    @Group(COLOR_GROUP)
    override val treeInteriorColor by c.setting("Tree Interior", Color(90, 110, 140, 120)) { enabled && renderSearchTree }

    private companion object {
        const val TOGGLES_GROUP = "Toggles"
        const val WIDTH_GROUP = "Widths"
        const val BUDGET_GROUP = "Graph Budgets"
        const val COLOR_GROUP = "Colors"
    }
}
