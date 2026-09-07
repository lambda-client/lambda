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

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import java.awt.Color

/**
 * One palette, three families: cool blues for the plan (route, graph), mint for the
 * certified tape, warm amber and red for warnings and failures. Neutral greys for what
 * is already behind the body.
 */
class PathingRenderSettings(override val c: Config) : PathingRenderConfig, ConfigBlock {
    @Group(LAYERS_GROUP)
    override val enabled by c.setting("Render", true, "Draw the walk in the world. Numbers live in the Pathing HUD element.")

    @Group(LAYERS_GROUP)
    override val depthTest by c.setting("Depth Test", false, "Hide the drawing behind blocks.") { enabled }

    @Group(LAYERS_GROUP)
    override val renderCoarseRoute by c.setting("Coarse Route", true, "The plan's skeleton: one colour per movement kind.") { enabled }

    @Group(LAYERS_GROUP)
    override val renderTrajectory by c.setting("Trajectory", true, "The certified tape: dim behind the body, bright ahead of it.") { enabled }

    @Group(LAYERS_GROUP)
    override val renderJumpMarkers by c.setting("Jump Ticks", true, "A tick where the tape presses jump.") { enabled && renderTrajectory }

    @Group(LAYERS_GROUP)
    override val renderSplices by c.setting("Splice Marks", false, "Where one controller hands over to the next.") { enabled && renderTrajectory }

    @Group(LAYERS_GROUP)
    override val renderTrail by c.setting("Live Trail", true, "Where the body actually went. Separation from the trajectory is simulator error.") { enabled }

    @Group(LAYERS_GROUP)
    override val renderGoal by c.setting("Goal", true, "A ring at the leg's goal and a beacon at the final one.") { enabled }

    @Group(LAYERS_GROUP)
    override val renderPlanning by c.setting(
        "Live Planning", false,
        "The coarse route the moment D* converges and every rollout as the search tries it.",
    ) { enabled }

    @Group(LAYERS_GROUP)
    override val renderCandidates by c.setting(
        "Candidates", false,
        "The continuations the horizon is choosing between: the best one bright, the rest faint.",
    ) { enabled && renderPlanning }

    @Group(LAYERS_GROUP)
    override val renderPlanGraph by c.setting(
        "Plan Graph", true,
        "The plan's decision graph: a diamond at every junction a repair may cut at, dotted alternates.",
    ) { enabled }

    @Group(LAYERS_GROUP)
    override val renderPlanGraphPace by c.setting(
        "Plan Graph Pace", false,
        "Shade each span of the plan graph by its pace, fast to slow, on top of the tape.",
    ) { enabled && renderPlanGraph }

    @Group(LAYERS_GROUP)
    override val renderSearchTree by c.setting(
        "Search Tree", true,
        "What the search is weighing: open branches solid, the best rival dashed, parked ones dotted.",
    ) { enabled }

    @Group(LAYERS_GROUP)
    override val renderSearchTreeNodes by c.setting("Search Tree Nodes", true, "Mark the anchors themselves.") { enabled && renderSearchTree }

    @Group(LAYERS_GROUP)
    override val renderGraph by c.setting(
        "Search Graph", false,
        "The lazy coarse graph: every transition D* materialised, in its movement colour; the descent bright.",
    ) { enabled }

    @Group(LAYERS_GROUP)
    override val renderGraphFrontier by c.setting("Graph Frontier", true, "Ring the cells still queued.") { enabled && renderGraph }

    @Group(LAYERS_GROUP)
    override val renderGraphEdges by c.setting("Graph Edges", true, "Every materialised transition; the one the descent takes is bright, the rest faint.") { enabled && renderGraph }

    @Group(LAYERS_GROUP)
    override val renderGraphCells by c.setting("Graph Cells", false, "Shade each touched cell by its remaining cost, as a carpet under the transitions.") { enabled && renderGraph }

    @Group(SIZE_GROUP)
    override val coarseWidth by c.setting("Coarse Width", 28, 1..150, 1, unit = " px") { enabled && renderCoarseRoute }

