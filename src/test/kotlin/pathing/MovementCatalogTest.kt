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
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.SimpleMoveLibrary
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.movement.*
import com.lambda.pathing.movement.providers.WalkMovement
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.VoxelPos
import com.lambda.util.player.prediction.MovementSimulationInput
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.MovementSimulationStepResult
import com.lambda.util.player.prediction.MovementSimulator
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.floor
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The claim the movement package exists to make: a new way of moving is one file.
 *
 * These tests register a movement that ships with nothing -- its own medium, its own cell
 * predicate, its own template shape -- and check that the graph picks it up, routes over
 * it, and hands its edges back to it for control. Nothing in the coarse graph, the search,
 * the renderer or the plan dump is touched to make that work, which is the whole point.
 */
class MovementCatalogTest {
    @Test
    fun `a registered movement contributes templates and owns its edges`() {
        val catalog = MovementCatalog.build(
            costs = CoarseMoveCosts.measured(),
            options = SimpleMoveOptions(),
            movements = MovementCatalog.REGISTERED + TeleportMovement,
        )

        assertTrue(
            catalog.templates.any { it.movement == TeleportMovement.id },
            "a registered movement's templates must reach the graph",
        )
        assertEquals(
            TeleportMovement, catalog[TeleportMovement.id],
            "an edge must be handed back to the movement that produced it",
        )
        assertTrue(catalog.supports(TeleportMovement.id))
    }

    /**
     * One movement may own several ids.
     *
     * Walking owns the stride, the one-block rise and the one-block step down: they share
     * conditions and a control program and differ only in what the route shows. Indexing
     * the catalogue by the owner's id alone left those edges belonging to nothing, and the
     * search rejected every route containing one as unsupported.
     */
    @Test
    fun `every id a movement contributes resolves back to it`() {
        val catalog = MovementCatalog.build(CoarseMoveCosts.measured(), SimpleMoveOptions())
        for (id in listOf(MovementId.WALK, MovementId.STEP_UP, MovementId.WALK_OFF)) {
            assertEquals(WalkMovement, catalog[id], "$id must resolve to the walking movement")
        }
        catalog.templates.forEach { template ->
            assertNotNull(
                catalog[template.movement],
                "template ${template.movement} has no owning movement",
            )
        }
    }

    /** A movement that is switched off contributes nothing, and costs the graph nothing. */
    @Test
    fun `a disabled movement contributes no templates`() {
        val enabled = MovementCatalog.build(
            CoarseMoveCosts.measured(), SimpleMoveOptions(allowClimbing = true),
        )
        val disabled = MovementCatalog.build(
            CoarseMoveCosts.measured(), SimpleMoveOptions(allowClimbing = false),
        )
        assertTrue(enabled.templates.any { it.movement == MovementId.CLIMB })
        assertTrue(disabled.templates.none { it.movement == MovementId.CLIMB })
    }

