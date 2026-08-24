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
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementId
import com.lambda.util.player.prediction.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * The primitive the planner used to lack, end to end.
 *
 * A ledge with a narrow pad below it is the case that separates a drop from everything
 * else. Walking off carries whatever speed the approach left, and a sprint clears 1.26
 * blocks off a one-block ledge, so it lands past the pad. Jumping off is worse again: it
 * adds height, air time and fall distance to a move whose purpose is to lose height.
 */
class DropPrimitiveTest {
    @Test
    fun `a descent past one block is planned as a drop, not a jump or a walk off`() {
        val moves = library()
        val edges = moves.edgesFrom(ledge(), Stance(0, 66, 0))
            .filter { it.to == Stance(1, 63, 0) }

        val drop = assertNotNull(
            edges.firstOrNull { it.movement == MovementId.DROP },
            "a three-block descent onto a clear pad must be offered as a drop: $edges",
        )
        assertTrue(
            edges.none { it.movement == MovementId.WALK_OFF },
            "walk-off must not claim a descent it cannot control",
        )
        val launch = assertNotNull(drop.launch, "a drop must publish the take-off that makes it work")
        assertTrue(launch.mode.drops, "a drop must not be solved as a jump")
    }

    /**
     * The number that makes the primitive necessary.
     *
     * Everything about the drop follows from a sprint walk-off overshooting the block it
     * was aimed at, so it is worth asserting directly rather than inferring from a plan.
     */
    @Test
    fun `a sprint walk off overshoots the pad a controlled drop lands on`() {
        val profile = BallisticProfile.VANILLA
        val sprinting = assertNotNull(
            profile.fly(LaunchMode.SPRINT_DROP, profile.cruiseSpeed(sprint = true), rise = -1),
        )
        assertTrue(
            sprinting.distance > 1.0,
            "a sprint walk-off must clear more than one block (${sprinting.distance})",
        )

        val crawling = assertNotNull(profile.fly(LaunchMode.WALK_DROP, 0.0, rise = -1, holdForward = false))
        assertTrue(
            crawling.distance < 1.0,
            "a controlled leave must stay inside the adjacent pad (${crawling.distance})",
        )
    }

    /**
     * The whole planner, over a ledge, with the tape checked frame by frame.
     *
     * Two properties, and the first is the interesting one: the certified tape presses no
     * jump anywhere. The old planner would happily leap off this ledge, because the only
     * way it had to leave a surface deliberately was the jump key.
     */
    @Test
    fun `the certified tape descends a ledge without ever pressing jump`() {
        val environment = ledgeEnvironment()
        val moves = library()
        val start = Stance(0, 66, 0)
        val goal = Stance(4, 63, 0)
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the ledge route must converge")
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = assertNotNull(planner.routePlan(0L), "a route down the ledge must publish")

        assertTrue(
            route.edges.any { it.movement == MovementId.DROP },
            "the route down must use the drop primitive: ${route.edges.map { it.movement }}",
        )

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 66.0, 0.5),
                rotation = Rotation(-90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )

        val path = assertNotNull(
            (outcome as? PathPlanResult.Planned)?.path,
            "the ledge must be certified, got $outcome",
        )
        val frames = path.plan.frames
        assertTrue(frames.none { it.input.jump }, "descending a ledge must never press jump")

