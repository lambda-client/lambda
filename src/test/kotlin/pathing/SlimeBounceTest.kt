/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.Stance
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.launch.BounceSolver
import com.lambda.pathing.launch.LaunchSolver
import com.lambda.pathing.launch.LaunchMode
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pathing.ProbeScenarios.PROFILE
import pathing.ProbeScenarios.moveLibrary

/**
 * Landing on slime, which vanilla treats as one block behaviour among several.
 *
 * "A landing stops you" is the *default* of `Block.onEntityLand`, not a rule of the engine.
 * The simulator applied it unconditionally, so slime was marked as physics it could not
 * model and the planner refused to touch the block at all.
 */
class SlimeBounceTest {
    /**
     * The reflection is total: the body comes back up to about where it fell from.
     *
     * Asserted as a height rather than as a velocity ratio because a tick does more than the
     * landing -- gravity and drag are applied in the same tick -- so comparing the velocity
     * either side of one measures those too. The height is the property that matters and the
     * one a route would be planned around.
     */
    @Test
    fun `landing on slime reflects the fall instead of stopping it`() {
        val dropFrom = 68.0
        val onSlime = apex(simulator(slimeAt(63), from = Vec3d(0.5, dropFrom, 0.5)))
        val onStone = apex(simulator(stoneAt(63), from = Vec3d(0.5, dropFrom, 0.5)))

        assertTrue(
            onStone < 64.5,
            "the control must simply land and stay down, reached $onStone",
        )
        // Not all the way back: the reflection is exact but drag acts on the rise as well
        // as the fall, so a four-block drop returns about two and a half. What matters is
        // that the body goes back *up* at all, which nothing else on the ground does.
        assertTrue(
            onSlime > 65.5,
            "a bounce must carry the body well back up, reached $onSlime",
        )
    }

    /**
     * The bounce is a vertical reflection, so horizontal momentum passes straight through.
     *
     * This is what makes the move worth having: the body keeps its speed *and* gets the air
     * time back, so it crosses ground it could not have jumped.
     *
     * Checked against an identical drop onto stone rather than against a constant. Both
     * landings leave horizontal velocity alone -- that is the claim -- so at the tick of
     * impact the two must agree exactly, whatever gravity and drag did to them on the way
     * down. What differs is everything after: one body is still flying, the other is being
     * scrubbed off by ground friction.
     */
    @Test
    fun `a bounce carries horizontal momentum straight through`() {
        val bouncing = simulator(slimeAt(63), from = Vec3d(0.5, 68.0, 0.5))
        val landing = simulator(stoneAt(63), from = Vec3d(0.5, 68.0, 0.5))
        bouncing.reset(bouncing.state.copy(velocity = Vec3d(0.25, 0.0, 0.0)))
        landing.reset(landing.state.copy(velocity = Vec3d(0.25, 0.0, 0.0)))

        var compared = false
        repeat(30) {
            val falling = bouncing.state.velocity.y < 0.0
            bouncing.tickMovement(MovementSimulationInput())
            landing.tickMovement(MovementSimulationInput())
            if (!compared && falling && bouncing.state.velocity.y > 0.0) {
                compared = true
                assertEquals(
                    landing.state.velocity.x, bouncing.state.velocity.x, 1e-12,
                    "the landing itself must not change horizontal speed, on slime or stone",
                )
                // Both are grounded on the tick of impact -- vanilla decides that from the
                // collision, before any block gets a say in the velocity. The bouncing body
                // leaves on the tick after, carrying the speed asserted above.
                assertTrue(landing.state.onGround && bouncing.state.onGround, "both touched down")
            }
        }
        assertTrue(compared, "the body must have bounced")
    }

    /** The highest point the body reaches after it first touches down. */
    private fun apex(sim: MovementSimulator): Double {
        var landed = false
        var highest = Double.NEGATIVE_INFINITY
        repeat(60) {
            sim.tickMovement(MovementSimulationInput())
            if (sim.state.onGround) landed = true
            if (landed) highest = maxOf(highest, sim.state.position.y)
        }
        return highest
    }

    /**
     * A carpet over slime still bounces, and the reason is the probe's reach.
     *
     * The landing block is the one 0.2 below the feet, not the one being stood on. A carpet
     * is 0.0625 thick, so the probe passes through it and finds the slime underneath.
     * Anything reading the supporting block instead would get exactly this case wrong.
     */
    @Test
    fun `a carpet laid over slime still bounces`() {
        val blocks = slimeAt(63).toMutableMap()
        blocks[BlockPos(0, 64, 0)] = SnapshotBlockPhysics.of(
            VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0625, 1.0)
        )
        val sim = simulator(blocks, from = Vec3d(0.5, 69.0, 0.5))

