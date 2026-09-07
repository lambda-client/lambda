/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.VoxelPos
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.SearchExhaustion
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * The plan DAG's first producer: a world change under the running tape's tail cuts the
 * plan at the last junction ahead of the body and re-solves from there, keeping the
 * prefix the body is replaying. See docs/decisions/publication-protocol.md (local repair).
 */
class DagRepairTest {
    private val wallX = 24

    private fun floorSection(sectionX: Int, withWall: Boolean): ImmutableSnapshotSection =
        ImmutableSnapshotSection.Builder().apply {
            for (x in 0..15) for (z in 0..15) for (y in 0..15) {
                val worldX = (sectionX shl 4) + x
                val solid = y <= 3 || (withWall && worldX == wallX && z == 0 && y in 4..5)
                set(x, y, z, if (solid) SnapshotBlockPhysics.FULL_CUBE else SnapshotBlockPhysics.AIR)
            }
        }.build(expectedWrites = 4096)

    @Test
    fun `a wall dropped under the published tail is repaired from a junction, not from rest`() {
        val sections = HashMap<Long, ImmutableSnapshotSection>()
        for (sx in -1..3) for (sz in -1..1) sections[ChunkSectionPos.asLong(sx, 0, sz)] = floorSection(sx, withWall = false)
        val environment = SnapshotSimulationEnvironment(
            bounds = SimulationSnapshotBounds(-16, 0, -16, 63, 15, 31),
            sections = sections,
            defaultBlock = SnapshotBlockPhysics.AIR,
        )
        val start = Stance(2, 4, 0)
        val goal = Stance(45, 4, 0)
        val moves = moveLibrary(SimpleMoveOptions())
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged)
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = checkNotNull(planner.routePlan(0L))

        val initial = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = Vec3d(start.x + 0.5, start.y.toDouble(), start.z + 0.5),
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, -0.0784, 0.0),
            onGround = true,
        )
        val clock = VirtualSearchClock()
        var firstTape: List<com.lambda.pathing.physics.MovementSimulationInput>? = null
        var lastTape: List<com.lambda.pathing.physics.MovementSimulationInput>? = null
        var publishedFrames = 0
        var mutated = false
        var exhaustion: SearchExhaustion? = null
        val wallSection = PathingSection(wallX shr 4, 0, 0)

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner, initial, PROFILE, environment, MotionConstraints(),
            cursorFrame = { clock.cursorFrame() },
            publish = { path, _ ->
                if (firstTape == null) firstTape = path.plan.tape.asList()
                lastTape = path.plan.tape.asList()
                publishedFrames = path.plan.tape.frameCount
            },
            started = System.currentTimeMillis(),
            clock = clock,
            worldSync = {
                // Once the body is under way and the tape reaches past the wall, drop it.
                if (!mutated && clock.cursorFrame() >= 10 && publishedFrames >= 70) {
                    mutated = true
                    environment.install(ChunkSectionPos.asLong(wallX shr 4, 0, 0), floorSection(wallX shr 4, withWall = true))
                    planner.worldChanged(listOf(VoxelPos(wallX, 4, 0), VoxelPos(wallX, 5, 0)))
                    planner.repair(Duration.INFINITE)
                    WorldSyncResult.Changed(planner.routePlan(1L), setOf(wallSection))
                } else WorldSyncResult.Quiet
            },
            onExhaustion = { exhaustion = it },
        )

        val planned = assertNotNull((outcome as? PathPlanResult.Planned)?.path, "no plan: $outcome")
        assertTrue(mutated, "the wall must have been dropped during the walk")
        assertTrue(!planned.partial, "the walk must arrive despite the wall")
        val report = assertNotNull(exhaustion)
        assertEquals(1, report.repairs, "exactly one repair expected: $report")

        val frames = planned.plan.frames
        assertTrue(
            frames.none { floor(it.state.position.x).toInt() == wallX && floor(it.state.position.z).toInt() == 0 && it.state.position.y < 6.0 },
            "the repaired tape must not pass through the wall cell",
        )
        val first = assertNotNull(firstTape)
        val final = planned.plan.tape.asList()
        val shared = first.zip(final).takeWhile { (a, b) -> a == b }.size
        assertTrue(shared >= 8, "the repaired tape must keep the executed prefix, shared only $shared frames")
        println("[repair] first=${first.size} final=${final.size} shared=$shared repairs=${report.repairs}")
    }
}