        val landing = frames.last().state.position
        assertEquals(
            goal.y, floor(landing.y + 1e-6).toInt(),
            "the tape must finish on the lower deck, at $landing",
        )
    }

    /**
     * A staircase of single blocks, which is where this was reported failing.
     *
     * Each tread is one block down and one across, and one block *wide*: there is no room
     * to overshoot onto. A sprint walk-off covers 1.26 blocks, so leaving a tread with any
     * speed clears the next one -- and leaving it airborne clears two. The planner used to
     * lead with a jump here because a one-block step down is not the walk movement's own
     * id, so the search read it as ballistic and tried to leap the whole staircase.
     */
    @Test
    fun `a narrow descending staircase is walked down, not jumped down`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        // A landing to start on, then seven single-block treads descending diagonally.
        for (x in -3..0) for (z in -1..1) blocks[BlockPos(x, 70, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (i in 1..7) blocks[BlockPos(i, 70 - i, 0)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in 8..11) for (z in -1..1) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 55, -6, 16, 85, 6), blocks,
        )
        val moves = library()
        val start = Stance(-1, 71, 0)
        val goal = Stance(9, 64, 0)
        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the staircase must converge")
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = assertNotNull(planner.routePlan(0L), "a staircase route must publish")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-0.5, 71.0, 0.5),
                rotation = Rotation(-90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )

        val path = assertNotNull(
            (outcome as? PathPlanResult.Planned)?.path,
            "the staircase must be certified, got $outcome",
        )
        val frames = path.plan.frames
        // Scoped to the descent rather than the whole tape. A hop taken on the flat
        // approach is the planner's ordinary vocabulary -- the blind straight-bearing
        // launches ActionSet keeps first are jumps, and they earn their place across the
        // corpus. What must never happen is leaving the ground *on a tread*, where the
        // landing is one block wide and a leap clears two of them.
        val descent = frames.dropWhile { it.state.position.y >= start.y.toDouble() }
        val jumps = descent.filter { it.input.jump }
        assertTrue(
            jumps.isEmpty(),
            "a staircase of single treads must be walked down, but the tape jumps at " +
                jumps.joinToString { "frame ${it.index} ${it.state.position}" },
        )
        assertEquals(
            goal.y, floor(frames.last().state.position.y + 1e-6).toInt(),
            "the tape must finish on the bottom landing",
        )
    }

    /**
     * Consecutive two-block drops, which is the shape this was reported failing on.
     *
     * A lone drop onto a wide deck always worked, and that is exactly what hid the bug:
     * the solver's landing window bounds where the body's *centre* may come down, so its
     * aim sat three tenths of a body past the pad. On a deck the far edge is metres away
     * and nobody notices. Here every pad is one block wide, so the aim was the edge, the
     * body came down overhanging it and carried straight on -- falling the entire
     * staircase in one go. Every drop in the route failed as a harmful fall, no motion
     * could be certified at all, and what the player saw was the bot leaping off the top.
     */
    @Test
    fun `a staircase of two block drops is certified and descends one tread at a time`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -3..0) for (z in -1..1) blocks[BlockPos(x, 70, z)] = SnapshotBlockPhysics.FULL_CUBE
        // Four treads, each two blocks below the last and only one block wide.
        for (i in 1..4) blocks[BlockPos(i, 70 - i * 2, 0)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in 5..8) for (z in -1..1) blocks[BlockPos(x, 62, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-8, 54, -6, 13, 85, 6), blocks,
        )
        val start = Stance(-1, 71, 0)
        val goal = Stance(6, 63, 0)
        val planner = CoarsePlanner(environment, library(), start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the staircase must converge")
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = assertNotNull(planner.routePlan(0L), "a staircase route must publish")
        assertTrue(
            route.edges.count { it.movement == MovementId.DROP } >= 4,
            "each two-block tread is a drop: ${route.edges.map { it.movement }}",
        )

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-0.5, 71.0, 0.5),
                rotation = Rotation(-90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )

        val path = assertNotNull(
            (outcome as? PathPlanResult.Planned)?.path,
            "consecutive drops must be certified, got $outcome",
        )
        val frames = path.plan.frames
        assertEquals(
            goal.y, floor(frames.last().state.position.y + 1e-6).toInt(),
            "the tape must finish on the bottom landing, at ${frames.last().state.position}",
        )
        // The failure mode was falling the whole flight at once, so assert it touched
        // down on the way: no single unbroken fall may span more than one tread.
        val longestFall = frames.fold(0 to 0) { (run, worst), frame ->
            if (frame.state.onGround) 0 to worst else (run + 1) to maxOf(worst, run + 1)
        }.second
        assertTrue(
            longestFall <= MAX_SINGLE_TREAD_AIR_FRAMES,
            "the body must land on each tread, but stayed airborne $longestFall frames",
        )
    }

    /**
     * The tape a descent hands the live client must not chatter the forward key.
     *
     * This is the shape of a failure that only ever appeared in play: a certified tape
     * aborting mid-descent on `Flag(name=sprinting, expected=false, actual=true)` with the
     * position and velocity matching to the last decimal. Only the flag disagreed.
     *
     * The cause was the approach regulating its speed by releasing and re-pressing forward.
     * A released-then-pressed forward is vanilla's double-tap-to-sprint whether a human or
     * a tape does it, so a program trying to hold a crawl kept asking for a sprint it never
     * wanted -- and the simulator models that window, but not tick for tick, so the two
     * ended up disagreeing about a flag the executor compares every frame.
     *
     * Asserted as a property of the published tape rather than of the program, because that
     * is the thing the client actually replays.
     */
    @Test
    fun `a descending tape never re-presses forward inside the double-tap sprint window`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in 2 downTo 0) for (z in -1..1) blocks[BlockPos(x, 16, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (i in 1..3) blocks[BlockPos(-i, 16 - i * 2, 0)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -4 downTo -7) for (z in -1..1) blocks[BlockPos(x, 10, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, 0, -8, 8, 40, 8), blocks,
        )
        val start = Stance(0, 17, 0)
        val goal = Stance(-5, 11, 0)
        val planner = CoarsePlanner(environment, library(), start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the staircase must converge")
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = assertNotNull(planner.routePlan(0L), "a staircase route must publish")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 17.0, 0.5),
                rotation = Rotation(90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )

        val tape = assertNotNull(
            (outcome as? PathPlanResult.Planned)?.path,
            "the descent must be certified, got $outcome",
        ).plan.tape

        var sinceRelease = Int.MAX_VALUE
        var lastPress = Int.MIN_VALUE
        for (frame in 0 until tape.frameCount) {
            val forward = tape[frame].forward > FORWARD_EPSILON
            if (!forward) {
                sinceRelease = if (sinceRelease == Int.MAX_VALUE) 0 else sinceRelease + 1
                continue
            }
            if (sinceRelease != Int.MAX_VALUE) {
                assertTrue(
                    frame - lastPress > DOUBLE_TAP_WINDOW_TICKS,
                    "frame $frame re-presses forward ${frame - lastPress} ticks after the last " +
                        "press, inside vanilla's ${DOUBLE_TAP_WINDOW_TICKS}-tick double-tap window",
                )
                lastPress = frame
                sinceRelease = Int.MAX_VALUE
            } else if (lastPress == Int.MIN_VALUE) {
                lastPress = frame
            }
        }
    }

    /**
     * A descent must stay on its line, not just leave at the right speed.
     *
     * Along-track speed is what the solver reasons about and what the approach regulates.
     * Nothing was watching the other axis: a coasting drop holds no key at all in the air,
     * so yaw does not steer it, and a lip left a few hundredths off the line lands a third
     * of a block to the side. On the open ground the corpus measures that is invisible; on a
     * one-block tread it is the difference between standing on it and standing on nothing.
     *
     * The airborne strafe trim is what closes it, and it is on strafe rather than forward
     * because forward is the key vanilla's double-tap sprint window watches.
     */
    @Test
    fun `a descent holds its line through the air`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in 2 downTo 0) for (z in -1..1) blocks[BlockPos(x, 16, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (i in 1..3) blocks[BlockPos(-i, 16 - i * 2, 0)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in -4 downTo -7) for (z in -1..1) blocks[BlockPos(x, 10, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, 0, -8, 8, 40, 8), blocks,
        )
        val planner = CoarsePlanner(environment, library(), Stance(0, 17, 0), Stance(-5, 11, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged, "the staircase must converge")
        planner.expandField(extraTicks = 36.0, maxExpansions = 20_000)
        val route = assertNotNull(planner.routePlan(0L), "a staircase route must publish")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 17.0, 0.5),
                rotation = Rotation(90.0, 0.0),
                velocity = Vec3d(0.0, -0.0784, 0.0),
                onGround = true,
            ),
            PROFILE, environment, MotionConstraints(),
            cursorFrame = { null },
            publish = { _, _ -> },
            started = System.currentTimeMillis(),
        )

        val frames = assertNotNull(
            (outcome as? PathPlanResult.Planned)?.path,
            "the descent must be certified, got $outcome",
        ).plan.frames

        // Every tread on this staircase is one block wide and centred on z = 0.5, so the
        // lateral error is exactly the distance from that line.
        val worst = frames.maxOf { abs(it.state.position.z - TREAD_CENTRE_Z) }
        assertTrue(
            worst <= MAX_LATERAL_DRIFT,
            "the descent drifted $worst blocks off the line of the treads",
        )
    }

    private fun library() = SimpleMoveLibrary.build(
        costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
        options = SimpleMoveOptions(maxWalkOffDepth = 3),
    )

    /** An upper deck at y = 66 ending at x = 0, and a lower deck three blocks down. */
    private fun ledgeBlocks(): Map<BlockPos, SnapshotBlockPhysics> = buildMap {
        for (x in -8..0) for (z in -2..2) put(BlockPos(x, 65, z), SnapshotBlockPhysics.FULL_CUBE)
        for (x in 1..8) for (z in -2..2) put(BlockPos(x, 62, z), SnapshotBlockPhysics.FULL_CUBE)
    }

    private fun ledgeEnvironment() = SnapshotSimulationEnvironment.synthetic(
        SimulationSnapshotBounds(-12, 55, -6, 12, 80, 6), ledgeBlocks(),
    )

    private fun ledge() = ledgeEnvironment()

    private companion object {
        const val TREAD_CENTRE_Z = 0.5

        /**
         * Lateral error a one-block tread tolerates, in blocks.
         *
         * Half the body's width is 0.3, so a third of a block still leaves most of it over
         * the tread. Untrimmed this run drifts past 0.4, which hangs a fifth of the body
         * off the side.
         *
         * This held 0.14 under the old forward-key brake and regressed to 0.31 when the lip
         * pin replaced it. Both numbers were the same bug: the simulator was reading a sneak
         * ledge clip as a wall collision and killing the body's velocity, so the descent left
         * the lip on a line the arc was never solved for. With the clip modelled correctly
         * the run holds its line to under a hundredth of a block, and the bound is back to
         * the tight number with room to spare rather than loosened around the symptom.
         */
        const val MAX_LATERAL_DRIFT = 0.14

        /** Vanilla's window for turning a released-then-pressed forward into a sprint. */
        const val DOUBLE_TAP_WINDOW_TICKS = 7

        const val FORWARD_EPSILON = 1.0E-5

        /**
         * Air frames a single two-block tread costs, with room for the approach.
         *
         * Falling two blocks is about seven ticks; falling the whole four-tread flight is
         * over twenty. The gap between those is wide enough that the bound does not need
         * to be tight to catch the failure it is here for.
         */
        const val MAX_SINGLE_TREAD_AIR_FRAMES = 12

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
