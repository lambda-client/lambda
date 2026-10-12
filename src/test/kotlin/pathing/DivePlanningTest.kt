package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.BubbleColumn
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.moveLibrary
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Diving: routes through flooded tunnels and up water shafts, goals under water, the air
 * budget that bounds a dive, and bubble columns as vanilla moves the body through them.
 */
class DivePlanningTest {
	init {
		MinecraftBootstrap.ensure()
	}

	@Test
	fun `a flooded tunnel under a wall is dived through`() {
		val environment = channel(length = 30) { x, y -> x in 12..15 && y >= 62 }
		val path = certified(environment, Stance(-2, 64, 0), Stance(32, 64, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		val frames = path.plan.frames
		assertTrue(frames.any { it.state.eyeInWater }, "the tunnel must be dived")
		assertTrue(frames.all { it.state.air >= Vanilla.SAFE_AIR }, "the dive must keep a safe air margin")
		assertEquals(Stance(32, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a tunnel longer than a breath is refused`() {
		val environment = channel(length = 70) { x, y -> x in 5..64 && y >= 62 }
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-2, 64, 0), Stance(72, 64, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "no route may dive past the air budget")
	}

	@Test
	fun `a goal on the floor of a pool is reached under water`() {
		val environment = channel(length = 12) { _, _ -> false }
		val path = certified(environment, Stance(-2, 64, 0), Stance(6, 61, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		val last = path.plan.frames.last().state
		assertEquals(Stance(6, 61, 0), Stance.of(last.position, onGround = last.onGround), "the dive must settle on the goal: ${last.position}")
	}

	@Test
	fun `a body short of air on a pool floor still plans its way out`() {
		val environment = channel(length = 12) { _, _ -> false }
		val start = MovementSimulationState.synthetic(ProbeScenarios.PROFILE, Vec3d(6.5, 61.0, 0.5), Rotation(-90.0, 0.0), onGround = true).copy(
			touchingWater = true, eyeInWater = true, submergedInWater = true, air = LOW_AIR,
		)
		val path = certified(environment, Stance(6, 61, 0), Stance(14, 64, 0), start, dives = false)
		val frames = path.plan.frames
		assertTrue(frames.all { it.state.air >= 0 }, "the escape must not run out of air")
		assertEquals(Stance(14, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a water hole is dropped into and dived along a flooded passage to another hole`() {
		val path = certified(sunkenPassage(tunnel = 12), Stance(-2, 64, 0), Stance(19, 64, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		val frames = path.plan.frames
		println("[dive] sunken passage frames=${frames.size} min air=${frames.minOf { it.state.air }} deepest=${frames.minOf { it.state.position.y }}")
		assertTrue(frames.any { it.state.position.y < 56.0 }, "the passage at y 55 must be dived")
		assertTrue(frames.all { it.state.air >= Vanilla.SAFE_AIR }, "the dive must keep a safe air margin")
		assertEquals(Stance(19, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a flooded passage longer than a breath is dived by refilling in an air pocket halfway`() {
		val length = 2 * POCKET_HALF + 5
		val pocket = length / 2
		val special = buildMap {
			for (z in -1..1) {
				put(BlockPos(pocket, 63, z), SnapshotBlockPhysics.AIR)
				put(BlockPos(pocket, 62, z), SnapshotBlockPhysics.water())
			}
		}
		val environment = channel(length, special) { x, y -> x in 2 until length - 2 && y >= 63 && !(x == pocket && y == 63) }
		val path = certified(environment, Stance(-2, 64, 0), Stance(length + 2, 64, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		val frames = path.plan.frames
		println("[dive] pocket tunnel frames=${frames.size} min air=${frames.minOf { it.state.air }}")
		assertTrue(frames.all { it.state.air >= Vanilla.SAFE_AIR }, "the dive must keep a safe air margin")
		assertEquals(Stance(length + 2, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a one-deep air gap over a one-high passage is no air source`() {
		val length = 2 * POCKET_HALF + 5
		val pocket = length / 2
		val special = buildMap {
			for (z in -1..1) {
				put(BlockPos(pocket, 62, z), SnapshotBlockPhysics.AIR)
				put(BlockPos(pocket, 61, z), SnapshotBlockPhysics.water())
			}
		}
		val environment = channel(length, special) { x, y -> x in 2 until length - 2 && y >= 62 && !(x == pocket && y == 62) }
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-2, 64, 0), Stance(length + 2, 64, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "a body that stands up to breathe there cannot swim back into the passage")
	}

	@Test
	fun `a passage longer than a breath is dived by detouring into an air alcove beside it`() {
		val length = 2 * POCKET_HALF + 5
		val path = certified(alcovePassage(length), Stance(-2, 64, 0), Stance(length + 2, 64, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		val frames = path.plan.frames
		println("[dive] alcove passage frames=${frames.size} min air=${frames.minOf { it.state.air }}")
		assertTrue(frames.any { it.state.position.z > 1.0 && it.state.position.x in (length / 2).toDouble()..(length / 2 + 1).toDouble() }, "the dive must turn into the alcove")
		assertTrue(frames.all { it.state.air >= Vanilla.SAFE_AIR }, "the dive must keep a safe air margin")
		assertEquals(Stance(length + 2, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a goal in open water above the floor is reached`() {
		val environment = channel(length = 12) { _, _ -> false }
		val path = certified(environment, Stance(-2, 64, 0), Stance(6, 62, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		val last = path.plan.frames.last().state
		println("[dive] mid-water goal end=${last.position}")
		assertTrue(abs(last.position.x - 6.5) < 1.0 && abs(last.position.z - 0.5) < 1.0 && last.position.y in 61.5..63.0, "the dive must end in the goal cell: ${last.position}")
	}

	@Test
	fun `a water shaft is swum up to a high exit`() {
		val path = certified(shaft(), Stance(-2, 64, 0), Stance(4, 72, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0))
		assertEquals(Stance(4, 72, 0), Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	@Test
	fun `a lifting bubble column climbs the shaft faster than swimming`() {
		val plain = certified(shaft(), Stance(-2, 64, 0), Stance(4, 72, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0)).plan.frames.size
		val lifted = certified(shaft(BubbleColumn.LIFT), Stance(-2, 64, 0), Stance(4, 72, 0), resting(Vec3d(-1.5, 64.0, 0.5), -90.0)).plan.frames.size
		println("[dive] shaft plain=$plain lifted=$lifted frames")
		assertTrue(lifted < plain, "the lift must shorten the climb: $lifted vs $plain")
	}

	@Test
	fun `a lifting column throws the body up and refills its air`() {
		val environment = column(BubbleColumn.LIFT)
		val simulator = MovementSimulator(ProbeScenarios.PROFILE, environment, afloat(Vec3d(0.5, 62.0, 0.5), air = 100))
		var peak = 0.0
		repeat(40) { peak = maxOf(peak, simulator.tickMovement(MovementSimulationInput(rotation = Rotation(0.0, 0.0))).velocity.y) }
		assertTrue(peak > 0.5, "the column must lift the body hard: peak ${peak}")
		assertTrue(simulator.state.air > 100, "bubbles at the eyes refill the air: ${simulator.state.air}")
	}

	@Test
	fun `a dragging column pulls the body to the floor`() {
		val environment = column(BubbleColumn.DRAG)
		val simulator = MovementSimulator(ProbeScenarios.PROFILE, environment, afloat(Vec3d(0.5, 69.0, 0.5), air = Vanilla.MAX_AIR))
		repeat(80) { simulator.tickMovement(MovementSimulationInput(jump = true, rotation = Rotation(0.0, 0.0))) }
		assertTrue(simulator.state.position.y < 62.0, "swimming up must lose against the drag: ${simulator.state.position}")
	}

	@Test
	fun `a dragging column is no coarse passage`() {
		val drag = bubble(BubbleColumn.DRAG)
		val dragged = channel(length = 12, special = (-1..1).associate { BlockPos(5, 61, it) to drag }) { x, y -> x == 5 && y >= 62 }
		val planner = CoarsePlanner(dragged, moveLibrary(SimpleMoveOptions()), Stance(-2, 64, 0), Stance(14, 64, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "a tunnel through a down column must not be routed")
	}

	private fun certified(environment: SnapshotSimulationEnvironment, from: Stance, to: Stance, start: MovementSimulationState, dives: Boolean = true) = run {
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), from, to)
		assertTrue(planner.repair().converged, "the route must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the terrain must carry a route")
		println("[dive] route ${route.edges.map { it.movement }}")
		if (dives) assertTrue(route.edges.any { it.movement == MovementId.DIVE }, "the route must dive: ${route.edges.map { it.movement }}")
		val outcome = walkFrom(route, planner, start, environment)
		assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the dive must be certified, got $outcome")
	}

	private fun afloat(position: Vec3d, air: Int) =
		MovementSimulationState.synthetic(ProbeScenarios.PROFILE, position, Rotation(0.0, 0.0), onGround = false).copy(air = air)

	private fun bubble(kind: BubbleColumn) = SnapshotBlockPhysics(
		VoxelShapes.empty(),
		coarseVoxel = if (kind == BubbleColumn.DRAG) CoarseVoxel.HAZARD else CoarseVoxel.WATER,
		water = WaterPhysics(1.0f, Vec3d.ZERO, kind),
	)

	/** A channel three blocks wide along x: banks at y 64, water y 61..63 over [0, length), solid where [solid] says so. */
	private fun channel(length: Int, special: Map<BlockPos, SnapshotBlockPhysics> = emptyMap(), solid: (Int, Int) -> Boolean): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..length + 6) for (z in -2..2) {
			blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
			for (y in 61..72) {
				val wall = z == -2 || z == 2
				val water = x in 0 until length && y <= 63
				blocks[BlockPos(x, y, z)] = when {
					wall || solid(x, y) -> SnapshotBlockPhysics.FULL_CUBE
					water && y == 63 && !solid(x, 64) -> SnapshotBlockPhysics.water()
					water -> SnapshotBlockPhysics.water(1.0f)
					y <= 63 -> SnapshotBlockPhysics.FULL_CUBE
					else -> continue
				}
			}
		}
		blocks.putAll(special)
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 50, -3, length + 10, 80, 3), blocks)
	}

	/** Pools at both ends joined by a one-wide, two-high flooded passage whose only air midway is an alcove beside it. */
	private fun alcovePassage(length: Int): SnapshotSimulationEnvironment {
		val alcove = length / 2
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..length + 6) for (z in -2..2) {
			blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
			for (y in 61..72) {
				val pool = (x in 0 until 2 || x in length - 2 until length) && z in -1..1 && y <= 63
				val passage = x in 2 until length - 2 && z == 0 && y <= 62
				val side = x == alcove && z == 1 && y <= 62
				blocks[BlockPos(x, y, z)] = when {
					pool && y == 63 -> SnapshotBlockPhysics.water()
					pool || passage -> SnapshotBlockPhysics.water(1.0f)
					side && y == 62 -> SnapshotBlockPhysics.water()
					side -> SnapshotBlockPhysics.water(1.0f)
					x == alcove && z == 1 && y == 63 -> SnapshotBlockPhysics.AIR
					y <= 63 || x in 2 until length - 2 -> SnapshotBlockPhysics.FULL_CUBE
					else -> continue
				}
			}
		}
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 50, -3, length + 10, 80, 3), blocks)
	}

	/** Ground at y 64 split by a high wall; a one-block water hole on each side drops to a flooded passage at y 55 joining them. */
	private fun sunkenPassage(tunnel: Int): SnapshotSimulationEnvironment {
		val entry = 2
		val exit = entry + tunnel
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..exit + 8) for (z in -2..2) {
			for (y in 54..72) {
				val hole = z == 0 && (x == entry || x == exit) && y in 55..63
				val passage = z == 0 && y == 55 && x in entry..exit
				val wall = y >= 64 && x in entry + 3..exit - 3
				blocks[BlockPos(x, y, z)] = when {
					hole && y == 63 -> SnapshotBlockPhysics.water()
					hole || passage -> SnapshotBlockPhysics.water(1.0f)
					y <= 63 || wall -> SnapshotBlockPhysics.FULL_CUBE
					else -> continue
				}
			}
		}
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 50, -3, exit + 12, 80, 3), blocks)
	}

	/** A pool at y 61..63 open above x 0..1 whose far end is a walled one-block shaft of water rising to y 71 beside a ledge at 72. */
	private fun shaft(bubbles: BubbleColumn? = null): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..10) for (z in -2..2) {
			blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
			for (y in 61..78) {
				val pool = x in 0..3 && z in -1..1 && y <= 63
				val column = x == 3 && z == 0 && y in 61..71
				blocks[BlockPos(x, y, z)] = when {
					column && bubbles != null -> bubble(bubbles).let { if (y == 71) it.copy(water = it.water!!.copy(height = SURFACE_WATER_HEIGHT)) else it }
					column -> if (y == 71) SnapshotBlockPhysics.water() else SnapshotBlockPhysics.water(1.0f)
					pool && y == 63 -> SnapshotBlockPhysics.water()
					pool -> SnapshotBlockPhysics.water(1.0f)
					x < 0 && y <= 63 -> SnapshotBlockPhysics.FULL_CUBE
					x >= 4 && y <= 71 -> SnapshotBlockPhysics.FULL_CUBE
					y in 64..71 && (x == 2 || x == 3) -> SnapshotBlockPhysics.FULL_CUBE
					else -> continue
				}
			}
		}
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 50, -3, 14, 80, 3), blocks)
	}

	/** A sealed one-block column of bubbles from y 61 to 70 under open air. */
	private fun column(kind: BubbleColumn): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -1..1) for (z in -1..1) {
			blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
			for (y in 61..70) blocks[BlockPos(x, y, z)] = if (x == 0 && z == 0) bubble(kind) else SnapshotBlockPhysics.FULL_CUBE
		}
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-4, 50, -4, 4, 80, 4), blocks)
	}

	private companion object {
		const val LOW_AIR = 40

		const val POCKET_HALF = 30
	}
}
