/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CampfireBlock
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockView
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blocks that hurt the body it touches. Fire has no collision box, so it was captured as air and
 * walked through, setting the body alight; a cactus is a sixteenth narrower than its cell on every
 * side, so it was a partial block the body stood on and brushed past, and vanilla pricks any body
 * whose box enters the cell. Both are hazards: the coarse route goes round them and no certified
 * trajectory lets the body touch their cells.
 */
class HazardAvoidanceTest {
	@Test
	fun `fire and lit campfires are hazards and an unlit campfire is not`() {
		MinecraftBootstrap.ensure()
		val burning = listOf(Blocks.FIRE.defaultState, Blocks.SOUL_FIRE.defaultState, Blocks.CAMPFIRE.defaultState, Blocks.SOUL_CAMPFIRE.defaultState)
		for (state in burning) {
			assertEquals(UnsupportedPhysicsKind.FIRE, captured(state).unsupportedPhysics?.kind, "$state burns")
		}
		assertNull(captured(Blocks.CAMPFIRE.defaultState.with(CampfireBlock.LIT, false)).unsupportedPhysics, "an unlit campfire is a block to stand on")
	}

	@Test
	fun `a cactus is a hazard`() {
		MinecraftBootstrap.ensure()
		assertEquals(UnsupportedPhysicsKind.CACTUS, captured(Blocks.CACTUS.defaultState).unsupportedPhysics?.kind)
	}

	@Test
	fun `a corridor with fire down its middle is walked round the fire`() = walkedRound { Blocks.FIRE.defaultState }

	@Test
	fun `a corridor with cacti down its middle is walked round them without a prick`() = walkedRound { Blocks.CACTUS.defaultState }

	/**
	 * Magma burns feet that stand on it unless the body sneaks, so every tick on it is a sneaking tick. The server
	 * also moves the player itself each tick from the last packet before judging the floor, so it lands the body one
	 * tick ahead of the client: the tick before standing on magma must already have sent the sneak key.
	 */
	@Test
	fun `a magma floor is crossed sneaking`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -4..8) for (z in -1..1) states[BlockPos(x, 63, z)] = if (x in 1..4) Blocks.MAGMA_BLOCK.defaultState else Blocks.STONE.defaultState
		for (z in listOf(-2, 2)) for (x in -4..8) for (y in 64..65) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		val environment = SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16),
			states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
		)
		val goal = Stance(6, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-2, 64, 0), goal)
		assertTrue(planner.repair().converged, "the magma corridor must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "magma is still a floor to cross")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 64.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the crossing must be certified, got $outcome")
		val frames = path.plan.frames
		val onMagma = frames.indices.filter { frames[it].state.onGround && frames[it].state.position.x in 1.0..<5.0 }
		assertTrue(onMagma.isNotEmpty(), "the crossing must stand on the magma")
		val burnt = onMagma.flatMap { listOf(it - 1, it) }.distinct().filter { it >= 0 && !frames[it].input.sneak }
		assertTrue(burnt.isEmpty(), "every tick standing on magma and the tick before it must sneak, burnt at $burnt")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	/**
	 * Parkour stacks a carpet on a cactus: the carpet's stance is two cells above the ground, but its
	 * top is only a block and a sixteenth up, inside a jump's reach. The jump must clear the cactus's
	 * cell on the way up and land on the carpet.
	 */
	@Test
	fun `a carpet on a cactus one block up is jumped onto without touching the cactus`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -4..6) for (z in -2..2) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		states[BlockPos(2, 64, 0)] = Blocks.CACTUS.defaultState
		states[BlockPos(2, 65, 0)] = Blocks.GREEN_CARPET.defaultState
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		val environment = SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16),
			states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
		)
		val goal = Stance(2, 66, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(-1, 64, 0), goal)
		assertTrue(planner.repair().converged, "the carpet on the cactus must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the carpet is a jump up from the ground")

		val outcome = walkFrom(route, planner, resting(Vec3d(-0.5, 64.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the jump must be certified, got $outcome")
		val cactus = Box(2.0, 64.0, 0.0, 3.0, 65.0, 1.0)
		assertTrue(path.plan.frames.none { it.state.boundingBox.intersects(cactus) }, "the body must never enter the cactus's cell")
		val last = path.plan.frames.last().state
		assertTrue(last.onGround && last.position.y in 65.0..65.1 && last.position.x in 2.0..3.0 && last.position.z in 0.0..1.0, "the body ends on the carpet, at ${last.position}")
	}

	private fun walkedRound(hazardOf: () -> BlockState) {
		MinecraftBootstrap.ensure()
		val hazard = hazardOf()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -4..8) for (z in -1..1) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		for (z in listOf(-2, 2)) for (x in -4..8) for (y in 64..65) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		val fires = listOf(BlockPos(2, 64, 0), BlockPos(3, 64, 0))
		for (fire in fires) states[fire] = hazard
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		val environment = SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16),
			states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
		)
		val start = Stance(-2, 64, 0)
		val goal = Stance(6, 64, 0)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), start, goal)
		assertTrue(planner.repair().converged, "the corridor must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the corridor beside the $hazard must carry a coarse route")
		assertTrue(fires.none { Stance(it.x, it.y, it.z) in route.nodes || Stance(it.x, it.y + 1, it.z) in route.nodes }, "the route must not enter or stand on the $hazard: ${route.nodes}")

		val outcome = walkFrom(route, planner, resting(Vec3d(-1.5, 64.0, 0.5), -90.0), environment)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "a walk round the $hazard must be certified, got $outcome")
		val touched = path.plan.frames.filter { frame -> fires.any { fire -> frame.state.boundingBox.intersects(Box(fire)) } }
		assertTrue(touched.isEmpty(), "the body must never touch a $hazard cell, touched at ${touched.map { it.index }}")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	private fun captured(state: BlockState) = BlockPhysicsCapture.capture(state, StatesView(mapOf(BlockPos.ORIGIN to state)), BlockPos.ORIGIN, ShapeContext.absent())

	private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getHeight(): Int = 384
		override fun getBottomY(): Int = -64
	}
}
