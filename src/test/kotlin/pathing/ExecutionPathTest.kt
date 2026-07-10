/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.execution.ExecutionPath
import com.lambda.pathing.execution.RecoveryMode
import com.lambda.pathing.execution.UnsupportedSegment
import com.lambda.pathing.execution.WalkSegment
import com.lambda.util.world.fastVectorOf
import net.minecraft.util.math.Vec3d
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExecutionPathTest {
    @Test
    fun `fromNodes deduplicates path and creates walk segments for flat edges`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 7,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(0, 0, 0),
                fastVectorOf(1, 0, 0),
                fastVectorOf(2, 0, 0),
            ),
        )

        assertEquals(3, path.sourceNodes.size)
        assertEquals(2, path.segments.size)
        assertIs<WalkSegment>(path.segments[0])
        assertIs<WalkSegment>(path.segments[1])
    }

    @Test
    fun `fromNodes emits walk segment for single-block step-up`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 9,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(0, 1, 0),
            ),
        )

        assertEquals(1, path.segments.size)
        val segment = path.segments.single()
        assertIs<WalkSegment>(segment)
        assertEquals(1, segment.verticalStep)
    }

    @Test
    fun `fromNodes emits walk segment for single-block step-down`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 10,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(0, -1, 0),
            ),
        )

        assertEquals(1, path.segments.size)
        val segment = path.segments.single()
        assertIs<WalkSegment>(segment)
        assertEquals(-1, segment.verticalStep)
    }

    @Test
    fun `fromNodes emits unsupported segment for multi-block vertical edge`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 11,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(0, 2, 0),
            ),
        )

        assertEquals(1, path.segments.size)
        assertIs<UnsupportedSegment>(path.segments.single())
    }

    @Test
    fun `locateSegment prefers the current valid segment instead of skipping ahead`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 1,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(1, 0, 0),
                fastVectorOf(2, 0, 0),
            ),
        )

        val selection = path.locateSegment(
            position = Vec3d(1.55, 0.0, 0.5),
            currentIndex = 0,
            searchBehind = 0,
            searchAhead = 2,
            corridorRadius = 0.75,
            verticalTolerance = 0.6,
            relocalizeDistance = 2.0,
            backtrackAllowance = 0.6,
            overshootAllowance = 0.7,
        )

        assertNotNull(selection)
        assertEquals(0, selection.index)
        assertEquals(RecoveryMode.KeptCurrent, selection.recoveryMode)
    }

    @Test
    fun `global relocalization finds progress beyond the bounded current window`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 2,
            nodes = (0..12).map { fastVectorOf(it, 0, 0) },
        )
        val position = Vec3d(10.75, 0.0, 0.5)

        val staleWindow = path.locateSegment(
            position = position,
            currentIndex = 0,
            searchBehind = 2,
            searchAhead = 6,
            corridorRadius = 0.75,
            verticalTolerance = 0.6,
            relocalizeDistance = 1.0,
            backtrackAllowance = 0.6,
            overshootAllowance = 0.7,
        )
        val global = path.locateSegment(
            position = position,
            currentIndex = null,
            searchBehind = 2,
            searchAhead = 6,
            corridorRadius = 0.75,
            verticalTolerance = 0.6,
            relocalizeDistance = 1.0,
            backtrackAllowance = 0.6,
            overshootAllowance = 0.7,
        )

        assertNull(staleWindow)
        assertNotNull(global)
        assertEquals(10, global.index)
        assertEquals(RecoveryMode.Relocalized, global.recoveryMode)
    }

    @Test
    fun `walk segment lookahead clamps to the segment end`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 1,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(1, 0, 0),
            ),
        )

        val segment = assertIs<WalkSegment>(path.segments.single())
        val lookahead = segment.lookaheadPoint(Vec3d(0.95, 0.0, 0.5), 2.0)

        assertEquals(Vec3d(1.5, 0.0, 0.5), lookahead)
    }

    @Test
    fun `hasReached does not accept a segment when only projected past the end with large drift`() {
        val path = ExecutionPath.fromNodes(
            traversalId = 1,
            nodes = listOf(
                fastVectorOf(0, 0, 0),
                fastVectorOf(2, 0, 0),
            ),
        )

        val segment = assertIs<WalkSegment>(path.segments.single())

        assertFalse(segment.hasReached(Vec3d(2.5, 0.0, 10.5), 0.75, 0.6))
    }
}