        var rebound = 0.0
        repeat(24) {
            val was = sim.state.velocity.y
            sim.tickMovement(MovementSimulationInput())
            if (rebound == 0.0 && was < 0.0 && sim.state.velocity.y > 0.0) rebound = sim.state.velocity.y
        }
        assertTrue(rebound > 0.0, "carpet over slime must still bounce the body")
    }

    /**
     * Sneaking cancels the bounce -- and that matters beyond slime.
     *
     * `bypassesLandingEffects()` is `isSneaking()`, and the drop control presses sneak to pin
     * itself at a ledge. A descent that sneaks onto slime lands dead instead of rebounding.
     */
    @Test
    fun `a sneaking body lands on slime without bouncing`() {
        val sim = simulator(slimeAt(63), from = Vec3d(0.5, 68.0, 0.5))

        repeat(20) { sim.tickMovement(MovementSimulationInput(sneak = true)) }
        assertTrue(
            sim.state.onGround && abs(sim.state.velocity.y) < 0.1,
            "a sneaking body must settle on the slime, not rebound: ${sim.state.velocity}",
        )
    }

    /** Walking across slime is roughly half speed, which is a separate behaviour again. */
    @Test
    fun `walking on slime is dragged to a crawl`() {
        fun cruise(blocks: Map<BlockPos, SnapshotBlockPhysics>): Double {
            val sim = simulator(blocks, from = Vec3d(0.5, 64.0, 0.5))
            repeat(24) {
                sim.tickMovement(MovementSimulationInput(forward = 1.0, rotation = Rotation(-90.0, 0.0)))
            }
            return sim.state.velocity.horizontalLength()
        }

        val onSlime = cruise(slimeAt(63))
        val onStone = cruise(stoneAt(63))
        assertTrue(
            onSlime < onStone * 0.75,
            "slime must drag a walking body well below stone cruise ($onSlime vs $onStone)",
        )
    }

    /**
     * The solved bounce arc, checked tick by tick against the body it models.
     *
     * [BallisticProfile] exists so a launch can be *solved* rather than searched, and a
     * solved arc is only worth having if it agrees with the simulator. So the arc is walked
     * alongside a real body over real slime and both must report the same height and the same
     * distance travelled at every tick of the flight -- the fall, the contact, and the way
     * back out.
     *
     * Three details of the contact tick came out of exactly this comparison, and each was
     * worth a visible error before it was found: the reflection takes the velocity from
     * before that tick's gravity, the contact tick still drags as an air tick, and the tick
     * after it is a ground tick in full -- surface acceleration as well as surface friction.
     */
    @Test
    fun `the solved bounce arc matches the body it models, tick for tick`() {
        val drop = 4
        val rise = -3
        val entrySpeed = 0.25
        val arc = assertNotNull(
            BallisticProfile.VANILLA.bounce(
                entrySpeed = entrySpeed, drop = drop.toDouble(), rise = rise.toDouble(),
                holdForward = true, sprint = true,
            ),
            "a four-block fall onto slime must rebound past three below the lip",
        )

        // Open air over slime, so the arc's model of the take-off tick is what is being
        // tested rather than the body scrubbing along a ledge on its way off one.
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..4) for (z in -4..24) blocks[BlockPos(x, 63, z)] = slime()
        val sim = MovementSimulator(
            profile = PROFILE,
            environment = SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(-16, 55, -16, 16, 90, 32), blocks,
            ),
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 68.0, 0.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, 0.0, entrySpeed),
                onGround = true,
            ),
        )

        // The last sample is the landing, which the arc reports clamped to the target rise
        // while the body carries on falling past it -- so it is checked separately below.
        for (tick in 0 until arc.airTicks - 1) {
            sim.tickMovement(
                MovementSimulationInput(forward = 1.0, sprint = true, rotation = Rotation(0.0, 0.0))
            )
            // A hundred-thousandth of a block. The arc is a scalar recurrence and the
            // simulator moves a box through a world, so twenty ticks of the same physics in a
            // different association order drift by about a millionth -- far below anything
            // that could change a landing, and far above nothing, which is what a mismatched
            // constant would produce.
            assertEquals(
                arc.heights[tick], sim.state.position.y - 68.0, 1e-5,
                "height diverged at tick ${tick + 1}",
            )
            assertEquals(
                arc.distances[tick], sim.state.position.z - 0.5, 1e-5,
                "distance diverged at tick ${tick + 1}",
            )
        }

        assertEquals(-drop.toDouble(), arc.heights.min(), 1e-9, "the trough is the slime top")
        assertTrue(arc.distance > 6.0, "the bounce must carry a long way, went ${arc.distance}")
    }

    /**
     * The jump-launch arc against the body it models, tick for tick. The launch tick
     * carries the jump velocity, the sprint-jump boost, and the last ground
     * acceleration all at once -- exactly like [BallisticProfile.fly]'s jump tick --
     * and the deeper impact is what the taller rebound is bought with.
     */
    @Test
    fun `the solved jump-launch bounce arc matches the body it models, tick for tick`() {
        val drop = 4
        val entrySpeed = 0.2
        val arc = assertNotNull(
            BallisticProfile.VANILLA.bounce(
                entrySpeed = entrySpeed, drop = drop.toDouble(), rise = -1.0,
                holdForward = true, sprint = true, jump = true,
            ),
            "a jump launch onto slime four down must rebound past one below the lip",
        )

        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..4) for (z in -4..24) blocks[BlockPos(x, 63, z)] = slime()
        val sim = MovementSimulator(
            profile = PROFILE,
            environment = SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(-16, 55, -16, 16, 90, 32), blocks,
            ),
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                position = Vec3d(0.5, 68.0, 0.5),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, 0.0, entrySpeed),
                onGround = true,
            ),
        )

        for (tick in 0 until arc.airTicks - 1) {
            sim.tickMovement(
                MovementSimulationInput(
                    forward = 1.0, sprint = true, jump = tick == 0,
                    rotation = Rotation(0.0, 0.0),
                )
            )
            assertEquals(
                arc.heights[tick], sim.state.position.y - 68.0, 1e-5,
                "height diverged at tick ${tick + 1}",
            )
            assertEquals(
                arc.distances[tick], sim.state.position.z - 0.5, 1e-5,
                "distance diverged at tick ${tick + 1}",
            )
        }

        assertEquals(-drop.toDouble(), arc.heights.min(), 1e-9, "the trough is the slime top")
        // The point of jumping: a walk-off from four up cannot land one below the lip.
        assertNull(
            BallisticProfile.VANILLA.bounce(0.0, drop.toDouble(), -1.0, holdForward = true, sprint = true),
            "the walk-off rebound must NOT reach one below the lip -- only the jump launch does",
        )
    }

    /**
     * The thing a bounce does that no fall can: finish above where it landed.
     *
     * A drop is monotone -- height given up is gone. A bounce hands most of it back, which
     * is the whole reason to route through one.
     */
    @Test
    fun `a bounce returns most of the height the fall gave up`() {
        val profile = BallisticProfile.VANILLA
        val arc = assertNotNull(profile.bounce(entrySpeed = 0.25, drop = 6.0, rise = -3.0))

        assertEquals(-6.0, arc.heights.min(), 1e-9, "the body must reach the slime and no lower")
        assertTrue(
            arc.heights.last() > arc.heights.min() + 2.5,
            "the rebound must give back most of a six-block fall, ended at ${arc.heights.last()}",
        )

        // The contrast, stated as the property rather than as a refusal: a fall ends at
        // its lowest point by definition, so its last height *is* its trough. A bounce ends
        // three blocks above its own.
        val fell = assertNotNull(
            profile.fly(LaunchMode.WALK_DROP, 0.25, rise = -6.0, holdForward = false),
            "the same six-block fall without slime must be flyable",
        )
        assertEquals(
            fell.heights.min(), fell.heights.last(), 1e-9,
            "a fall finishes at the bottom of itself",
        )
        assertTrue(
            arc.heights.last() > fell.heights.last() + 2.5,
            "the bounce must finish well above where the same fall would have",
        )
    }

    /**
     * The solver's claim, put to the body it is a claim about.
     *
     * [BounceSolver] says "leave the ledge at this speed and you will land there". That is
     * falsifiable, so it is falsified: the solution is flown through the simulator over real
     * slime, and the body has to arrive. This is the same contract [LaunchSolverTest] holds
     * the jump solver to, and it is the only kind of test that catches a solver whose
     * arithmetic is self-consistent and wrong.
     */
    @Test
    fun `a solved bounce lands where it says it will`() {
        val drop = 5
        val rise = -2
        val aim = 7.0

        val solution = assertNotNull(
            BounceSolver.solve(horizontalDistance = aim, drop = drop, rise = rise),
            "a seven-block bounce out of a five-deep pit must be solvable",
        )
        assertTrue(solution.speed > 0.0, "the bounce must need real speed, got ${solution.speed}")
        assertTrue(
            solution.contactDistance in 1.0..aim,
            "the slime must be met somewhere along the way, at ${solution.contactDistance}",
        )

        // A slime floor five below the lip, and a landing pad two below it at the aim point.
        val takeOff = 68.0
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -4..4) for (z in -4..24) blocks[BlockPos(x, (takeOff - drop - 1).toInt(), z)] = slime()
        val sim = MovementSimulator(
            profile = PROFILE,
            environment = SnapshotSimulationEnvironment.synthetic(
                SimulationSnapshotBounds(-16, 40, -16, 16, 90, 32), blocks,
            ),
            initialState = MovementSimulationState.synthetic(
                profile = PROFILE,
                // At the take-off point, not the stance centre. The solved speed is the speed
                // the body has *when it leaves the ground*, and the eight tenths of a block
                // before that are walked rather than flown.
                position = Vec3d(0.5, takeOff, 0.5 + solution.launchOffset),
                rotation = Rotation(0.0, 0.0),
                velocity = Vec3d(0.0, 0.0, solution.speed),
                onGround = true,
            ),
        )

        var touchedSlime = false
        // One more than [ArcSample.airTicks]: the arc's take-off sample is its first entry, so
        // its landing entry -- the one [ArcSample.distance] reports -- is the tick after the
        // count of air ticks. Off by one here reads as a quarter-block solver error.
        repeat(solution.airTicks + 1) {
            sim.tickMovement(
                MovementSimulationInput(
                    forward = if (solution.holdForward) 1.0 else 0.0,
                    sprint = solution.sprint,
                    rotation = Rotation(0.0, 0.0),
                )
            )
            if (sim.state.position.y <= takeOff - drop + 1e-6) touchedSlime = true
        }

        assertTrue(touchedSlime, "the body must have reached the slime")
        assertEquals(
            aim, sim.state.position.z - 0.5, 0.05,
            "the body must arrive where the solver aimed it, measured from the stance centre",
        )
        assertEquals(
            takeOff + rise, sim.state.position.y, 0.1,
            "and at the height it was solved for, at ${sim.state.position}",
        )
    }

    /**
     * A bounce reaches ground a jump cannot, which is the reason to plan one.
     *
     * Same start, same landing height: the jump solver refuses the distance outright while
     * the bounce covers it, because the fall supplies an impulse no jump key can.
     */
    @Test
    fun `a bounce reaches distances no jump can`() {
        // Nine blocks out and six down: past every jump and every drop, because a drop
        // gives up all its height and a jump has only its own impulse.
        assertNull(
            LaunchSolver.best(Stance(0, 68, 0), Stance(0, 62, 10)),
            "ten blocks out and six down is beyond every jump and drop",
        )
        assertNotNull(
            BounceSolver.solve(horizontalDistance = 10.0, drop = 8, rise = -6),
            "but a bounce off slime eight below covers it",
        )

        // The fall is what supplies the impulse, so a deeper pit reaches further. That is the
        // property that makes a bounce a distinct primitive rather than a long jump.
        // Six blocks is inside the reach of both, which is what makes the two comparable at
        // all -- a distance only the deep one covers proves nothing about speed.
        val shallow = assertNotNull(BounceSolver.solve(6.0, drop = 3, rise = -2))
        val deep = assertNotNull(BounceSolver.solve(6.0, drop = 6, rise = -2))
        assertTrue(
            deep.speed < shallow.speed,
            "a deeper fall must need less run-up for the same reach " +
                "(${deep.speed} vs ${shallow.speed})",
        )
    }

    /**
     * The whole planner over a slime pit, end to end.
     *
     * This is the case the primitive exists for: two ledges with a gap between them that no
     * jump crosses, and slime at the bottom. Walking off means falling in; jumping means
     * falling in short. The only way over is to fall onto the slime deliberately and ride the
     * rebound, and either the graph offers that or the goal is unreachable.
     */
    @Test
    fun `a slime pit is crossed by a planned bounce`() {
        val lip = 71
        val floor = lip - 6
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        // Take-off deck, then a six-deep pit floored with slime, then a landing deck part way
        // up the far side -- reachable by a rebound and by nothing else.
        for (x in -6..0) for (z in -2..2) blocks[BlockPos(x, lip - 1, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in 1..7) for (z in -3..3) blocks[BlockPos(x, floor - 1, z)] = slime()
        // The landing deck starts where the arc arrives and not a block sooner. Put it closer
        // and the rebound flies into its *side* on the way up, which the obstacle sweep
        // refuses -- correctly, and invisibly unless the geometry is drawn out.
        for (x in 8..11) for (z in -2..2) blocks[BlockPos(x, lip - 4, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, 55, -12, 20, 90, 12), blocks,
        )
        val moves = moveLibrary(SimpleMoveOptions(allowSlimeBounces = true, maxWalkOffDepth = 3))

        val edges = moves.edgesFrom(environment, Stance(0, lip, 0))
        val bounce = assertNotNull(
            edges.firstOrNull { it.movement == MovementId.BOUNCE && it.to == Stance(8, lip - 3, 0) },
            "the pit must be offered as a bounce onto the far deck: " +
                edges.filter { it.movement == MovementId.BOUNCE }.map { "${it.to}" },
        )
        val solution = assertNotNull(bounce.bounce, "a bounce edge must carry its solved fall")
        assertEquals(6, solution.drop, "the fall is to the slime floor")
        assertTrue(
            solution.rebound > 2.0,
            "the rebound must lift the body clear of the pit, got ${solution.rebound}",
        )
        assertTrue(
            bounce.to.y > floor,
            "the landing must be above the pit floor, at ${bounce.to}",
        )
    }

    /** Slime in the wrong place is no bounce at all: the probe has to check where it lands. */
    @Test
    fun `a bounce is not offered where the slime is not under the arc`() {
        val lip = 71
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
        for (x in -6..0) for (z in -2..2) blocks[BlockPos(x, lip - 1, z)] = SnapshotBlockPhysics.FULL_CUBE
        // A plain stone pit floor. Everything else about the geometry is unchanged.
        for (x in 1..7) for (z in -3..3) blocks[BlockPos(x, lip - 7, z)] = SnapshotBlockPhysics.FULL_CUBE
        for (x in 8..11) for (z in -2..2) blocks[BlockPos(x, lip - 4, z)] = SnapshotBlockPhysics.FULL_CUBE

        val environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-12, 55, -12, 20, 90, 12), blocks,
        )
        val moves = moveLibrary(SimpleMoveOptions(allowSlimeBounces = true, maxWalkOffDepth = 3))
        assertTrue(
            moves.edgesFrom(environment, Stance(0, lip, 0)).none { it.movement == MovementId.BOUNCE },
            "a stone pit must not be offered as a bounce",
        )
    }

    /** Off by default: the arcs are long, and the terrain that rewards them is rare. */
    @Test
    fun `bounces are not offered unless asked for`() {
        val plain = moveLibrary()
        assertTrue(
            plain.templates.none { it.movement == MovementId.BOUNCE },
            "no bounce templates may exist by default",
        )
    }

    private fun stoneAt(y: Int): Map<BlockPos, SnapshotBlockPhysics> = buildMap {
        for (x in -8..8) for (z in -8..8) put(BlockPos(x, y, z), SnapshotBlockPhysics.FULL_CUBE)
    }

    private fun slimeAt(y: Int): Map<BlockPos, SnapshotBlockPhysics> = buildMap {
        for (x in -8..8) for (z in -8..8) put(BlockPos(x, y, z), slime())
    }

    /** A full cube that reflects a landing, which is what makes it slime. */
    private fun slime() = SnapshotBlockPhysics.of(
        VoxelShapes.fullCube(),
        bounceFactor = 1.0,
        dampensSteppingSpeed = true,
    )

    private fun simulator(blocks: Map<BlockPos, SnapshotBlockPhysics>, from: Vec3d) = MovementSimulator(
        profile = PROFILE,
        environment = SnapshotSimulationEnvironment.synthetic(
            SimulationSnapshotBounds(-16, 55, -16, 16, 90, 16), blocks,
        ),
        initialState = MovementSimulationState.synthetic(
            profile = PROFILE,
            position = from,
            rotation = Rotation(-90.0, 0.0),
            velocity = Vec3d(0.0, 0.0, 0.0),
            onGround = false,
        ),
    )

}
