package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.actions.CoarseMoveRates
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.search.TrajectoryPlan
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.search.WorldLink
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.moveLibrary
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Surface swimming: a pool between two banks is crossed by walking off the near bank into the
 * water, swimming at the surface and climbing out over the far bank, and the coarse swim rate
 * is what the simulated stroke actually achieves.
 */
class SwimPlanningTest {
	init {
		MinecraftBootstrap.ensure()
	}

	@Test
	fun `a pool between one block banks is swum across`() {
		val environment = pool(width = 8)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-2, 64, 0), Stance(10, 64, 0))
		assertTrue(planner.repair().converged, "the pool crossing must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the pool must carry a route")
		val movements = route.edges.map { it.movement }
		assertTrue(MovementId.ENTER_WATER in movements && MovementId.SWIM in movements && MovementId.EXIT_WATER in movements, "the route must swim: $movements")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 64.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the crossing must be certified, got $outcome")
		val frames = path.plan.frames
		assertTrue(frames.count { it.state.touchingWater } > 40, "the tape must swim the pool")
		assertTrue(frames.none { it.state.eyeInWater }, "a surface crossing never puts the eyes under water")
		assertEquals(Stance(10, 64, 0), Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a long swim is published and adopted afloat before the far bank is reached`() {
		val crossing = crossLongPool(stallTicks = 0)
		assertTrue(crossing.afloat > 0 && crossing.executor.adoptions > crossing.afloat, "tapes must be published and adopted mid-pool, ending in a float: $crossing")
		assertEquals(0, crossing.floating, "a search that keeps up never leaves the body treading water")
	}

	@Test
	fun `a stalled search leaves the body treading water and resumes from the float`() {
		val crossing = crossLongPool(stallTicks = 120)
		assertTrue(crossing.floating in 20..120, "the body must tread water through the stall and resume within it: $crossing")
		assertTrue(crossing.plan.frames.none { it.state.eyeInWater }, "treading water keeps the eyes above the surface")
	}

	private class Crossing(val plan: TrajectoryPlan, val executor: VirtualExecutor, val afloat: Int) {
		val floating = plan.frames.indices.count { plan.tape[it].forward == 0.0 && plan.frames[it].state.touchingWater }

		override fun toString() = "frames=${plan.frames.size} floating=$floating afloat=$afloat adoptions=${executor.adoptions}"
	}

	/** Crosses a pool forty blocks long behind a slow search; the planner thread starves once a mid-pool tape is out. */
	private fun crossLongPool(stallTicks: Int): Crossing {
		val environment = pool(width = 40)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-2, 64, 0), Stance(42, 64, 0))
		assertTrue(planner.repair().converged, "the long pool must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the long pool must carry a route")
		val clock = VirtualSearchClock(microsPerExpansion = SLOW_SEARCH_MICROS)
		val executor = VirtualExecutor(clock)
		var afloat = 0
		val outcome = walkHorizon(
			route, planner, resting(Vec3d(-1.5, 64.0, 0.5), -90.0), ProbeScenarios.PROFILE, environment, MotionConstraints(),
			execution = executor, clock = clock, started = System.currentTimeMillis(),
			publish = { path, _ ->
				executor.offer(path)
				if (path.partial && path.plan.frames.last().state.touchingWater && ++afloat == STALL_AFTER_AFLOAT_TAPES) clock.stall(stallTicks)
			},
			world = object : WorldLink {
				override fun await(timeoutMillis: Long) = false
				override fun capturable(sectionX: Int, sectionZ: Int) = true
				override fun sync(route: CoarseRoutePlan, from: BodyState): WorldSyncResult = WorldSyncResult.Quiet
			},
		)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the long swim must arrive, got $outcome")
		return Crossing(path.plan, executor, afloat)
	}

	@Test
	fun `only swimming moves leave the water`() {
		val environment = pool(width = 8)
		val library = moveLibrary(SimpleMoveOptions(allowClimbing = true, allowSlimeBounces = true, allowDeepDropJumps = true))
		for (x in 0 until 8) {
			val stance = Stance(x, 63, 0)
			assertTrue(library.isStance(environment, stance), "the surface is a stance: $stance")
			val edges = library.edgesFrom(environment, stance)
			assertTrue(edges.isNotEmpty(), "the surface must have moves: $stance")
			assertTrue(edges.all { it.movement in MovementId.AQUATIC }, "no land move departs from water at $stance: ${edges.map { it.movement }}")
		}
		val bank = library.edgesFrom(environment, Stance(-1, 64, 0))
		assertTrue(bank.none { it.movement == MovementId.SWIM || it.movement == MovementId.EXIT_WATER }, "a bank is not water: $bank")
		assertTrue(bank.any { it.movement == MovementId.ENTER_WATER && it.to == Stance(0, 63, 0) }, "the bank must step into the pool: $bank")
	}

	@Test
	fun `the coarse swim rate is the simulated surface stroke`() {
		val environment = pool(width = 40)
		val start = MovementSimulationState.synthetic(ProbeScenarios.PROFILE, Vec3d(0.5, 63.3, 0.5), Rotation(-90.0, 0.0), onGround = false)
		val simulator = MovementSimulator(ProbeScenarios.PROFILE, environment, start)
		val xs = ArrayList<Double>()
		repeat(300) {
			val state = simulator.state
			xs += simulator.tickMovement(
				MovementSimulationInput(forward = 1.0, jump = state.touchingWater, sprint = true, rotation = Rotation(-90.0, 0.0)),
			).position.x
		}
		val from = xs.indexOfFirst { it >= 8.0 }
		val until = xs.indexOfFirst { it >= 28.0 }
		val rate = 20.0 / (until - from)
		assertTrue(abs(rate - CoarseMoveRates.SWIM_BLOCKS_PER_TICK) < 0.002, "measured $rate b/t against ${CoarseMoveRates.SWIM_BLOCKS_PER_TICK}")
	}

	private fun pool(width: Int): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -6..width + 6) for (z in -4..4) {
			blocks[BlockPos(x, 60, z)] = SnapshotBlockPhysics.FULL_CUBE
			val water = x in 0 until width
			for (y in 61..63) {
				blocks[BlockPos(x, y, z)] = when {
					!water -> SnapshotBlockPhysics.FULL_CUBE
					y == 63 -> SnapshotBlockPhysics.water()
					else -> SnapshotBlockPhysics.water(1.0f)
				}
			}
		}
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-10, 50, -10, width + 10, 80, 10), blocks)
	}

	private companion object {
		const val SLOW_SEARCH_MICROS = 20_000L

		const val STALL_AFTER_AFLOAT_TAPES = 2
	}
}
