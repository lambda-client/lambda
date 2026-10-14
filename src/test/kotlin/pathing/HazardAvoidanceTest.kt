/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.UnsupportedPhysicsKind
import com.lambda.pathing.rollout.RolloutEvaluator
import com.lambda.pathing.rollout.RolloutVerdict
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.CampfireBlock
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.EntityType
import net.minecraft.entity.MarkerEntity
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockView
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Blocks that hurt the body it touches. Fire has no collision box, so it was captured as air and
 * walked through, setting the body alight; a cactus is a sixteenth narrower than its cell on every
 * side, so it was a partial block the body stood on and brushed past, and vanilla pricks any body
 * whose box enters the cell. Both are hazards: the coarse route goes round them and no certified
 * trajectory lets the body touch their cells. Ignore Hazards captures them as the blocks they are
 * again, for courses that can only be crossed by taking the damage.
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
	fun `powder snow is captured as the snow a walking body sinks into even when captured mid-fall`() {
		MinecraftBootstrap.ensure()
		val state = Blocks.POWDER_SNOW.defaultState
		val view = StatesView(mapOf(BlockPos.ORIGIN to state))
		val falling = MarkerEntity(EntityType.MARKER, null).apply { fallDistance = 3.0 }
		assertTrue(!state.getCollisionShape(view, BlockPos.ORIGIN, ShapeContext.of(falling)).isEmpty, "vanilla catches a body falling more than 2.5 blocks")
		val captured = BlockPhysicsCapture.capture(state, view, BlockPos.ORIGIN, ShapeContext.of(falling))
		assertTrue(captured.collisionShape.isEmpty, "a capture taken while the player falls must not turn powder snow into a floor")
		assertEquals(UnsupportedPhysicsKind.POWDER_SNOW, captured.unsupportedPhysics?.kind)
	}

	@Test
	fun `avoiding lava edges keeps landings off lava, lets walking pass over it and is off by default`() {
		MinecraftBootstrap.ensure()
		val environment = SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-8, 0, -8, 8, 16, 8),
			mapOf(BlockPos(0, 4, 0) to SnapshotBlockPhysics.FULL_CUBE, BlockPos(0, 4, 1) to captured(Blocks.LAVA.defaultState)),
		)
		fun body(y: Double, z: Double, grounded: Boolean) =
			MovementSimulationState.synthetic(ProbeScenarios.PROFILE, Vec3d(0.5, y, z), Rotation(0.0, 0.0), onGround = grounded)
		val centre = HorizontalPoint(0.5, 5.0, 0.5)
		fun verdict(avoid: Boolean, from: MovementSimulationState, to: MovementSimulationState) = RolloutEvaluator(
			from, listOf(centre), centre, MotionConstraints(avoidLavaEdges = avoid),
			climbing = { false }, lava = { environment.lavaWithin(it) },
		).observe(1, to)
		assertEquals(RolloutVerdict.Continue, verdict(true, body(6.0, 0.5, false), body(5.0, 0.5, true)), "a landing centred on the block is clear of the lava")
		assertIs<RolloutVerdict.Failed>(verdict(true, body(6.0, 0.85, false), body(5.0, 0.85, true)), "a jump landing with the feet 0.15 over the lava lands on its edge")
		assertEquals(RolloutVerdict.Continue, verdict(true, body(5.0, 0.5, true), body(5.0, 0.85, true)), "walking past the edge stays allowed")
		assertEquals(RolloutVerdict.Continue, verdict(true, body(5.2, 0.85, false), body(5.0, 0.85, true)), "a hop under a low ceiling barely falls and stays allowed")
		assertIs<RolloutVerdict.Failed>(verdict(true, body(5.2, 1.2, false), body(5.0, 1.2, true)), "even a low hop may not land with the centre over the lava")
		assertEquals(RolloutVerdict.Continue, verdict(false, body(6.0, 0.85, false), body(5.0, 0.85, true)), "off, the planner follows vanilla, which lands on any edge")
	}

	/**
	 * A parkour course's lava lake: a single gold block with a gold wall behind it, four blocks of lava to a pair of
	 * magma blocks one level lower, and lava beside and behind the landing. Every jump the body can make without a
	 * run-up lands with its feet hanging over the lava. Avoiding lava edges, the body backs up to the wall, sprints
	 * and jumps on the last tick it still counts as grounded, past the block's edge, and lands clear of the lava.
	 */
	@Test
	fun `a long jump off a single block lands clear of the lava when avoiding lava edges`() {
		MinecraftBootstrap.ensure()
		val layers = mapOf(
			63 to listOf(
				"~~~GGGnGnBB", "~~~~GGGGnnB", "G~~~~MnGGnn", "G~~~~~MMnnn", "~~~~~~~~Mnn", "~~~~~~~~Mnn", "~~~~~~~Mnnn",
				"~GGG~~~Mnnn", "~GGGG~~~nnn", "GGGGG~~~Mnn", "GGG~~~~~~MM", "GG~~~~~~~~~", "~~~~gg~~~~~",
			),
			64 to listOf(
				"....GGG....", "...........", "GG.........", "G.........n", ".........nn", "........Mnn", ".......nnnn",
				"..GG...nnnn", ".GGGG...nnn", "GGGG....nnn", "GGG......nM", "GB.........", "...........",
			),
			65 to listOf(
				"n....G.....", "...........", "...........", "..........n", ".........nn", "........nnn", ".......nnnn",
				"..G....nnnn", "GGGG....nnn", "GGGG....nnn", "BGG......nn", "...........", "...........",
			),
			66 to listOf(
				"n..........", "...........", "...........", "..........n", "........nnn", "........nnn", ".......nnnn",
				".......nnnn", ".GGG....nnn", ".GG.....nnn", ".G.......nn", "...........", "...........",
			),
			67 to listOf(
				"nn.........", "...........", "..........n", ".........nn", "........nnn", ".......nnnn", ".......nnnn",
				"......nnnnn", ".GGG...nnnn", ".GG....nnnn", ".B.......nn", "...........", "...........",
			),
		)
		val blocks = mapOf(
			'~' to Blocks.LAVA.defaultState, 'G' to Blocks.GOLD_BLOCK.defaultState, 'n' to Blocks.NETHERRACK.defaultState,
			'M' to Blocks.MAGMA_BLOCK.defaultState, 'B' to Blocks.NETHER_BRICKS.defaultState, 'g' to Blocks.GRAVEL.defaultState,
		)
		val states = HashMap<BlockPos, BlockState>()
		for (x in -4..6) for (z in -8..4) states[BlockPos(x, 62, z)] = Blocks.NETHERRACK.defaultState
		for ((y, rows) in layers) for ((row, line) in rows.withIndex()) for ((column, symbol) in line.withIndex()) {
			blocks[symbol]?.let { states[BlockPos(column - 4, y, row - 8)] = it }
		}
		val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent())
		val environment = SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16),
			states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
		)
		val goal = Stance(2, 64, -5)
		val planner = CoarsePlanner(environment, moveLibrary(SimpleMoveOptions()), Stance(0, 65, 0), goal)
		assertTrue(planner.repair().converged, "the lava lake must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the gold block must have a coarse jump onto the magma")

		val outcome = walkFrom(route, planner, resting(Vec3d(0.5, 65.0, 0.5), 180.0), environment, MotionConstraints(avoidLavaEdges = true))
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the jump must be certified, got $outcome")
		val overLava = path.plan.frames.filter { frame ->
			val box = frame.state.boundingBox
			frame.state.onGround && environment.lavaWithin(Box(box.minX, box.minY - 0.05, box.minZ, box.maxX, box.minY, box.maxZ))
		}
		assertTrue(overLava.isEmpty(), "the feet must never stand over lava, did at ${overLava.map { it.index }}")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
	}

	@Test
	fun `ignored hazards are captured as the blocks they are`() {
		MinecraftBootstrap.ensure()
		for (state in listOf(Blocks.FIRE.defaultState, Blocks.SOUL_FIRE.defaultState)) {
			val fire = captured(state, ignoreHazards = true)
			assertNull(fire.unsupportedPhysics, "$state")
			assertTrue(fire.coarseVoxel.fullyPassable, "$state has no collision box")
		}
		assertNull(captured(Blocks.CAMPFIRE.defaultState, ignoreHazards = true).unsupportedPhysics, "a lit campfire is a slab to stand on")
		val cactus = captured(Blocks.CACTUS.defaultState, ignoreHazards = true)
		assertNull(cactus.unsupportedPhysics)
		assertEquals(15.0 / 16.0, assertNotNull(cactus.coarseVoxel.standingSurface, "a cactus is a floor"), 1e-9)
		assertEquals(
			UnsupportedPhysicsKind.SWEET_BERRY_BUSH, captured(Blocks.SWEET_BERRY_BUSH.defaultState, ignoreHazards = true).unsupportedPhysics?.kind,
			"a berry bush also slows the body, which the simulator does not model",
		)
	}

	/**
	 * A chasm five cells wide is too far for any jump, and the only footing in it is a cactus on a sand pillar,
	 * two cells from either rim. Avoiding hazards there is no route; ignoring them, the walk hops across on the cactus.
	 */
	@Test
	fun `a chasm with a cactus as its only stepping stone is crossed only when hazards are ignored`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (z in -1..1) {
			for (x in -6..0) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
			for (x in 6..10) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		}
		for (y in 52..61) states[BlockPos(3, y, 0)] = Blocks.STONE.defaultState
		states[BlockPos(3, 62, 0)] = Blocks.SAND.defaultState
		states[BlockPos(3, 63, 0)] = Blocks.CACTUS.defaultState
		val start = Stance(-4, 64, 0)
		val goal = Stance(8, 64, 0)
		fun environment(ignoreHazards: Boolean): SnapshotSimulationEnvironment {
			val sampler = BlockPhysicsSampler(StatesView(states), ShapeContext.absent(), ignoreHazards)
			return SnapshotSimulationEnvironment.synthetic(
				SimulationSnapshotBounds(-16, 50, -16, 24, 80, 16),
				states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) },
			)
		}

		val avoided = CoarsePlanner(environment(ignoreHazards = false), moveLibrary(SimpleMoveOptions()), start, goal)
		avoided.repair()
		assertNull(avoided.routePlan(0L), "the cactus is the only way across and it is a hazard")

		val ignoring = environment(ignoreHazards = true)
		val moves = moveLibrary(SimpleMoveOptions(ignoreHazards = true))
		val planner = CoarsePlanner(ignoring, moves, start, goal)
		assertTrue(planner.repair().converged, "the chasm must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the cactus carries the route across")
		val outcome = walkFrom(route, planner, resting(Vec3d(-3.5, 64.0, 0.5), -90.0), ignoring)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the crossing must be certified, got $outcome")
		val cactusTop = Box(3.0, 63.9, 0.0, 4.0, 64.0, 1.0)
		assertTrue(path.plan.frames.any { it.state.onGround && it.state.boundingBox.intersects(cactusTop) }, "the walk must touch down on the cactus")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
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

	private fun captured(state: BlockState, ignoreHazards: Boolean = false) =
		BlockPhysicsCapture.capture(state, StatesView(mapOf(BlockPos.ORIGIN to state)), BlockPos.ORIGIN, ShapeContext.absent(), ignoreHazards)

	private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getHeight(): Int = 384
		override fun getBottomY(): Int = -64
	}
}
