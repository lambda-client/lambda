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
import com.lambda.pathing.physics.fallRule
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.PointedDripstoneBlock
import net.minecraft.block.ShapeContext
import net.minecraft.block.SlabBlock
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.Thickness
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Direction
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
	fun `an upward dripstone tip hurts from a one block fall`() {
		MinecraftBootstrap.ensure()
		val tip = Blocks.POINTED_DRIPSTONE.defaultState
			.with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.UP)
			.with(PointedDripstoneBlock.THICKNESS, Thickness.TIP)
		assertEquals(FallRule.STALAGMITE, tip.fallRule())
		assertEquals(FallRule.NORMAL, tip.with(PointedDripstoneBlock.VERTICAL_DIRECTION, Direction.DOWN).fallRule(), "a hanging tip is no stalagmite")
		assertEquals(FallRule.NORMAL, tip.with(PointedDripstoneBlock.THICKNESS, Thickness.BASE).fallRule(), "only the tip pierces")
		val limit = FallLimits().maxFallOnto(FallRule.STALAGMITE)
		assertTrue(limit in 0.99..1.0, "vanilla adds 2.5 blocks to the fall and doubles the damage: $limit")
		assertTrue(!FallRule.STALAGMITE.cushions, "a stalagmite is no soft landing")
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
	 * Leaving the top of a one block wide wall into a pool: the body can only gain speed along the wall, so it drops
	 * or jumps in from a standstill and swims on, never climbing back onto the wall to try a running jump it cannot
	 * build (the field dead-ended on such a jump; that dump is in the field-dump gate).
	 */
	@Test
	fun `a one block wide wall over a pool is left without a running jump it cannot build`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in 0..16) for (z in -6..6) states[BlockPos(x, 50, z)] = Blocks.STONE.defaultState
		for (x in 1..12) for (z in -6..6) for (y in 51..63) states[BlockPos(x, y, z)] = Blocks.WATER.defaultState
		for (z in -6..6) for (y in 51..63) states[BlockPos(0, y, z)] = Blocks.STONE.defaultState
		for (x in 13..16) for (z in -6..6) for (y in 51..63) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		val environment = environment(states)
		val start = Stance(0, 64, -3)
		val goal = Stance(14, 64, 2)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the wall must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the pool must carry a route")

		val outcome = walkFrom(route, planner, resting(Vec3d(0.5, 64.0, -2.5), 0.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "leaving the wall must be certified, got $outcome")
		val frames = path.plan.frames
		val wet = frames.indexOfFirst { it.state.touchingWater }
		assertTrue(wet >= 0, "the body must enter the pool")
		assertTrue(frames.drop(wet).none { it.state.onGround && it.state.position.x < 1.0 }, "once in the pool the body never climbs back onto the wall")
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
	fun `a waterlogged slab at the bottom of the world takes a fall from its top`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -8..14) for (z in -6..6) states[BlockPos(x, -62, z)] = Blocks.STONE.defaultState
		val cushion = Blocks.STONE_SLAB.defaultState.with(SlabBlock.WATERLOGGED, true)
		for (x in 1..2) for (z in -1..1) states[BlockPos(x, -61, z)] = cushion
		for (x in -4..0) for (z in -2..2) for (y in -61..313) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (x in 9..14) for (z in -6..6) states[BlockPos(x, -61, z)] = Blocks.STONE.defaultState
		val environment = environment(states, SimulationSnapshotBounds(-16, -64, -16, 24, 319, 16))
		val start = Stance(-2, 314, 0)
		val goal = Stance(12, -60, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the tower must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the water in a waterlogged slab cushions a fall of any height")
		val fall = assertNotNull(route.edges.firstOrNull { it.movement == MovementId.FALL }, "the route must fall: ${route.edges.map { it.movement }}")
		assertEquals(-60, fall.to.y, "the fall lands on the waterlogged slab: $fall")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 314.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the fall onto the waterlogged slab must be certified, got $outcome")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	@Test
	fun `a rim over an uncaptured column asks for the column below it`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -8..14) for (z in -6..6) states[BlockPos(x, 0, z)] = Blocks.STONE.defaultState
		for (x in -4..0) for (z in -2..2) for (y in 1..79) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (x in 1..2) for (z in -1..1) states[BlockPos(x, 1, z)] = Blocks.WATER.defaultState
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		val bounds = SimulationSnapshotBounds(-16, 0, -16, 31, 127, 31)
		val cells = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -16..31) for (y in 0..127) for (z in -16..31) cells[BlockPos(x, y, z)] = sampler.physicsAt(x, y, z)
		val uncaptured = ChunkSectionPos.asLong(0, 2, 0)
		val environment = SnapshotSimulationEnvironment(bounds, ImmutableSnapshotSection.sectionsOf(cells) - uncaptured, null)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-2, 80, 0), Stance(12, 1, 0))
		planner.repair()
		assertNull(planner.routePlan(0L), "the only way down falls through terrain nobody captured")
		planner.probeDropsFrom(Stance(-2, 80, 0), maxStances = 512)
		val asked = ArrayList<BlockPos>()
		planner.drainUnknownDrops { x, y, z -> asked += BlockPos(x, y, z) }
		assertTrue(asked.any { it.x == 1 && it.y == 47 }, "the rim must ask for the uncaptured column below it: $asked")
		planner.drainUnknownDrops { x, y, z -> error("drained twice: ($x, $y, $z)") }
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

	private fun environment(
		states: Map<BlockPos, BlockState>,
		bounds: SimulationSnapshotBounds = SimulationSnapshotBounds(-16, 50, -16, 24, 100, 16),
	): SnapshotSimulationEnvironment {
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		return SnapshotSimulationEnvironment.synthetic(bounds, states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) })
	}

	private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getHeight(): Int = 384
		override fun getBottomY(): Int = -64
	}
}
