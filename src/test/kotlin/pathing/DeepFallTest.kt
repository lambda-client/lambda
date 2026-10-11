/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.FallLimits
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockView
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Falls deeper than any walk-off template: the coarse layer finds them by scanning the columns
 * below a rim, so their depth has no template cap, and they land only where the landing is
 * harmless -- water deep enough for the speed it is hit at, a hay bale within the fall it
 * cushions, or any floor the accepted fall damage allows.
 */
class DeepFallTest {
	@Test
	fun `vanilla fall damage decides how deep each landing may be`() {
		val vanilla = FallLimits()
		assertTrue(vanilla.maxFallOnto(FallRule.NORMAL) in 3.99..4.0, "four blocks onto stone already cost half a heart: ${vanilla.maxFallOnto(FallRule.NORMAL)}")
		assertTrue(vanilla.maxFallOnto(FallRule.HAY) in 7.99..8.0, "hay takes four fifths off, so eight blocks are free: ${vanilla.maxFallOnto(FallRule.HAY)}")
		assertTrue(vanilla.maxFallOnto(FallRule.BED) in 7.99..8.0, "a bed halves the distance: ${vanilla.maxFallOnto(FallRule.BED)}")
		assertEquals(Double.POSITIVE_INFINITY, vanilla.maxFallOnto(FallRule.NONE))
		val halfHeart = FallLimits(acceptedDamage = 1)
		assertTrue(halfHeart.maxFallOnto(FallRule.NORMAL) in 4.99..5.0, "half a heart is a five block fall onto stone: ${halfHeart.maxFallOnto(FallRule.NORMAL)}")
		assertTrue(halfHeart.maxFallOnto(FallRule.HAY) in 12.99..13.0, "and a thirteen block fall onto hay: ${halfHeart.maxFallOnto(FallRule.HAY)}")
		assertEquals(Double.POSITIVE_INFINITY, FallLimits.IMMUNE.maxFallOnto(FallRule.NORMAL), "flight and Slow Falling take any fall")
	}

	@Test
	fun `a tower standing in a lake is left by falling into the water`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -8..8) for (z in -6..6) {
			states[BlockPos(x, 60, z)] = Blocks.STONE.defaultState
			for (y in 61..63) states[BlockPos(x, y, z)] = Blocks.WATER.defaultState
		}
		for (x in -4..0) for (z in -2..2) for (y in 61..80) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (x in 9..14) for (z in -6..6) for (y in 60..63) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		val environment = environment(states)
		val start = Stance(-2, 81, 0)
		val goal = Stance(12, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the tower must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the only way off the tower is a fall into the lake")
		assertTrue(route.edges.any { it.movement == MovementId.FALL }, "the route must fall: ${route.edges.map { it.movement }}")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 81.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the fall must be certified, got $outcome")
		val frames = path.plan.frames
		assertTrue(frames.any { it.state.touchingWater }, "the body must land in the water")
		assertEquals(goal, Stance.of(frames.last().state.position, onGround = true))
	}

	/**
	 * Vanilla re-checks water at the landing position before it judges a fall, so a body that hits the floor of a
	 * single water layer in the very tick it enters the water is unhurt however fast it falls.
	 */
	@Test
	fun `a single layer of water takes a thirty block fall`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -8..14) for (z in -6..6) states[BlockPos(x, 60, z)] = Blocks.STONE.defaultState
		for (x in -8..8) for (z in -6..6) states[BlockPos(x, 61, z)] = Blocks.WATER.defaultState
		for (x in -4..0) for (z in -2..2) for (y in 61..95) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (x in 9..14) for (z in -6..6) states[BlockPos(x, 61, z)] = Blocks.STONE.defaultState
		val environment = environment(states)
		val start = Stance(-2, 96, 0)
		val goal = Stance(12, 62, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the tower must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "a single water layer is a landing for any fall")
		val fall = assertNotNull(route.edges.firstOrNull { it.movement == MovementId.FALL }, "the route must fall: ${route.edges.map { it.movement }}")
		assertEquals(61, fall.to.y, "the fall lands in the water layer: $fall")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 96.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the fall into shallow water must be certified, got $outcome")
		val frames = path.plan.frames
		val airborne = frames.indexOfFirst { !it.state.onGround }
		val landing = frames.drop(airborne).first { it.state.onGround }.state
		assertTrue(landing.touchingWater && landing.position.y < 61.5, "the thirty block fall ends on the floor of the water layer: $landing")
		assertEquals(goal, Stance.of(frames.last().state.position, onGround = true))
	}

	@Test
	fun `a hay bale seven blocks down cushions the fall`() {
		val (environment, start, goal) = hayTower(hayTop = 74)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the hay tower must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "a seven block fall onto hay is harmless")
		val fall = assertNotNull(route.edges.firstOrNull { it.movement == MovementId.FALL }, "the route must fall: ${route.edges.map { it.movement }}")
		assertEquals(73, fall.to.y - 1, "the fall lands on the hay, not the ground beside it: $fall")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 81.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the fall onto hay must be certified, got $outcome")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	@Test
	fun `a hay bale nine blocks down is taken only when half a heart is accepted`() {
		val (environment, start, goal) = hayTower(hayTop = 72)
		val refused = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		refused.repair()
		assertNull(refused.routePlan(0L), "nine blocks onto hay costs half a heart, which the default limits refuse")

		val limits = FallLimits(acceptedDamage = 1)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions(fallLimits = limits)), start, goal)
		assertTrue(planner.repair().converged, "the accepting planner must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "accepting half a heart opens the hay")
		val outcome = walkHorizon(
			route, planner, resting(Vec3d(-1.5, 81.0, 0.5), -90.0), ProbeScenarios.PROFILE, environment,
			MotionConstraints(fallLimits = limits),
			publish = { _, _ -> },
			started = System.currentTimeMillis(),
		)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the accepted fall must be certified, got $outcome")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	private fun hayTower(hayTop: Int): Triple<SnapshotSimulationEnvironment, Stance, Stance> {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -8..14) for (z in -6..6) for (y in 60..71) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (x in -4..0) for (z in -2..2) for (y in 72..80) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (y in 72 until hayTop) for (x in 1..2) for (z in -1..1) states[BlockPos(x, y, z)] = Blocks.HAY_BLOCK.defaultState
		if (hayTop == 72) for (x in 1..2) for (z in -1..1) states[BlockPos(x, 71, z)] = Blocks.HAY_BLOCK.defaultState
		return Triple(environment(states), Stance(-2, 81, 0), Stance(10, 72, 0))
	}

	private fun environment(states: Map<BlockPos, BlockState>): SnapshotSimulationEnvironment {
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		return SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-16, 50, -16, 24, 100, 16),
			states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
		)
	}

	private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getHeight(): Int = 384
		override fun getBottomY(): Int = -64
	}
}