    @Group(SIZE_GROUP)
    override val trajectoryWidth by c.setting("Trajectory Width", 64, 1..150, 1, unit = " px") { enabled && renderTrajectory }

    @Group(SIZE_GROUP)
    override val trailWidth by c.setting("Trail Width", 24, 1..150, 1, unit = " px") { enabled && renderTrail }

    @Group(SIZE_GROUP)
    override val searchTreeWidth by c.setting("Search Tree Width", 18, 1..150, 1, unit = " px") { enabled && renderSearchTree }

    @Group(SIZE_GROUP)
    override val graphEdgeWidth by c.setting("Graph Edge Width", 10, 1..80, 1, unit = " px") { enabled && renderGraph && renderGraphEdges }

    @Group(SIZE_GROUP)
    override val graphNodeSize by c.setting("Graph Cell Size", 0.5, 0.05..0.8, 0.01) { enabled && renderGraph }

    @Group(SIZE_GROUP)
    override val searchTreeNodeSize by c.setting("Search Tree Node Size", 0.1, 0.01..0.5, 0.01) { enabled && renderSearchTree && renderSearchTreeNodes }

    @Group(SIZE_GROUP)
    override val junctionSize by c.setting("Junction Size", 0.18, 0.02..0.6, 0.01) { enabled && renderPlanGraph }

    @Group(BUDGET_GROUP)
    override val graphRadius by c.setting("Graph View Radius", 96.0, 8.0..256.0, 1.0, unit = " blocks") { enabled && renderGraph }

    @Group(BUDGET_GROUP)
    override val graphCellBudget by c.setting("Graph Cell Budget", 16384, 64..131072, 256) { enabled && renderGraph }

    @Group(BUDGET_GROUP)
    override val graphEdgeBudget by c.setting("Graph Edge Budget", 32768, 64..524288, 512) { enabled && renderGraph && renderGraphEdges }

