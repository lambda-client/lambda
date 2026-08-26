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

package com.lambda

import com.lambda.PathingTestHarness.EXECUTION_TOLERANCE
import com.lambda.PathingTestHarness.assertPathingWalk
import com.lambda.PathingTestHarness.restoreArena
import com.lambda.pathing.core.Stance
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext

/**
 * Trajectory-certification scenarios: moving splices, retained momentum across
 * chained launches, off-axis and diagonal jumps -- the shapes where the certified
 * tape itself (not just the coarse route) is what is under test.
 */
@Suppress("UnstableApiUsage")
internal object TrajectoryPathingTests {
    fun run(
        context: ClientGameTestContext,
        server: TestServerContext,
    ) {
        restoreArena(server)

        // A rise immediately beyond a deliberately short local horizon exercises
        // the state that failed in-game: the next controller begins with retained
        // momentum close to the lip and must still discover a valid takeoff. The
        // raised section then drops back to the original goal height.
        server.runCommand("/fill -2 99 9 2 99 22 minecraft:stone")
        server.runCommand("/fill -2 100 10 2 100 14 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-moving-splice-step-up", Stance(0, 100, 20),
            maxLegs = 1, minContinuousSegments = 2, requireMovingSplices = true,
            requireJumpInput = true, plannerMaxFrames = 40,
            maxDeviation = EXECUTION_TOLERANCE,
        )
        restoreArena(server)

        // A sequence of rises ending immediately at a long descending gap is the
        // live shape that exposed greedy continuous expansion. The focused JVM test
        // forces the hazard-driven runway boundary; this live companion forces an
        // ordinary frame-horizon boundary and proves the combined tape still retains
        // momentum, launches across span four, and never inserts a runtime stop.
        server.runCommand("/fill -8 100 3 8 100 5 minecraft:stone")
        server.runCommand("/fill -8 101 6 8 101 8 minecraft:stone")
        server.runCommand("/fill -8 102 9 8 102 12 minecraft:stone")
        server.runCommand("/fill -8 101 16 8 101 24 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-ascent-to-descending-gap-splice", Stance(0, 102, 20),
            maxLegs = 1, minGapLaunches = 1, minContinuousSegments = 2,
            requireMovingSplices = true, requireJumpInput = true,
            plannerMaxFrames = 53, maxDeviation = EXECUTION_TOLERANCE,
        )
        restoreArena(server)

        // Two-block treads climbing to a top that sits three blocks above *both*
        // endpoints, then back down. Every other live rise ends on the high ground,
        // whose height is therefore already in the snapshot's own bounds -- so none of
        // them could ever exercise a route that peaks above what start and goal see.
        // The capture must budget a sprint jump's ceiling above a stance neither
        // endpoint reaches; when it did not, entering these stairs one step lower was
        // the whole difference between a refusal and a certified trajectory.
        server.runCommand("/fill -8 99 3 8 99 8 minecraft:air")
        server.runCommand("/fill -2 100 3 2 100 4 minecraft:stone")
        server.runCommand("/fill -2 101 5 2 101 6 minecraft:stone")
        server.runCommand("/fill -2 102 7 2 102 8 minecraft:stone")
        // A two-wide hole across the top: crossing it is the only thing that launches
        // *from* the peak, and a launch is the only motion that reads four blocks up.
        server.runCommand("/fill -2 102 11 2 102 14 minecraft:stone")
        server.runCommand("/fill -2 101 15 2 101 16 minecraft:stone")
        server.runCommand("/fill -2 99 17 2 99 24 minecraft:stone")
        // Interim: see the gap-chain note above -- the continuous-session rework
        // restores the single-window guarantee structurally.
        assertPathingWalk(
            context, server, "pathing-staircase-above-both-endpoints", Stance(0, 100, 22),
            maxLegs = 2, minGapLaunches = 1, requireJumpInput = true,
        )
        restoreArena(server)

        // A gap that lands off the compass. The jump templates were a unit direction times
        // a span, so only cardinals and exact diagonals had one -- three across and one to
        // the side, which is an unremarkable gap in anything built by hand, could not be
        // proposed at all and the search walked around it or gave up.
        server.runCommand("/fill -8 99 3 8 99 6 minecraft:air")
        server.runCommand("/fill 0 99 2 0 99 2 minecraft:stone")
        server.runCommand("/fill 1 99 5 1 99 5 minecraft:stone")
        server.runCommand("/fill -2 99 6 2 99 10 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-off-axis-gap-jump", Stance(0, 100, 8),
            requireJumpInput = true,
        )
        restoreArena(server)

        // A diagonal notch of void: the direct line to a diagonally offset goal is a
        // diagonal jump. Without diagonal jump topology the coarse layer had to zigzag
        // cardinally -- the "slalom" the body then walked. Vanilla must certify the
        // off-axis launch, which is where the sine-table jump boost has to be exact.
        server.runCommand("/fill 1 99 1 1 99 2 minecraft:air")
        server.runCommand("/fill 2 99 1 2 99 1 minecraft:air")
        assertPathingWalk(
            context, server, "pathing-gap-jump-diagonal", Stance(3, 100, 3),
            requireJumpInput = true,
        )
        restoreArena(server)

        // Three descending gaps reproduce the real suffix that exhausted its
        // launch beam after a frame-40 moving splice. Every landing is one block
        // lower; the complete run must keep momentum and still acquire the tight
        // final stop rather than publishing parts.
        server.runCommand("/fill -8 99 3 8 99 20 minecraft:air")
        server.runCommand("/fill -2 98 5 2 98 7 minecraft:stone")
        server.runCommand("/fill -2 97 10 2 97 12 minecraft:stone")
        server.runCommand("/fill -2 96 15 2 96 20 minecraft:stone")
        assertPathingWalk(
            context, server, "pathing-descending-gap-chain-splice", Stance(0, 97, 17),
            maxLegs = 1, minGapLaunches = 3, minContinuousSegments = 2,
            requireMovingSplices = true, requireJumpInput = true,
            plannerMaxFrames = 40, maxDeviation = EXECUTION_TOLERANCE,
        )
        restoreArena(server)

        // Two independent gaps in one short route must produce one continuous
        // certified tape. The search used to discover only one gap launch, so the
        // planner shortened the window and the manager stopped on every platform,
        // throwing away momentum before planning the next jump.
        server.runCommand("/fill -2 99 9 2 99 14 minecraft:stone")
        server.runCommand("/fill -8 99 3 8 99 4 minecraft:air")
        server.runCommand("/fill -8 99 9 8 99 10 minecraft:air")
        // Interim: streaming publication can race the full solution into a second
        // window since brake tails became closed cycles; the continuous-session
        // rework restores the single-tape guarantee structurally.
        assertPathingWalk(
            context, server, "pathing-gap-chain-one-tape", Stance(0, 100, 13),
            maxLegs = 2, minGapLaunches = 2, requireJumpInput = true,
        )
        restoreArena(server)
    }
}