    /**
     * A ladder is routable terrain now, not a hole in the map.
     *
     * Climbables used to classify as [Medium.UNKNOWN] -- indistinguishable from unstreamed
     * chunks -- so no movement could ever claim one however it was written.
     */
    @Test
    fun `the graph routes up a ladder column when climbing is enabled`() {
        val world = LadderWorld()
        world.solid(0, 0, 0)
        for (y in 1..4) world.climbable(0, y, 0)

        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(),
            options = SimpleMoveOptions(allowClimbing = true),
        )
        val edges = moves.edgesFrom(world, Stance(0, 1, 0))
        assertTrue(
            edges.any { it.movement == MovementId.CLIMB && it.to == Stance(0, 2, 0) },
            "a body holding a ladder must be offered the cell above it: $edges",
        )
    }

    /**
     * A switched-off movement must vanish completely, not linger as an empty one.
     *
     * Occupancy and templates have to agree. A movement still registered with no edges
     * keeps answering "yes, a body can be here" while offering no way to leave, and the
     * graph fills with ladder cells that are reachable and terminal -- which is what turned
     * a ladder into "no coarse route" rather than into a route that ignored it.
     */
    @Test
    fun `a disabled movement claims no cells either`() {
        val world = LadderWorld()
        world.solid(0, 0, 0)
        for (y in 1..4) world.climbable(0, y, 0)

        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(),
            options = SimpleMoveOptions(allowClimbing = false),
        )
        assertTrue(
            moves.edgesFrom(world, Stance(0, 3, 0)).isEmpty(),
            "with climbing off, a cell only a climber could occupy must not be a stance",
        )
    }

    /**
     * The climbing branch in the simulator, against the simulator.
     *
     * Vanilla re-asserts a fixed rise *after* moving, so what the body actually gains each
     * tick is what gravity and drag leave of it -- about 0.1176 blocks, not the 0.2 the
     * code writes. Getting that backwards would put every climb cost out by 40%.
     */
    @Test
    fun `a body pressed into a ladder rises at the vanilla climbing rate`() {
        val environment = LadderEnvironment(climbable = (0..8).map { BlockPos(0, it, 0) }.toSet())
        val simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            // Inside the ladder's own cell, facing the wall it is mounted on. Vanilla reads
            // the climbable from the block the *body* occupies, not the one it faces.
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 1.0, 0.6),
                rotation = Rotation(0.0, 0.0),
                onGround = true,
            ),
        )

        val start = simulator.state.position.y
        repeat(CLIMB_TICKS) {
            val advanced = simulator.tryTickMovement(
                MovementSimulationInput(forward = 1.0, rotation = Rotation(0.0, 0.0)),
            )
            assertTrue(advanced is MovementSimulationStepResult.Advanced)
        }
        val risen = simulator.state.position.y - start

        assertTrue(
            risen > CLIMB_TICKS * 0.10 && risen < CLIMB_TICKS * 0.13,
            "climbing must rise at roughly 0.1176 blocks per tick, got ${risen / CLIMB_TICKS}",
        )
    }

    /**
     * The rise stops the tick the body leaves the column, not the tick after.
     *
     * Vanilla re-tests the hold at the position the move *ended* at. Reusing the answer from
     * before the move kept a body rising for one tick after it had already climbed off the
     * top of a ladder -- a tenth of a block of pure invention, which is plenty for a
     * certified tape and the live client to disagree about vertical velocity and abort the
     * replay. It only shows up in play, because it needs the body to actually leave the
     * column while still colliding.
     */
    @Test
    fun `a body that climbs off the top of a ladder stops rising immediately`() {
        val topRung = 4
        val environment = LadderEnvironment(climbable = (0..topRung).map { BlockPos(0, it, 0) }.toSet())
        val simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 1.0, 0.6),
                rotation = Rotation(0.0, 0.0),
                onGround = true,
            ),
        )

        // Climb until the feet are past the last rung.
        var leftColumn = false
        repeat(CLIMB_TICKS * 3) {
            if (leftColumn) return@repeat
            simulator.tryTickMovement(
                MovementSimulationInput(forward = 1.0, rotation = Rotation(0.0, 0.0)),
            )
            if (floor(simulator.state.position.y).toInt() > topRung) leftColumn = true
        }
        assertTrue(leftColumn, "the body must climb clear of the ladder to test anything")

        // Off the ladder and still pressed into the wall: the rise must not be re-asserted,
        // so the stored velocity is whatever gravity and drag left of the last one.
        assertTrue(
            simulator.state.velocity.y < CLIMB_RISE_VELOCITY - 1e-6,
            "a body off the ladder must stop rising, but velocity.y is " +
                "${simulator.state.velocity.y}",
        )
    }

    /** Releasing the hold turns the fall into a controlled slide rather than a drop. */
    @Test
    fun `a climbing body falls no faster than the vanilla clamp`() {
        val environment = LadderEnvironment(climbable = (0..20).map { BlockPos(0, it, 0) }.toSet())
        val simulator = MovementSimulator(
            profile = PROFILE,
            environment = environment,
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 18.0, 0.6),
                rotation = Rotation(0.0, 0.0),
                onGround = false,
            ),
        )
        // The clamp is on the movement, not the stored velocity: vanilla applies it before
        // moving and then lets gravity and drag run, so the velocity reads lower than the
        // distance actually covered. Measuring the velocity tests the wrong quantity.
        var worst = 0.0
        repeat(20) {
            val before = simulator.state.position.y
            simulator.tryTickMovement(MovementSimulationInput(rotation = Rotation(0.0, 0.0)))
            worst = maxOf(worst, before - simulator.state.position.y)
        }
        assertTrue(
            worst <= 0.15 + 1e-9,
            "a climbing fall covers at most 0.15 blocks per tick, got $worst",
        )
    }

    /**
     * A whole route up a ladder, from the ground to the top deck.
     *
     * The single-edge test above proves the template matches; this proves D* can actually
     * get somewhere with it, which is what "no coarse route" was about.
     */
    @Test
    fun `a route climbs a ladder from the floor to the top deck`() {
        val world = LadderWorld()
        // Floor, a wall to hang the ladder on, the ladder itself, and a deck at the top.
        for (x in -3..0) world.solid(x, 0, 0)
        for (y in 1..7) world.solid(1, y, 0)
        for (y in 1..6) world.climbable(0, y, 0)
        // The deck stops short of the ladder column, so the top of the ladder stays a
        // climbable cell to step off rather than a solid one that caps it.
        for (x in -3..-1) world.solid(x, 6, 0)

        val moves = SimpleMoveLibrary.build(
            costs = CoarseMoveCosts.measured(transitionOverheadTicks = 1.0),
            options = SimpleMoveOptions(allowClimbing = true),
        )
        val start = Stance(-2, 1, 0)
        val goal = Stance(-2, 7, 0)
        val planner = CoarsePlanner(world, moves, start, goal)
        assertTrue(planner.repair(Duration.INFINITE).converged, "the ladder search must converge")

        val route = assertNotNull(planner.routePlan(0L), "a ladder route must publish")
        assertEquals(goal, route.nodes.last())
        assertTrue(
            route.edges.any { it.movement == MovementId.CLIMB },
            "the only way up is the ladder: ${route.edges.map { it.movement }}",
        )
    }

    /**
     * A movement invented entirely inside this test file.
     *
     * It touches nothing else. If registering it needed an enum case, a renderer branch or
     * a route-validator entry, this would not compile -- which is exactly the regression
     * this guards against.
     */
    private object TeleportMovement : Movement {
        override val id = MovementId("test_teleport")

        override fun templates(context: MovementContext) = listOf(
            TemplateSpec(
                dx = 4, dy = 0, dz = 0,
                movement = id,
                cost = 1.0,
                conditions = WalkMovement.stanceConditions(4, 0, 0),
            )
        )

        override fun decisions(context: DecisionContext) =
            listOf(TrajectoryDecision.Walk(false, context.edge.to, 1, false, id))

        override fun program(context: ProgramContext): ControlProgram =
            ControlProgram { _, observed -> MovementSimulationInput(rotation = observed.rotation) }

        override fun completed(context: CompletionContext) = context.stance != context.body.stance
    }

    /** Air, plus whatever is placed. Solid blocks collide; climbables do not. */
    private class LadderWorld : CoarseVoxelView {
        private val cells = HashMap<VoxelPos, CoarseVoxel>()

        fun solid(x: Int, y: Int, z: Int) {
            cells[VoxelPos(x, y, z)] = CoarseVoxel.FULL_BLOCK
        }

        fun climbable(x: Int, y: Int, z: Int) {
            cells[VoxelPos(x, y, z)] = CoarseVoxel.of(Medium.CLIMBABLE)
        }

        override fun voxel(x: Int, y: Int, z: Int): CoarseVoxel =
            cells[VoxelPos(x, y, z)] ?: CoarseVoxel.AIR

        override fun collisionShape(x: Int, y: Int, z: Int): VoxelShape =
            if (voxel(x, y, z) == CoarseVoxel.FULL_BLOCK) VoxelShapes.fullCube() else VoxelShapes.empty()
    }

    /** A wall of ladders at z = 1 with a floor, for driving the simulator directly. */
    private class LadderEnvironment(private val climbable: Set<BlockPos>) : SimulationEnvironment {
        override fun slipperiness(pos: BlockPos) = 0.6
        override fun velocityMultiplier(pos: BlockPos) = 1.0
        override fun jumpVelocityMultiplier(pos: BlockPos) = 1.0
        override fun isFenceLike(pos: BlockPos) = false
        override fun isClimbable(pos: BlockPos) = pos in climbable

        override fun adjustMovementForCollisions(
            movement: Vec3d,
            boundingBox: Box,
            onGround: Boolean,
            stepHeight: Double,
        ): Vec3d {
            // A solid wall at z >= 1.0 the body presses into, and a floor at y = 0.
            val z = if (boundingBox.maxZ + movement.z > WALL_Z) WALL_Z - boundingBox.maxZ else movement.z
            val y = if (boundingBox.minY + movement.y < 0.0) -boundingBox.minY else movement.y
            return Vec3d(movement.x, y, z)
        }

        override fun findSupportingBlockPos(box: Box, entityPos: Vec3d): BlockPos? =
            if (box.minY <= 1e-6) BlockPos(0, -1, 0) else null

        private companion object {
            const val WALL_Z = 1.0
        }
    }

    private companion object {
        const val CLIMB_TICKS = 20

        /** What the re-asserted 0.2 rise reads as once gravity and drag have run. */
        const val CLIMB_RISE_VELOCITY = (0.2 - 0.08) * 0.98

        val PROFILE = PlayerPhysicsProfile(
            movementSpeed = 0.1, sneakSpeedModifier = 0.3, gravity = 0.08, jumpStrength = 0.42,
            stepHeight = 0.6, jumpBoostVelocityModifier = 0.0, slowFalling = false,
            width = 0.6, height = 1.8, eyeHeight = 1.62,
        )
    }
}