    @Group(MOVEMENT_GROUP)
    override val walkColor by c.setting("Walk", Color(96, 165, 250, 230)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val stepUpColor by c.setting("Step Up", Color(129, 140, 248, 230)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val walkOffColor by c.setting("Walk Off", Color(56, 189, 248, 230)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val dropColor by c.setting("Drop", Color(45, 212, 191, 230)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val jumpColor by c.setting("Jump", Color(251, 146, 60, 235)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val bounceColor by c.setting("Bounce", Color(163, 230, 53, 235)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val climbColor by c.setting("Climb / Ladder", Color(232, 121, 249, 235)) { enabled }

    @Group(MOVEMENT_GROUP)
    override val unknownMovementColor by c.setting("Other", Color(203, 213, 225, 200)) { enabled }

    @Group(TAPE_GROUP)
    override val trajectoryColor by c.setting("Trajectory Ahead", Color(52, 211, 153, 245)) { enabled && renderTrajectory }

    @Group(TAPE_GROUP)
    override val executedColor by c.setting("Trajectory Behind", Color(148, 163, 184, 120)) { enabled && renderTrajectory }

    @Group(TAPE_GROUP)
    override val trailColor by c.setting("Live Trail", Color(255, 255, 255, 235)) { enabled && renderTrail }

    @Group(TAPE_GROUP)
    override val jumpMarkerColor by c.setting("Jump Tick", Color(253, 224, 71, 240)) { enabled && renderJumpMarkers }

    @Group(TAPE_GROUP)
    override val spliceColor by c.setting("Splice Mark", Color(125, 211, 252, 220)) { enabled && renderSplices }

    @Group(TAPE_GROUP)
    override val stopColor by c.setting("Certified Stop", Color(52, 211, 153, 255)) { enabled && renderTrajectory }

    @Group(TAPE_GROUP)
    override val partialStopColor by c.setting("Partial Tape End", Color(251, 191, 36, 255)) { enabled && renderTrajectory }

    @Group(TAPE_GROUP)
    override val goalColor by c.setting("Goal", Color(250, 204, 21, 255)) { enabled && renderGoal }

    @Group(PLANNING_GROUP)
    override val attemptColor by c.setting("Certified Attempt", Color(52, 211, 153, 110)) { enabled && renderPlanning }

    @Group(PLANNING_GROUP)
    override val rejectColor by c.setting("Rejected", Color(248, 113, 113, 255)) { enabled }

    @Group(PLANNING_GROUP)
    override val bestCandidateColor by c.setting("Best Candidate", Color(253, 224, 71, 240)) { enabled && renderCandidates }

    @Group(PLANNING_GROUP)
    override val candidateColor by c.setting("Other Candidates", Color(167, 139, 250, 120)) { enabled && renderCandidates }

    @Group(GRAPH_GROUP)
    override val graphNearColor by c.setting("Near Goal", Color(52, 211, 153, 170)) { enabled && renderGraph }

    @Group(GRAPH_GROUP)
    override val graphFarColor by c.setting("Far From Goal", Color(67, 56, 202, 160)) { enabled && renderGraph }

    @Group(GRAPH_GROUP)
    override val graphUnreachableColor by c.setting("Unreachable", Color(60, 60, 70, 120)) { enabled && renderGraph }

    @Group(GRAPH_GROUP)
    override val graphFrontierColor by c.setting("Frontier", Color(251, 146, 60, 235)) { enabled && renderGraphFrontier }

    @Group(GRAPH_GROUP)
    override val graphAnchorColor by c.setting("Optimistic Anchor", Color(232, 121, 249, 230)) { enabled && renderGraph }

    @Group(GRAPH_GROUP)
    override val junctionColor by c.setting("Junction", Color(125, 211, 252, 240)) { enabled && renderPlanGraph }

    @Group(GRAPH_GROUP)
    override val unsettledJunctionColor by c.setting("Junction (airborne)", Color(251, 146, 60, 200)) { enabled && renderPlanGraph }

    @Group(GRAPH_GROUP)
    override val segmentFastColor by c.setting("Span Fast", Color(52, 211, 153, 220)) { enabled && renderPlanGraph }

    @Group(GRAPH_GROUP)
    override val segmentSlowColor by c.setting("Span Slow", Color(248, 113, 113, 220)) { enabled && renderPlanGraph }

    @Group(GRAPH_GROUP)
    override val alternateColor by c.setting("Alternate", Color(253, 224, 71, 240)) { enabled && renderPlanGraph }

    @Group(TREE_GROUP)
    override val spineColor by c.setting("Spine", Color(52, 211, 153, 245)) { enabled && renderSearchTree }

    @Group(TREE_GROUP)
    override val treeBestColor by c.setting("Best Rival", Color(253, 224, 71, 240)) { enabled && renderSearchTree }

    @Group(TREE_GROUP)
    override val treeOpenColor by c.setting("Open", Color(251, 146, 60, 200)) { enabled && renderSearchTree }

    @Group(TREE_GROUP)
    override val treeParkedColor by c.setting("Parked", Color(167, 139, 250, 170)) { enabled && renderSearchTree }

    @Group(TREE_GROUP)
    override val treeSpentColor by c.setting("Spent", Color(120, 120, 130, 120)) { enabled && renderSearchTree }

    @Group(TREE_GROUP)
    override val treeInteriorColor by c.setting("Interior", Color(90, 110, 140, 90)) { enabled && renderSearchTree }

    private companion object {
        const val LAYERS_GROUP = "Layers"
        const val SIZE_GROUP = "Sizes"
        const val BUDGET_GROUP = "Graph Budgets"
        const val MOVEMENT_GROUP = "Movement Colors"
        const val TAPE_GROUP = "Tape Colors"
        const val PLANNING_GROUP = "Planning Colors"
        const val GRAPH_GROUP = "Graph Colors"
        const val TREE_GROUP = "Search Tree Colors"
    }
}
