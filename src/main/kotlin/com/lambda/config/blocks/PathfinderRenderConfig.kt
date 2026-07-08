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

interface PathfinderRenderConfig {
    val enabled: Boolean
    val renderPath: Boolean
    val renderPathNodes: Boolean
    val renderPartial: Boolean
    val renderCoarsePath: Boolean
    val renderRefinementDebug: Boolean
    val renderExecutionDebug: Boolean
    val renderAcceptedShortcuts: Boolean
    val renderRejectedShortcuts: Boolean
    val renderShortcutReasons: Boolean
    val renderLazyGraph: Boolean
    val renderGraphEdges: Boolean
    val renderGraphNodes: Boolean
    val renderGraphData: Boolean
    val renderGraphPositions: Boolean
    val renderGraphG: Boolean
    val renderGraphRhs: Boolean
    val renderGraphKey: Boolean
    val renderGraphQueue: Boolean
    val renderGraphEdgeCost: Boolean
    val depthTest: Boolean
    val useSplines: Boolean
    val splineSegments: Int
    val splineTension: Double
    val yOffset: Double
    val screenWidth: Int
    val coarseScreenWidth: Int
    val pathNodeSize: Double
    val refinementDebugWidth: Int
    val maxRefinementDebugAttempts: Int
    val renderActiveSegment: Boolean
    val renderLookaheadPoint: Boolean
    val renderProjectedPoint: Boolean
    val renderProjectionLine: Boolean
    val renderDesiredVector: Boolean
    val renderVelocityVector: Boolean
    val renderMoveDeltaVector: Boolean
    val renderExecutionTrail: Boolean
    val renderExecutionText: Boolean
    val executionDebugWidth: Int
    val executionTrailWidth: Int
    val lookaheadPointSize: Double
    val projectedPointSize: Double
    val executionVectorScale: Double
    val maxExecutionSamples: Int
    val graphScreenWidth: Int
    val graphNodeSize: Double
    val graphTextSize: Double
    val maxGraphNodes: Int
    val maxGraphEdges: Int
    val maxGraphLabels: Int
    val renderMarkers: Boolean
    val markerSize: Double
    val markerHeight: Double

    val coarsePathColor: Color
    val readyStartColor: Color
    val readyEndColor: Color
    val partialStartColor: Color
    val partialEndColor: Color
    val pathNodeColor: Color
    val acceptedShortcutColor: Color
    val executionSegmentColor: Color
    val lookaheadPointColor: Color
    val projectedPointColor: Color
    val projectionLineColor: Color
    val desiredVectorColor: Color
    val velocityVectorColor: Color
    val moveDeltaColor: Color
    val executionTrailColor: Color
    val executionTextColor: Color
    val rejectedShortcutColor: Color
    val shortcutReasonColor: Color
    val graphEdgeColor: Color
    val graphNodeColor: Color
    val graphDataColor: Color
    val startMarkerColor: Color
    val goalMarkerColor: Color
}
