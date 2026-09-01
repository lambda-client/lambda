/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.PathPlanResult
import com.lambda.pathing.TrajectoryPlanner
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.movement.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.prediction.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * Terrain that is not made of whole cubes.
 *
 * The simulator has always been shape-correct -- it stores each block's real collision
 * shape and resolves against it -- but the coarse graph reduced a cell to "is its top face
 * a full solid square". Under that question a bottom slab is neither standable nor
 * passable, which makes it a *wall*: the planner refused to route over terrain the body
 * would have walked without noticing.
 */
class BlockShapeStanceTest {
    @Test
    fun `a body can stand on a slab`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds(),
            buildMap {
                for (x in -4..4) for (z in -4..4) put(BlockPos(x, 63, z), SnapshotBlockPhysics.FULL_CUBE)
                put(BlockPos(0, 64, 0), slab())
            },
        )
        assertTrue(
            library().isStance(environment, Stance(0, 65, 0)),
            "a bottom slab carries a stance half a block above the floor",
        )
    }

    @Test
    fun `a body can stand on a snow layer and on stairs`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds(),
            buildMap {
                for (x in -4..4) for (z in -4..4) put(BlockPos(x, 63, z), SnapshotBlockPhysics.FULL_CUBE)
                put(BlockPos(0, 64, 0), snowLayer(2))
                put(BlockPos(1, 64, 0), stairs())
            },
        )
        val moves = library()
        assertTrue(moves.isStance(environment, Stance(0, 65, 0)), "a snow layer is standable")
        assertTrue(moves.isStance(environment, Stance(1, 65, 0)), "a stairs block is standable")
    }

    /**
     * The case the whole thing is for: a run of slabs is a ramp, not a wall.
     *
     * Each riser is half a block, which the body clears by walking -- there is no jump in
     * a slab staircase, and a planner that cannot see the slabs cannot see that.
     */
    @Test
    fun `a staircase of slabs is a route`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..8) for (z in -2..2) blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
        // Alternating slab, then full block, then slab-on-block: a half-step ramp.
        blocks[BlockPos(1, 64, 0)] = slab()
        blocks[BlockPos(2, 64, 0)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(3, 64, 0)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(3, 65, 0)] = slab()
        blocks[BlockPos(4, 64, 0)] = SnapshotBlockPhysics.FULL_CUBE
        blocks[BlockPos(4, 65, 0)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(bounds(), blocks)
        val planner = CoarsePlanner(environment, library(), Stance(0, 64, 0), Stance(4, 66, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged, "the slab ramp must converge")
        val route = assertNotNull(planner.routePlan(0L), "a slab ramp must publish a route")

        assertEquals(Stance(4, 66, 0), route.nodes.last(), "the route must reach the top of the ramp")
        assertTrue(
            route.nodes.contains(Stance(1, 65, 0)),
            "the route must stand on the slab rather than route around it: ${route.nodes}",
        )
    }

    /**
     * The claim the coarse layer makes about a slab, checked by the simulator.
     *
     * A route is only a claim that terrain exists; the tape is what says the body can walk
     * it. Worth asserting together because the two layers name a stance differently -- the
     * graph from the cell that holds the body up, the search from where the body's feet
     * are -- and on whole blocks those are the same number. On a slab they are not, and a
     * tape that reached the right place under the wrong name never certified at all.
     */
    @Test
    fun `a tape certifies while walking on a slab surface`() {
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..10) for (z in -2..2) {
            blocks[BlockPos(x, 63, z)] = SnapshotBlockPhysics.FULL_CUBE
            blocks[BlockPos(x, 64, z)] = slab()
        }
        val environment = SnapshotSimulationEnvironment.synthetic(bounds(), blocks)

        val planner = CoarsePlanner(environment, library(), Stance(0, 65, 0), Stance(5, 65, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged, "a slab floor must converge")
        planner.expandField(extraTicks = 36.0, timeBudget = Duration.INFINITE, maxExpansions = 20_000)
        val route = assertNotNull(planner.routePlan(0L), "a slab floor must publish a route")

        val outcome = TrajectoryPlanner.walkHorizon(
            route, planner,
            MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 64.5, 0.5),
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
            "a walk across slabs must be certified, got $outcome",
        )
        // Every grounded frame must be on the slab tops. Airborne frames are not asserted:
        // a sprint jump across open ground is ordinary and faster, and has nothing to do
        // with the shape of what is underneath.
        val grounded = path.plan.frames.filter { it.state.onGround }
        assertTrue(grounded.isNotEmpty(), "the tape must spend time on the ground")
        grounded.forEach {
            assertEquals(
                64.5, it.state.position.y, 1e-6,
                "frame ${it.index} stands at ${it.state.position}, not on the slab top",
            )
        }
        assertEquals(64.5, path.plan.frames.last().state.position.y, 1e-6, "the tape must finish on a slab")
    }

    /**
     * A whole block's surface is its top, exactly.
     *
     * Worth asserting to the last bit rather than approximately. The surface is what the
     * arc probe launches the body's box from, and a surface a ten-millionth low starts the
     * sweep *inside* the take-off block -- which the probe answers by refusing the arc. The
     * symptom was that jumps stopped working between whole blocks while still working
     * between skulls, whose tops are nowhere near the value that was drifting.
     */
    @Test
    fun `a whole block stands the body at exactly its top`() {
        val cube = SnapshotBlockPhysics.of(VoxelShapes.fullCube()).coarseVoxel
        assertEquals(1.0, cube.standingSurface, "a full cube's surface is the block top")
        assertEquals(0.0, cube.surfaceOffset, "a full cube must carry no surface offset at all")
        assertEquals(
            SnapshotBlockPhysics.FULL_CUBE.coarseVoxel, cube,
            "a captured full cube must read identically to the declared one",
        )
    }

    /**
     * The comparison that found it: the same gap, cleared from two kinds of block.
     *
     * A jump between whole blocks and a jump between skulls differ only in what the body
     * stands on. If one is offered and the other is not, the difference is in how the
     * support's height was read, not in the arc.
     */
    @Test
    fun `a gap jump is offered from whole blocks and from partial ones alike`() {
        fun jumpsAcross(pad: SnapshotBlockPhysics): Boolean {
            val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
            for (z in -1..1) {
                blocks[BlockPos(0, 63, z)] = pad
                blocks[BlockPos(3, 63, z)] = pad
            }
            val environment = SnapshotSimulationEnvironment.synthetic(bounds(), blocks)
            return library().edgesFrom(environment, Stance(0, 64, 0))
                .any { it.to == Stance(3, 64, 0) && it.launch != null }
        }

        assertTrue(jumpsAcross(skull()), "a three-block gap between skulls must be jumpable")

        // Derived from the shape rather than [SnapshotBlockPhysics.FULL_CUBE], and that is
        // the whole point of the case. The declared constant carries an exact 1.0 surface,
        // so every fixture built from it is blind to a capture that derives a different
        // one -- which is exactly how a live-only regression got past a green corpus.
        assertTrue(
            jumpsAcross(SnapshotBlockPhysics.of(VoxelShapes.fullCube())),
            "the same gap between whole blocks must be jumpable too",
        )
    }

    /**
     * Landings that are not on one of the eight compass rays.
     *
     * The jump templates used to be a unit direction times a span, so the only reachable
     * landings were straight ahead or at exactly forty-five degrees. Three across and one to
     * the side -- an unremarkable gap in anything built by hand -- had no template at all,
     * and the search answered it by walking around or refusing.
     */
    @Test
    fun `an off-axis gap is jumpable`() {
        fun jumpsTo(dx: Int, dz: Int): Boolean {
            val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
            blocks[BlockPos(0, 63, 0)] = SnapshotBlockPhysics.of(VoxelShapes.fullCube())
            blocks[BlockPos(dx, 63, dz)] = SnapshotBlockPhysics.of(VoxelShapes.fullCube())
            val environment = SnapshotSimulationEnvironment.synthetic(bounds(), blocks)
            return library().edgesFrom(environment, Stance(0, 64, 0))
                .any { it.to == Stance(dx, 64, dz) && it.launch != null }
        }

        assertTrue(jumpsTo(3, 1), "three across and one to the side must be jumpable")
        assertTrue(jumpsTo(1, 3), "and the same offset on the other axis")
        assertTrue(jumpsTo(2, 1), "as must the shorter off-axis hop")
        assertTrue(jumpsTo(-3, 2), "and one that goes backwards and across")
    }

    /**
     * Reach is what the solver can do from a cruise approach, not a count of cells.
     *
     * A four-block jump lands from an ordinary approach; a five-block one needs the body to
     * arrive already carrying more speed than walking gives it, which it may not have had
     * room to build. Offering the latter unconditionally produced jumps that certified in
     * the planner and then did not happen in play.
     *
     * The bound is the solver itself rather than a distance, because a flight distance and a
     * centre-to-centre offset are different quantities -- the body leaves past the stance
     * centre and may land anywhere on the target -- and comparing them refused jumps that
     * are entirely ordinary.
     */
    @Test
    fun `a jump needing a run-up is not offered while an ordinary long one is`() {
        fun jumpsTo(dx: Int, dz: Int, rise: Int = 0): Boolean {
            val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
            blocks[BlockPos(0, 63, 0)] = SnapshotBlockPhysics.of(VoxelShapes.fullCube())
            blocks[BlockPos(dx, 63 + rise, dz)] = SnapshotBlockPhysics.of(VoxelShapes.fullCube())
            val environment = SnapshotSimulationEnvironment.synthetic(bounds(), blocks)
            return library().edgesFrom(environment, Stance(0, 64, 0))
                .any { it.to == Stance(dx, 64 + rise, dz) && it.launch != null }
        }

        // Three blocks of air, straight across and with steps to the side. All are
        // standing-start jumps (rollout-measured in OffAxisJumpProbeTest) and all must
        // be offered from flat ground -- including the two-block offset the old
        // centre-distance gate silently dropped.
        assertTrue(jumpsTo(4, 0), "a three-block gap must be jumpable from solid blocks")
        assertTrue(jumpsTo(4, 1), "and the same gap with a step to the side")
        assertTrue(jumpsTo(4, 2), "and with two steps to the side: the air gap is only hypot(3,1)")
        assertTrue(jumpsTo(3, 1), "as must the shorter off-axis hop")

        // Four blocks of air needs momentum a standing start cannot build, and the
        // coarse graph cannot promise a run-up exists at the launch. A route through a
        // jump the body may arrive unable to make is a route the walk cannot honour --
        // the graph stays a lower bound the search solves every time.
        assertTrue(!jumpsTo(5, 0), "a four-block gap is momentum-only and must not be promised")

        // Five blocks of air is beyond the body at any entry speed.
        assertTrue(!jumpsTo(6, 0), "a five-block gap is out of reach and must not be offered")

        // Rising jumps trade reach for the block of height: three air blocks straight
        // up-and-over certifies once in nine standing rollouts -- not a promise.
        assertTrue(!jumpsTo(4, 0, rise = 1), "a rising three-air gap is beyond a standing start")
        assertTrue(jumpsTo(3, 0, rise = 1), "a rising two-air gap is an everyday jump")
    }

    /**
     * A body on a carpet is standing in the cell above it.
     *
     * The planner used to name its start stance with [net.minecraft.entity.Entity.getBlockPos],
     * which floors the position -- right on a whole block, and one cell low on anything
     * partial. The route was then built from a stance the body was not in, and nothing
     * certified from it.
     */
    @Test
    fun `a body on a partial block is attributed to the stance above it`() {
        val carpet = 0.0625
        assertEquals(
            Stance(0, 65, 0), Stance.of(Vec3d(0.5, 64.0 + carpet, 0.5), onGround = true),
            "a body on a carpet at y=64 stands in the stance at y=65",
        )
        assertEquals(
            Stance(0, 65, 0), Stance.of(Vec3d(0.5, 65.0, 0.5), onGround = true),
            "and a body on a whole block reads exactly as it always did",
        )
        // Airborne is the other question entirely: the body is inside a cell, not on one.
        assertEquals(
            Stance(0, 64, 0), Stance.of(Vec3d(0.5, 64.0 + carpet, 0.5), onGround = false),
            "an airborne body is attributed to the cell containing it",
        )
    }

    /**
     * A fence holds the body up in the cell *above* it, not in its own.
     *
     * The only shape so far that is taller than the cell containing it. Everything else --
     * slab, stairs, carpet, snow -- provides a surface inside its own cell, so "which cell
     * holds the body up" and "which cell owns the shape" were the same question and one
     * lookup answered both. A fence post is 1.5 blocks: a body on top of it stands half a
     * block into the neighbouring cell, and asking that cell about its own contents finds
     * air. No fence line was walkable at all.
     */
    @Test
    fun `a body can stand on a fence, half a block into the cell above it`() {
        val environment = SnapshotSimulationEnvironment.synthetic(
            bounds(),
            buildMap {
                for (x in -4..4) for (z in -4..4) put(BlockPos(x, 63, z), SnapshotBlockPhysics.FULL_CUBE)
                for (x in 0..3) put(BlockPos(x, 64, 0), fence())
            },
        )
        val moves = library()

        // The fence's own cell holds nobody up: what it provides lands above it.
        val post = environment.voxel(0, 64, 0)
        assertEquals(null, post.standingSurface, "a fence is taller than its cell")
        assertEquals(0.5, post.intrusionHeight, 1e-9, "and reaches half a block past it")

        // The cell above is air, and is exactly where the body stands.
        assertEquals(
            0.5, assertNotNull(environment.standingSurface(0, 65, 0)), 1e-9,
            "the surface a fence provides is half a block up the cell above it",
        )
        assertTrue(moves.isStance(environment, Stance(0, 66, 0)), "a fence carries a stance")

        // Feet at y=65.5 put the body in cell 65, so its stance is 66 -- the planner and the
        // simulator have to agree on that or nothing certifies from it.
        assertEquals(
            Stance(0, 66, 0), Stance.of(Vec3d(0.5, 65.5, 0.5), onGround = true),
            "a body on a fence is attributed to the stance the graph built",
        )

        // And a fence line is walkable end to end.
        val planner = CoarsePlanner(environment, moves, Stance(0, 66, 0), Stance(3, 66, 0))
        assertTrue(planner.repair(Duration.INFINITE).converged, "a fence line must converge")
        val route = assertNotNull(planner.routePlan(0L), "a fence line must publish a route")
        assertEquals(Stance(3, 66, 0), route.nodes.last(), "the route must run along the fence")
    }

    private fun bounds() = SimulationSnapshotBounds(-16, 55, -16, 16, 80, 16)

    /** Vanilla's fence: a 1.5-tall post, narrow enough to leave the cell mostly open. */
    private fun fence() = SnapshotBlockPhysics.of(
        VoxelShapes.cuboid(0.375, 0.0, 0.375, 0.625, 1.5, 0.625),
        fenceLike = true,
    )

    private fun library() = moveLibrary(SimpleMoveOptions())

    private fun slab() = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0))

    /** A player head: half high and inset, so its top is nowhere near the block top. */
    private fun skull() = SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.25, 0.0, 0.25, 0.75, 0.5, 0.75))

    private fun snowLayer(layers: Int) =
        SnapshotBlockPhysics.of(VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, layers * 0.125, 1.0))

    /** A bottom-half slab plus a raised step over the far half, as vanilla stairs are. */
    private fun stairs() = SnapshotBlockPhysics.of(
        VoxelShapes.union(
            VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.5, 1.0),
            VoxelShapes.cuboid(0.5, 0.5, 0.0, 1.0, 1.0, 1.0),
        )
    )
}
