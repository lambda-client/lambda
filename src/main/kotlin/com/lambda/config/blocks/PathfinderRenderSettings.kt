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

class PathfinderRenderSettings(override val c: Config) : PathfinderRenderConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
        private const val PATH_GROUP = "Path"
        private const val PATH_COLORS_GROUP = "Path Colors"
        private const val NODES_GROUP = "Nodes"
        private const val TRAJECTORY_GROUP = "Trajectories"
        private const val MARKERS_GROUP = "Markers"
        private const val REFINEMENT_GROUP = "Refinement"
        private const val EXECUTION_GROUP = "Execution"
        private const val EXECUTION_COLORS_GROUP = "Exec Colors"
        private const val GRAPH_GROUP = "Graph"
        private const val GRAPH_COLORS_GROUP = "Graph Colors"
    }

    @Group(GENERAL_GROUP) override val enabled by c.setting("Render Enabled", true, "Master toggle for pathfinder rendering.")
    @Group(GENERAL_GROUP) override val depthTest by c.setting("Depth Test", false)
    @Group(GENERAL_GROUP) override val useSplines by c.setting("Use Splines", true)
    @Group(GENERAL_GROUP) override val splineSegments by c.setting("Spline Segments", 3, 1..32, 1)
    @Group(GENERAL_GROUP) override val splineTension by c.setting("Spline Tension", 0.85, 0.0..1.0, 0.05, "Higher values keep the spline tighter around path nodes.")
    @Group(GENERAL_GROUP) override val yOffset by c.setting("Y Offset", 0.08, 0.0..1.0, 0.01)

    @Group(PATH_GROUP) override val renderPath by c.setting("Render Path", true)
    @Group(PATH_GROUP) override val renderPartial by c.setting("Render Partial Paths", true)
    @Group(PATH_GROUP) override val renderCoarsePath by c.setting("Render Coarse Path", false)
    @Group(PATH_GROUP) override val screenWidth by c.setting("Screen Width", 48, 1..150, 1)
    @Group(PATH_GROUP) override val coarseScreenWidth by c.setting("Coarse Path Screen Width", 30, 1..150, 1) { renderCoarsePath }
    @Group(PATH_GROUP) override val fadeTraversedPath by c.setting("Fade Traversed Path", true, "Dim the part of the path already walked so the remaining plan stands out.") { renderPath }
    @Group(PATH_GROUP) override val traversedPathOpacity by c.setting("Traversed Path Opacity", 0.25, 0.0..1.0, 0.05) { renderPath && fadeTraversedPath }

    @Group(PATH_COLORS_GROUP) override val coarsePathColor by c.setting("Coarse Path Color", Color(255, 220, 80, 150))
    @Group(PATH_COLORS_GROUP) override val readyStartColor by c.setting("Ready Start Color", Color(40, 220, 255, 230))
    @Group(PATH_COLORS_GROUP) override val readyEndColor by c.setting("Ready End Color", Color(120, 255, 140, 230))
    @Group(PATH_COLORS_GROUP) override val partialStartColor by c.setting("Partial Start Color", Color(255, 200, 40, 230))
    @Group(PATH_COLORS_GROUP) override val partialEndColor by c.setting("Partial End Color", Color(255, 120, 40, 230))

    @Group(NODES_GROUP) override val renderPathNodes by c.setting("Render Path Nodes", true) { renderPath }
    @Group(NODES_GROUP) override val pathNodeSize by c.setting("Path Node Size", 0.11, 0.02..0.5, 0.01) { renderPath && renderPathNodes }
    @Group(NODES_GROUP) override val pathNodeColor by c.setting("Path Node Color", Color(255, 255, 255, 115)) { renderPath && renderPathNodes }

    @Group(TRAJECTORY_GROUP) override val renderSimulatedWalk by c.setting("Render Simulated Walk", true, "The walk the solver rolled out through real physics from the live state: corners rounded, the lattice zig-zag pulled straight. Where this line and the path polyline disagree, the polyline is the one that is wrong.")
    @Group(TRAJECTORY_GROUP) override val simulatedWalkColor by c.setting("Simulated Walk Color", Color(90, 255, 180, 230)) { renderSimulatedWalk }
    @Group(TRAJECTORY_GROUP) override val renderPlannedJumps by c.setting("Render Planned Jumps", true, "Simulated flight paths of upcoming jumps, drops and chain maneuvers — what the executor intends to fly.")
    @Group(TRAJECTORY_GROUP) override val plannedJumpWidth by c.setting("Planned Jump Width", 34, 1..150, 1) { renderPlannedJumps }
    @Group(TRAJECTORY_GROUP) override val renderLandingMarkers by c.setting("Render Landing Markers", true, "Flat reticle on each planned landing block.") { renderPlannedJumps }
    @Group(TRAJECTORY_GROUP) override val landingMarkerSize by c.setting("Landing Marker Size", 0.35, 0.1..1.0, 0.05) { renderPlannedJumps && renderLandingMarkers }
    @Group(TRAJECTORY_GROUP) override val jumpArcColor by c.setting("Jump Arc Color", Color(255, 190, 70, 235)) { renderPlannedJumps }
    @Group(TRAJECTORY_GROUP) override val chainArcColor by c.setting("Chain Arc Color", Color(255, 120, 220, 235)) { renderPlannedJumps }
    @Group(TRAJECTORY_GROUP) override val dropArcColor by c.setting("Drop Arc Color", Color(120, 200, 255, 210)) { renderPlannedJumps }
    @Group(TRAJECTORY_GROUP) override val launchArcColor by c.setting("Launch Arc Color", Color(255, 255, 255, 245), "Flight prediction computed at the actual launch tick — the committed trajectory of the jump in the air.") { renderPlannedJumps }

    @Group(MARKERS_GROUP) override val renderMarkers by c.setting("Render Markers", true)
    @Group(MARKERS_GROUP) override val markerSize by c.setting("Marker Size", 0.45, 0.1..1.5, 0.05)
    @Group(MARKERS_GROUP) override val markerHeight by c.setting("Marker Height", 0.08, 0.02..0.5, 0.01)
    @Group(MARKERS_GROUP) override val startMarkerColor by c.setting("Start Marker Color", Color(80, 255, 120, 230))
    @Group(MARKERS_GROUP) override val goalMarkerColor by c.setting("Goal Marker Color", Color(255, 80, 120, 230))

    @Group(REFINEMENT_GROUP) override val renderRefinementDebug by c.setting("Render Refinement Debug", false)
    @Group(REFINEMENT_GROUP) override val renderAcceptedShortcuts by c.setting("Render Accepted Shortcuts", true) { renderRefinementDebug }
    @Group(REFINEMENT_GROUP) override val renderRejectedShortcuts by c.setting("Render Rejected Shortcuts", true) { renderRefinementDebug }
    @Group(REFINEMENT_GROUP) override val renderShortcutReasons by c.setting("Render Shortcut Reasons", false) { renderRefinementDebug }
    @Group(REFINEMENT_GROUP) override val refinementDebugWidth by c.setting("Refinement Debug Width", 18, 1..100, 1) { renderRefinementDebug }
    @Group(REFINEMENT_GROUP) override val maxRefinementDebugAttempts by c.setting("Max Refinement Debug Attempts", 64, 1..512, 1) { renderRefinementDebug }
    @Group(REFINEMENT_GROUP) override val acceptedShortcutColor by c.setting("Accepted Shortcut Color", Color(80, 255, 120, 190)) { renderRefinementDebug && renderAcceptedShortcuts }
    @Group(REFINEMENT_GROUP) override val rejectedShortcutColor by c.setting("Rejected Shortcut Color", Color(255, 90, 90, 170)) { renderRefinementDebug && renderRejectedShortcuts }
    @Group(REFINEMENT_GROUP) override val shortcutReasonColor by c.setting("Shortcut Reason Color", Color(255, 255, 255, 220)) { renderRefinementDebug && renderShortcutReasons }

    @Group(EXECUTION_GROUP) override val renderExecutionDebug by c.setting("Render Execution Debug", false)
    @Group(EXECUTION_GROUP) override val renderActiveSegment by c.setting("Render Active Segment", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderLookaheadPoint by c.setting("Render Lookahead Point", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderProjectedPoint by c.setting("Render Projected Point", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderProjectionLine by c.setting("Render Projection Line", true) { renderExecutionDebug && renderProjectedPoint }
    @Group(EXECUTION_GROUP) override val renderDesiredVector by c.setting("Render Desired Vector", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderVelocityVector by c.setting("Render Velocity Vector", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderMoveDeltaVector by c.setting("Render MoveDelta Vector", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderExecutionTrail by c.setting("Render Execution Trail", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val renderExecutionText by c.setting("Render Execution Text", true) { renderExecutionDebug }
    @Group(EXECUTION_GROUP) override val executionDebugWidth by c.setting("Execution Debug Width", 30, 1..150, 1) { renderExecutionDebug && renderActiveSegment }
    @Group(EXECUTION_GROUP) override val executionTrailWidth by c.setting("Execution Trail Width", 18, 1..150, 1) { renderExecutionDebug && renderExecutionTrail }
    @Group(EXECUTION_GROUP) override val lookaheadPointSize by c.setting("Lookahead Point Size", 0.12, 0.02..0.5, 0.01) { renderExecutionDebug && renderLookaheadPoint }
    @Group(EXECUTION_GROUP) override val projectedPointSize by c.setting("Projected Point Size", 0.10, 0.02..0.5, 0.01) { renderExecutionDebug && renderProjectedPoint }
    @Group(EXECUTION_GROUP) override val executionVectorScale by c.setting("Execution Vector Scale", 2.50, 0.10..10.0, 0.10) { renderExecutionDebug && (renderVelocityVector || renderMoveDeltaVector) }
    @Group(EXECUTION_GROUP) override val maxExecutionSamples by c.setting("Max Execution Samples", 40, 2..400, 1) { renderExecutionDebug && renderExecutionTrail }

    @Group(EXECUTION_COLORS_GROUP) override val executionSegmentColor by c.setting("Execution Segment Color", Color(80, 180, 255, 220)) { renderExecutionDebug && renderActiveSegment }
    @Group(EXECUTION_COLORS_GROUP) override val lookaheadPointColor by c.setting("Lookahead Point Color", Color(255, 255, 255, 220)) { renderExecutionDebug && renderLookaheadPoint }
    @Group(EXECUTION_COLORS_GROUP) override val projectedPointColor by c.setting("Projected Point Color", Color(255, 230, 120, 220)) { renderExecutionDebug && renderProjectedPoint }
    @Group(EXECUTION_COLORS_GROUP) override val projectionLineColor by c.setting("Projection Line Color", Color(255, 160, 80, 220)) { renderExecutionDebug && renderProjectionLine }
    @Group(EXECUTION_COLORS_GROUP) override val desiredVectorColor by c.setting("Desired Vector Color", Color(80, 255, 255, 210)) { renderExecutionDebug && renderDesiredVector }
    @Group(EXECUTION_COLORS_GROUP) override val velocityVectorColor by c.setting("Velocity Vector Color", Color(120, 255, 120, 210)) { renderExecutionDebug && renderVelocityVector }
    @Group(EXECUTION_COLORS_GROUP) override val moveDeltaColor by c.setting("MoveDelta Color", Color(255, 120, 255, 210)) { renderExecutionDebug && renderMoveDeltaVector }
    @Group(EXECUTION_COLORS_GROUP) override val executionTrailColor by c.setting("Execution Trail Color", Color(255, 255, 255, 170)) { renderExecutionDebug && renderExecutionTrail }
    @Group(EXECUTION_COLORS_GROUP) override val executionTextColor by c.setting("Execution Text Color", Color(255, 255, 255, 220)) { renderExecutionDebug && renderExecutionText }

    @Group(GRAPH_GROUP) override val renderLazyGraph by c.setting("Render Lazy Graph", false)
    @Group(GRAPH_GROUP) override val renderGraphEdges by c.setting("Render Graph Edges", true) { renderLazyGraph }
    @Group(GRAPH_GROUP) override val renderGraphNodes by c.setting("Render Graph Nodes", false) { renderLazyGraph }
    @Group(GRAPH_GROUP) override val renderGraphData by c.setting("Render Graph Data", false) { renderLazyGraph }
    @Group(GRAPH_GROUP) override val renderGraphPositions by c.setting("Graph Positions", false) { renderLazyGraph && renderGraphData }
    @Group(GRAPH_GROUP) override val renderGraphG by c.setting("Graph g", false) { renderLazyGraph && renderGraphData }
    @Group(GRAPH_GROUP) override val renderGraphRhs by c.setting("Graph rhs", true) { renderLazyGraph && renderGraphData }
    @Group(GRAPH_GROUP) override val renderGraphKey by c.setting("Graph Key", false) { renderLazyGraph && renderGraphData }
    @Group(GRAPH_GROUP) override val renderGraphQueue by c.setting("Graph Queue", true) { renderLazyGraph && renderGraphData }
    @Group(GRAPH_GROUP) override val renderGraphEdgeCost by c.setting("Graph Edge Cost", false) { renderLazyGraph && renderGraphData && renderGraphEdges }
    @Group(GRAPH_GROUP) override val graphScreenWidth by c.setting("Graph Screen Width", 28, 1..150, 1) { renderLazyGraph && renderGraphEdges }
    @Group(GRAPH_GROUP) override val graphNodeSize by c.setting("Graph Node Size", 0.10, 0.02..0.5, 0.01) { renderLazyGraph && renderGraphNodes }
    @Group(GRAPH_GROUP) override val graphTextSize by c.setting("Graph Text Size", 0.22, 0.05..1.0, 0.01) { renderLazyGraph && renderGraphData }
    @Group(GRAPH_GROUP) override val maxGraphNodes by c.setting("Max Graph Nodes", 2_000, 100..50_000, 100) { renderLazyGraph }
    @Group(GRAPH_GROUP) override val maxGraphEdges by c.setting("Max Graph Edges", 8_000, 100..200_000, 100) { renderLazyGraph && renderGraphEdges }
    @Group(GRAPH_GROUP) override val maxGraphLabels by c.setting("Max Graph Labels", 200, 1..5_000, 1) { renderLazyGraph && renderGraphData }

    @Group(GRAPH_COLORS_GROUP) override val graphEdgeColor by c.setting("Graph Edge Color", Color(160, 120, 255, 130)) { renderLazyGraph && renderGraphEdges }
    @Group(GRAPH_COLORS_GROUP) override val graphNodeColor by c.setting("Graph Node Color", Color(120, 180, 255, 100)) { renderLazyGraph && renderGraphNodes }
    @Group(GRAPH_COLORS_GROUP) override val graphDataColor by c.setting("Graph Data Color", Color(230, 230, 255, 230)) { renderLazyGraph && renderGraphData }
}
