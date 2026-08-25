/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.movement.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import com.lambda.util.player.prediction.*
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * A ladder, driven by the real planner through the real simulator.
 *
 * The coarse layer already had coverage and already passed: it routes up a ladder column
 * happily. What had none was everything after it, which is where this was reported
 * failing -- a route existed and the body climbed off the ladder instead of up it.
 */
class LadderExecutionTest {
    @Test
    fun `a route up a ladder is certified and arrives on the top deck`() {
        val environment = ladderEnvironment()
        val moves = library()
        val start = Stance(-2, BOTTOM_STANCE, 0)
        val goal = Stance(-2, TOP_STANCE, 0)

        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the ladder route must converge")
        planner.expandField(extraTicks = 60.0, maxExpansions = 40_000)
        val route = assertNotNull(planner.routePlan(0L), "a ladder route must publish")
        assertTrue(
            route.edges.any { it.movement == MovementId.CLIMB },
            "the only way up is the ladder: ${route.edges.map { it.movement }}",
        )

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-1.5, BOTTOM_STANCE.toDouble(), 0.5),
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
            "the ladder must be certified, got $outcome",
        )
        val frames = path.plan.frames
        val landing = frames.last().state.position
        assertTrue(
            floor(landing.y + 1e-6).toInt() >= goal.y,
            "the tape must finish on the top deck, at $landing",
        )

        // Height alone could in principle come from somewhere else, so pin the mechanism:
        // the body must gain most of its height while inside the ladder's own column.
        val inColumn = frames.filter { floor(it.state.position.x).toInt() == -1 }
        val climbed = (inColumn.maxOfOrNull { it.state.position.y } ?: 0.0) -
            (inColumn.minOfOrNull { it.state.position.y } ?: 0.0)
        assertTrue(
            climbed >= 6.0,
            "the height must be won on the ladder, but the column only spans $climbed blocks",
        )
        // Deliberately not asserting that jump is never pressed. Vanilla re-asserts a
        // climbing body's rise on `horizontalCollision || isJumping`, so holding jump on a
        // ladder climbs it just as pressing into the wall does -- the search picks between
        // them on cost, and which one it lands on moves with the wall clock. The height
        // won inside the column above is the claim that matters, and it is the one the
        // reported failure broke: a body that drives off the ladder cannot make it.
    }

    /**
     * The same ladder, downwards.
     *
     * Descending is not the ascent reversed. Vanilla re-asserts a climbing body's rise
     * whenever it is horizontally colliding, so a descent that presses into its hold
     * climbs back up the ladder it is trying to leave -- which is why the press and the
     * facing are decided separately rather than both read off the direction of travel.
     */
    @Test
    fun `a route down a ladder is certified and arrives on the floor`() {
        val environment = ladderEnvironment()
        val moves = library()
        val start = Stance(-2, TOP_STANCE, 0)
        val goal = Stance(-2, BOTTOM_STANCE, 0)

        val planner = CoarsePlanner(environment, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the descent must converge")
        planner.expandField(extraTicks = 60.0, maxExpansions = 40_000)
        val route = assertNotNull(planner.routePlan(0L), "a descent route must publish")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-1.5, TOP_STANCE.toDouble(), 0.5),
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
            "the descent must be certified, got $outcome",
        )
        val landing = path.plan.frames.last().state.position
        assertEquals(
            goal.y, floor(landing.y + 1e-6).toInt(),
            "the tape must finish on the floor, at $landing",
        )
    }

    /**
     * A vine hung with nothing behind it, which only the jump key can climb.
     *
     * The press-into-the-wall technique has no wall here, and holding forward would be
     * actively wrong: it would walk the body straight out of the hitbox that is keeping it
     * up. Vanilla re-asserts a climbing body's rise on `horizontalCollision || isJumping`,
     * so the jump key is the whole mechanism, and the first jump -- taken from the ground,
     * so a real one -- is what puts the body into the column at all.
     */
    @Test
    fun `a backing-less vine is climbed with the jump key`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..0) for (z in -1..1) blocks[BlockPos(x, 62, z)] = SnapshotBlockPhysics.FULL_CUBE
        // A free-hanging column: every cardinal neighbour of every cell is open air.
        for (y in 63..70) blocks[BlockPos(0, y, 0)] = LADDER
        // The deck it leads to, whose blocks sit a level below the cell stepped off into.
        for (x in -4..-1) for (z in -1..1) blocks[BlockPos(x, 69, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-10, 55, -8, 8, 90, 8), blocks,
        )
        val start = Stance(-2, 63, 0)
        val goal = Stance(-2, 70, 0)

        val planner = CoarsePlanner(environment, library(), start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the vine route must converge")
        planner.expandField(extraTicks = 60.0, maxExpansions = 40_000)
        val route = assertNotNull(planner.routePlan(0L), "a vine route must publish")
        assertTrue(
            route.edges.any { it.movement == MovementId.CLIMB },
            "the only way up is the vine: ${route.edges.map { it.movement }}",
        )

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-1.5, 63.0, 0.5),
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
            "the vine must be certified, got $outcome",
        )
        val frames = path.plan.frames
        assertEquals(
            goal.y, floor(frames.last().state.position.y + 1e-6).toInt(),
            "the tape must finish on the deck, at ${frames.last().state.position}",
        )
        val inColumn = frames.filter { floor(it.state.position.x).toInt() == 0 }
        assertTrue(
            inColumn.any { it.input.jump },
            "with no wall to press, the rise can only come from the jump key",
        )
    }

    /**
     * A ladder taller than one horizon window, which used to be the height limit.
     *
     * A leg is only committed where it can be braked to a stable *grounded* stop, and a
     * ladder offers no ground anywhere along it -- so the whole climb has to fit inside one
     * window or nothing commits at all. At the vanilla climb rate that made the shipping
     * 80-frame window worth about ten rungs, and taller ladders parked the body at the foot
     * of the ladder where the heading fan walked it off sideways.
     *
     * Thirty rungs is comfortably past the old ceiling and past twice it, so this fails if
     * the window ever stops being sized to the route's climbing again.
     */
    @Test
    fun `a ladder taller than one horizon window is climbed to the top`() {
        val top = BOTTOM_STANCE + 30
        val environment = ladderEnvironment(top)
        val start = Stance(-2, BOTTOM_STANCE, 0)
        val goal = Stance(-2, top, 0)

        val planner = CoarsePlanner(environment, library(), start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the tall shaft must converge")
        planner.expandField(extraTicks = 400.0, maxExpansions = 200_000)
        val route = assertNotNull(planner.routePlan(0L), "a tall ladder route must publish")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-1.5, BOTTOM_STANCE.toDouble(), 0.5),
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
            "a tall ladder must be certified, got $outcome",
        )
        val landing = path.plan.frames.last().state.position
        assertEquals(
            top, floor(landing.y + 1e-6).toInt(),
            "the tape must reach the top deck, at $landing",
        )
    }

    /**
     * A ladder whose column starts *below* the deck it serves, which is how most are built.
     *
     * Climbing out of such a shaft works: the body reaches the top rung and a walking
     * step-up carries it onto the deck. Going the other way had no mirror. Every climb
     * template was either purely vertical or purely horizontal, so from a deck stance there
     * was no edge at all into a climbable one level down -- the route simply stopped at the
     * lip, and the body milled about on the edge looking for a way in.
     *
     * The move itself is nothing exotic: step off the ledge and let the ladder's own volume
     * catch you.
     */
    @Test
    fun `a ladder entered from the deck above it is climbed down`() {
        val environment = topEntryShaft()
        val start = Stance(-2, DECK_STANCE, 0)
        val goal = Stance(-2, BOTTOM_STANCE, 0)

        val planner = CoarsePlanner(environment, library(), start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the descent must converge")
        planner.expandField(extraTicks = 200.0, maxExpansions = 80_000)
        val route = assertNotNull(planner.routePlan(0L), "a descent route must publish")
        assertTrue(
            route.edges.any { it.movement == MovementId.CLIMB },
            "the shaft is the only way down: ${route.edges.map { it.movement }}",
        )

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(-1.5, DECK_STANCE.toDouble(), 0.5),
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
            "the descent must be certified, got $outcome",
        )
        val landing = path.plan.frames.last().state.position
        assertEquals(
            BOTTOM_STANCE, floor(landing.y + 1e-6).toInt(),
            "the tape must reach the bottom landing, at $landing",
        )
    }

    /**
     * A shaft whose top rung sits one below the deck, so it has to be entered over the lip.
     */
    private fun topEntryShaft(): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -3..0) for (z in -1..1) for (y in BOTTOM_STANCE - 1..DECK_STANCE + 2) {
            blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
        }
        for (y in LADDER_BOTTOM..DECK_STANCE - 1) blocks[BlockPos(-1, y, 0)] = LADDER
        // Headroom over the top rung, and over the deck.
        for (y in DECK_STANCE..DECK_STANCE + 1) blocks.remove(BlockPos(-1, y, 0))
        for (x in -3..-2) {
            for (y in BOTTOM_STANCE..BOTTOM_STANCE + 1) blocks.remove(BlockPos(x, y, 0))
            for (y in DECK_STANCE..DECK_STANCE + 1) blocks.remove(BlockPos(x, y, 0))
        }
        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-10, 40, -8, 8, DECK_STANCE + 20, 8), blocks,
        )
    }

    private fun library() = SimpleMoveLibrary.build(
        costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
        options = SimpleMoveOptions(allowClimbing = true),
    )

    /**
     * A ladder shaft cut through solid rock, with a landing at each end.
     *
     * Enclosed on purpose. An open room beside the column gives the value field a cheaper
     * way down than the ladder -- a three-block drop is a handful of ticks where nine rungs
     * are most of a second -- and the search then spends itself on a shortcut that the
     * simulator refuses as a harmful fall. That is a fair thing to know about the cost
     * model, but it is not what this test is for: here the ladder is the only way through,
     * so what is measured is whether the body can ride it.
     */
    private fun ladderEnvironment(top: Int = TOP_STANCE): SnapshotSimulationEnvironment {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -3..0) for (z in -1..1) for (y in BOTTOM_STANCE - 1..top + 2) {
            blocks[BlockPos(x, y, z)] = SnapshotBlockPhysics.FULL_CUBE
        }
        // The shaft itself, hung on the wall at x = 0.
        for (y in LADDER_BOTTOM..top) blocks[BlockPos(-1, y, 0)] = LADDER
        blocks.remove(BlockPos(-1, top + 1, 0))
        // A landing at each end, opening west out of the column.
        for (x in -3..-2) {
            for (y in BOTTOM_STANCE..BOTTOM_STANCE + 1) blocks.remove(BlockPos(x, y, 0))
            for (y in top..top + 1) blocks.remove(BlockPos(x, y, 0))
        }

        return SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-10, 40, -8, 8, top + 20, 8), blocks,
        )
    }

    private companion object {
        const val LADDER_BOTTOM = 63
        const val LADDER_TOP = 72
        const val BOTTOM_STANCE = 63
        const val TOP_STANCE = 72

        /** A deck one level above the ladder's top rung, so the shaft is entered over a lip. */
        const val DECK_STANCE = 73

        val LADDER = SnapshotBlockPhysics(
            collisionShape = VoxelShapes.empty(),
            coarseVoxel = CoarseVoxel.of(Medium.CLIMBABLE),
        )

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
