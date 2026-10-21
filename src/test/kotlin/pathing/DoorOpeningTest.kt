/*
 * Copyright 2026 Lambda
 */
package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.BodyState
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.search.VirtualSearchClock
import com.lambda.pathing.search.WorldLink
import com.lambda.pathing.search.WorldSyncResult
import com.lambda.pathing.search.replaysUnchanged
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.edit.DoorRules
import com.lambda.pathing.world.edit.SnapshotRaycast
import com.lambda.pathing.world.snapshot.BlockPhysicsSampler
import com.lambda.pathing.world.snapshot.CellEdit
import com.lambda.pathing.world.snapshot.HazardRules
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.DoorBlock
import net.minecraft.block.FenceBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.block.enums.BlockHalf
import net.minecraft.block.enums.DoorHinge
import net.minecraft.block.ShapeContext
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.enums.DoubleBlockHalf
import net.minecraft.fluid.FluidState
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.BlockView
import net.minecraft.world.RaycastContext
import java.util.SplittableRandom
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoorOpeningTest {
	private val hazards = HazardRules(openDoors = true)
	private val bounds = SimulationSnapshotBounds(-16, 55, -16, 16, 80, 16)

	@Test
	fun `the snapshot raycast hits what the vanilla outline raycast hits`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -4..4) for (z in -4..4) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		door(states, BlockPos(0, 64, 2), Direction.NORTH, open = false)
		door(states, BlockPos(2, 64, 2), Direction.EAST, open = true)
		states[BlockPos(1, 64, 2)] = Blocks.OAK_FENCE_GATE.defaultState.with(FenceGateBlock.FACING, Direction.NORTH)
		states[BlockPos(-2, 64, 2)] = Blocks.OAK_FENCE_GATE.defaultState.with(FenceGateBlock.OPEN, true)
		states[BlockPos(-1, 64, 2)] = Blocks.OAK_FENCE.defaultState
		states[BlockPos(2, 64, -1)] = Blocks.OAK_SLAB.defaultState
		states[BlockPos(-2, 64, -1)] = Blocks.OAK_STAIRS.defaultState
		states[BlockPos(0, 64, -2)] = Blocks.TORCH.defaultState
		states[BlockPos(1, 65, -2)] = Blocks.OAK_SIGN.defaultState
		states[BlockPos(-1, 64, -3)] = Blocks.SNOW.defaultState
		val view = StatesView(states)
		val environment = environmentOf(states)
		val random = SplittableRandom(7)
		var hits = 0
		repeat(20_000) {
			val eye = Vec3d(random.nextDouble(-3.0, 3.0), random.nextDouble(64.05, 66.5), random.nextDouble(-3.0, 3.0))
			val rotation = Rotation(random.nextDouble(-180.0, 180.0), random.nextDouble(-90.0, 90.0))
			val end = eye.add(DoorRules.look(rotation).multiply(4.5))
			val vanilla = view.raycast(RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, ShapeContext.absent()))
			val ours = SnapshotRaycast.cast(environment, eye, end)
			if (vanilla.type == HitResult.Type.MISS) {
				assertNull(ours, "vanilla missed from $eye along $rotation")
			} else {
				hits++
				assertNotNull(ours, "vanilla hit ${vanilla.blockPos} from $eye along $rotation")
				assertEquals(vanilla.blockPos, ours.blockPos, "from $eye along $rotation")
				assertEquals(vanilla.side, ours.side, "from $eye along $rotation")
				assertEquals(vanilla.pos, ours.pos, "from $eye along $rotation")
			}
		}
		assertTrue(hits > 5_000, "the rays must mostly hit something: $hits")
	}

	@Test
	fun `a closed door on the route is opened on the way without stopping`() {
		MinecraftBootstrap.ensure()
		val closed = doorway(open = false)
		val path = walk(closed)
		val uses = path.plan.tape.asList().withIndex().filter { it.value.use != null }
		assertEquals(1, uses.size, "one use opens the door: ${uses.map { it.index }}")
		val (useFrame, input) = uses.single()
		val use = checkNotNull(input.use)
		assertTrue(use.open && (use.pos == BlockPos(0, 64, 0) || use.pos == BlockPos(0, 65, 0)), "the use opens the door: $use")
		assertEquals(listOf(useFrame, useFrame), path.plan.edits.map { it.frame }, "both halves open on the use frame")
		val before = if (useFrame == 0) path.plan.initialState else path.plan.frames[useFrame - 1].state
		assertTrue(-before.position.z > 1.0, "the door opens ahead of the body, not at it: ${before.position}")
		val doorFrame = path.plan.frames.indexOfFirst { it.state.position.z > -0.3 }
		assertTrue(useFrame > CRUISE_FRAMES, "the door opens on the run, not before the start: $useFrame")
		val slowest = path.plan.frames.subList(CRUISE_FRAMES, doorFrame + 4).minOf { hypot(it.state.velocity.x, it.state.velocity.z) }
		assertTrue(slowest > 0.14, "the body keeps its speed through the doorway: $slowest")

		val openWalk = walk(doorway(open = true))
		assertTrue(path.plan.tape.frameCount <= openWalk.plan.tape.frameCount, "opening the door costs no time: ${path.plan.tape.frameCount} vs ${openWalk.plan.tape.frameCount}")
	}

	@Test
	fun `a body looking at the floor raises its aim to the door`() {
		MinecraftBootstrap.ensure()
		val looking = Rotation(0.0, 35.0)
		val path = walk(doorway(open = false), looking)
		val useFrame = path.plan.tape.asList().indexOfFirst { it.use != null }
		val aimed = (if (useFrame == 0) path.plan.initialState else path.plan.frames[useFrame - 1].state).rotation.pitch
		assertTrue(aimed < looking.pitch, "the click looks up at the door, not at the floor: $aimed")
		val openWalk = walk(doorway(open = true), looking)
		assertTrue(path.plan.tape.frameCount <= openWalk.plan.tape.frameCount + SLACK_FRAMES, "aiming costs no time: ${path.plan.tape.frameCount} vs ${openWalk.plan.tape.frameCount}")
	}

	@Test
	fun `a closed fence gate is opened like a door`() {
		MinecraftBootstrap.ensure()
		val path = walk(gateway(open = false))
		assertEquals(listOf(BlockPos(0, 64, 0)), path.plan.tape.asList().mapNotNull { it.use?.pos }, "one use opens the gate")
		val openWalk = walk(gateway(open = true))
		assertTrue(path.plan.tape.frameCount <= openWalk.plan.tape.frameCount + SLACK_FRAMES, "opening the gate costs no time: ${path.plan.tape.frameCount} vs ${openWalk.plan.tape.frameCount}")
	}

	/**
	 * A side room opens into a corridor of doors through a door in the corridor wall. The corridor door in
	 * front of it swings shut against the entry side when opened, so the route must open the entry door,
	 * leave that corridor door closed, and open the doors further along the corridor.
	 */
	@Test
	fun `each door is set to the state the route needs`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -6..8) for (z in -4..6) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		for (x in -6..8) for (y in 64..66) {
			states[BlockPos(x, y, -1)] = Blocks.STONE.defaultState
			if (x != 0) states[BlockPos(x, y, 1)] = Blocks.STONE.defaultState
		}
		for (y in 64..66) for (z in 2..3) {
			states[BlockPos(-1, y, z)] = Blocks.STONE.defaultState
			states[BlockPos(1, y, z)] = Blocks.STONE.defaultState
		}
		for (y in 64..66) states[BlockPos(0, y, 4)] = Blocks.STONE.defaultState
		for (y in 64..66) states[BlockPos(-6, y, 0)] = Blocks.STONE.defaultState
		door(states, BlockPos(0, 64, 1), Direction.NORTH, open = false)
		for (x in 0..2) door(states, BlockPos(x, 64, 0), Direction.EAST, open = false, hinge = DoorHinge.RIGHT)
		val environment = environmentOf(states)
		val path = walk(environment, start = Stance(0, 64, 3), goal = Stance(6, 64, 0))
		val used = path.plan.tape.asList().mapNotNull { it.use?.pos?.let { pos -> BlockPos(pos.x, if (pos.y == 65) 64 else pos.y, pos.z) } }.toSet()
		assertTrue(BlockPos(0, 64, 1) in used, "the entry door opens: $used")
		assertFalse(BlockPos(0, 64, 0) in used, "the corridor door at the entry stays closed: $used")
		assertTrue(BlockPos(1, 64, 0) in used && BlockPos(2, 64, 0) in used, "the corridor doors further on open: $used")
	}

	/**
	 * Five open trapdoors hang in the floor across a corridor over a pit too wide to jump. Closed, they are
	 * floor. The route closes them while running up and crosses on them.
	 */
	@Test
	fun `open trapdoors over a pit are closed and walked on`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (z in -14..12) for (x in -1..1) states[BlockPos(x, 63, z)] = if (x == 0 && z in 0..4) {
			Blocks.OAK_TRAPDOOR.defaultState.with(TrapdoorBlock.HALF, BlockHalf.TOP).with(TrapdoorBlock.OPEN, true).with(TrapdoorBlock.FACING, Direction.NORTH)
		} else Blocks.STONE.defaultState
		for (z in -14..12) for (y in 64..66) {
			states[BlockPos(-1, y, z)] = Blocks.STONE.defaultState
			states[BlockPos(1, y, z)] = Blocks.STONE.defaultState
		}
		for (z in 0..4) for (y in 56..62) states[BlockPos(0, y, z)] = Blocks.AIR.defaultState
		val path = walk(environmentOf(states), start = Stance(0, 64, -10), goal = Stance(0, 64, 9))
		val closed = path.plan.tape.asList().mapNotNull { it.use }.filter { !it.open }.map { it.pos }.toSet()
		assertTrue(closed.isNotEmpty() && closed.all { it.x == 0 && it.y == 63 && it.z in 0..4 }, "trapdoors are closed to cross: $closed")
		assertTrue(path.plan.frames.all { it.state.position.y >= 63.9 }, "the body never drops into the pit")
	}

	@Test
	fun `with close doors the door is shut again once the body is past it`() {
		MinecraftBootstrap.ensure()
		val path = walk(doorway(open = false), constraints = MotionConstraints(openDoors = true, closeDoors = true))
		val uses = path.plan.tape.asList().mapNotNull { it.use }
		assertEquals(listOf(true, false), uses.map { it.open }, "the door is opened and then closed: $uses")
		val closing = path.plan.tape.asList().indexOfFirst { it.use?.open == false }
		val body = path.plan.frames[closing - 1].state.position
		assertTrue(body.z > 1.0, "the door closes behind the body: $body")
		val last = path.plan.edits.last { it.cell.y == 64 }
		assertEquals(false, last.cell.physics.toggle?.open, "the door ends closed")
	}

	@Test
	fun `a trapdoor opened as a step is closed again behind a closed gate`() {
		MinecraftBootstrap.ensure()
		val path = walk(stepCourse(), start = STEP_START, goal = STEP_GOAL, constraints = CLOSING)
		val uses = path.plan.tape.asList().withIndex().filter { it.value.use != null }.map { it.index to it.value.use!! }
		val opened = uses.firstOrNull { (_, use) -> use.pos == STEP_TRAPDOOR && use.open }?.first
		assertNotNull(opened, "the trapdoor is opened to stand on: $uses")
		val landed = path.plan.frames.indexOfFirst { it.state.onGround && it.state.position.x < 7.0 }
		assertTrue(opened <= landed, "the trapdoor is open by the landing: $opened vs $landed")
		assertTrue(path.plan.frames.none { it.state.onGround && it.state.position.x in 6.0..7.0 && it.state.position.y < 64.5 }, "the body never lands on the closed trapdoor")
		assertEquals(false, uses.lastOrNull { (_, use) -> use.pos == STEP_TRAPDOOR }?.second?.open, "the trapdoor ends closed: $uses")
		assertEquals(false, uses.lastOrNull { (_, use) -> use.pos == STEP_GATE }?.second?.open, "the gate ends closed: $uses")
	}

	@Test
	fun `a door this journey opened is closed behind even when the world already shows it open`() {
		MinecraftBootstrap.ensure()
		val environment = doorway(open = true)
		val door = BlockPos(0, 64, 0)
		val opened = listOf(door, door.up()).map { pos -> CellEdit(pos.x, pos.y, pos.z, assertNotNull(environment.toggleAt(pos.x, pos.y, pos.z)).physics(true)) }
		val path = walk(environment, constraints = CLOSING, rootEdits = opened)
		val uses = path.plan.tape.asList().mapNotNull { it.use }
		assertEquals(false, uses.lastOrNull { DoorRules.covers(environment, door, it.pos) }?.open, "the door opened earlier in the journey is closed behind: $uses")
	}

	@Test
	fun `an air room behind an open door deep under a lake is reached through a flooded tunnel`() {
		MinecraftBootstrap.ensure()
		val states = HashMap<BlockPos, BlockState>()
		for (x in -4..TUNNEL + 12) for (z in -4..10) for (y in 38..62) states[BlockPos(x, y, z)] = Blocks.STONE.defaultState
		for (x in 0..7) for (z in 0..7) for (y in 40..62) states[BlockPos(x, y, z)] = Blocks.WATER.defaultState
		for (x in 8 until 8 + TUNNEL) for (z in 2..5) for (y in 40..42) states[BlockPos(x, y, z)] = Blocks.WATER.defaultState
		door(states, BlockPos(8 + TUNNEL, 40, 3), Direction.EAST, open = true)
		for (x in 9 + TUNNEL..10 + TUNNEL) for (z in 2..5) for (y in 40..41) states.remove(BlockPos(x, y, z))
		val environment = environmentOf(states, bounds = SimulationSnapshotBounds(-8, 34, -8, TUNNEL + 16, 72, 14))
		val planner = CoarsePlanner(environment, ProbeScenarios.moveLibrary(SimpleMoveOptions(hazards = hazards)), Stance(-2, 63, 3), Stance(10 + TUNNEL, 40, 3))
		assertTrue(planner.repair().converged, "the route must converge")
		val route = assertNotNull(planner.routePlan(0L), "the room must be reachable under water")
		assertTrue(route.edges.any { it.movement == MovementId.EXIT_WATER && it.to == Stance(8 + TUNNEL, 40, 3) }, "the route leaves the water through the door: ${route.edges.map { "${it.movement}->${it.to}" }}")
	}

	@Test
	fun `a live walk never comes to rest on a trapdoor it still has to open`() {
		MinecraftBootstrap.ensure()
		for (micros in LIVE_MICROS_PER_EXPANSION) {
			val rests = ArrayList<MovementSimulationState>()
			val outcome = liveWalk(stepCourse(), STEP_START, STEP_GOAL, CLOSING, micros) { rests += it.plan.frames.last().state }
			val onTrapdoor = rests.filter { it.position.x in 6.0..7.0 && it.position.y < 64.5 }
			assertTrue(onTrapdoor.isEmpty(), "no published tape may stop on the closed trapdoor at $micros us per expansion: ${onTrapdoor.map { it.position }}")
			assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the live walk must arrive at $micros us per expansion, got $outcome")
		}
	}

	@Test
	fun `a door at a corner is clicked while strafing past`() {
		MinecraftBootstrap.ensure()
		fun corner(open: Boolean): SnapshotSimulationEnvironment {
			val states = HashMap<BlockPos, BlockState>()
			for (x in -2..8) for (z in -14..2) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
			for (y in 64..66) {
				for (z in -14..1) {
					states[BlockPos(-1, y, z)] = Blocks.STONE.defaultState
					if (z != 0) states[BlockPos(1, y, z)] = Blocks.STONE.defaultState
				}
				for (x in -1..8) states[BlockPos(x, y, 1)] = Blocks.STONE.defaultState
				for (x in 1..8) states[BlockPos(x, y, -1)] = Blocks.STONE.defaultState
			}
			door(states, BlockPos(1, 64, 0), Direction.EAST, open)
			return environmentOf(states)
		}
		val path = walk(corner(open = false), start = Stance(0, 64, -10), goal = Stance(6, 64, 0))
		assertEquals(listOf(BlockPos(1, 64, 0)), path.plan.tape.asList().mapNotNull { it.use?.pos?.let { pos -> BlockPos(pos.x, 64, pos.z) } }, "one use opens the corner door")
		val openWalk = walk(corner(open = true), start = Stance(0, 64, -10), goal = Stance(6, 64, 0))
		assertTrue(path.plan.tape.frameCount <= openWalk.plan.tape.frameCount + SLACK_FRAMES, "the corner door costs no time: ${path.plan.tape.frameCount} vs ${openWalk.plan.tape.frameCount}")
	}

	@Test
	fun `a door already open is the use done and a door closed again is a changed world`() {
		MinecraftBootstrap.ensure()
		val path = walk(doorway(open = false))
		val useFrame = path.plan.tape.asList().indexOfFirst { it.use != null }
		val frames = path.plan.tape.frameCount
		assertTrue(path.plan.replaysUnchanged(doorway(open = true), useFrame, frames), "a door opened by our own use replays the tape")
		assertTrue(path.plan.replaysUnchanged(doorway(open = false), useFrame, frames), "the use opens a door still closed")
		assertFalse(path.plan.replaysUnchanged(doorway(open = false), useFrame + 1, frames), "a door closed after the use blocks the tape")
	}

	@Test
	fun `without the setting a closed door stays a wall`() {
		MinecraftBootstrap.ensure()
		val environment = doorway(open = false, hazards = HazardRules())
		val planner = CoarsePlanner(environment, ProbeScenarios.moveLibrary(SimpleMoveOptions()), START, GOAL)
		planner.repair()
		assertNull(planner.routePlan(0L), "a closed door is no route unless doors may be opened")
		assertNull(environment.installedBlock(0, 64, 0)?.toggle, "no door knowledge is captured")
	}

	private fun walk(
		environment: SnapshotSimulationEnvironment,
		rotation: Rotation = Rotation(0.0, 0.0),
		start: Stance = START,
		goal: Stance = GOAL,
		constraints: MotionConstraints = MotionConstraints(openDoors = true),
		rootEdits: List<CellEdit> = emptyList(),
	) = run {
		val planner = CoarsePlanner(environment, ProbeScenarios.moveLibrary(SimpleMoveOptions(hazards = hazards)), start, goal)
		assertTrue(planner.repair().converged, "the route must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the toggles must carry a coarse route")
		val body = resting(Vec3d(start.x + 0.5, 64.0, start.z + 0.5)).copy(rotation = rotation)
		val outcome = walkFrom(route, planner, body, environment, constraints, rootEdits)
		val path = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the walk must be certified, got $outcome")
		assertEquals(goal, Stance.of(path.plan.frames.last().state.position, onGround = true))
		path
	}

	private fun liveWalk(
		environment: SnapshotSimulationEnvironment,
		start: Stance,
		goal: Stance,
		constraints: MotionConstraints,
		microsPerExpansion: Long,
		published: (PublishedPath) -> Unit,
	): PathPlanResult {
		val planner = CoarsePlanner(environment, ProbeScenarios.moveLibrary(SimpleMoveOptions(hazards = hazards)), start, goal)
		assertTrue(planner.repair().converged, "the route must converge")
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L), "the toggles must carry a coarse route")
		val clock = VirtualSearchClock(microsPerExpansion)
		val executor = VirtualExecutor(clock)
		return walkHorizon(
			route, planner, resting(Vec3d(start.x + 0.5, 64.0, start.z + 0.5)), ProbeScenarios.PROFILE, environment, constraints,
			execution = executor, clock = clock, started = System.currentTimeMillis(),
			publish = { path, _ ->
				published(path)
				executor.offer(path)
			},
			world = object : WorldLink {
				override fun await(timeoutMillis: Long) = false
				override fun loadable(chunkX: Int, chunkZ: Int) = true
				override fun sync(route: CoarseRoutePlan, from: BodyState): WorldSyncResult = WorldSyncResult.Quiet
			},
		)
	}

	private fun stepCourse(): SnapshotSimulationEnvironment {
		val states = HashMap<BlockPos, BlockState>()
		for (x in -1..15) for (z in -1..1) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		for (x in -1..15) for (y in 64..68) {
			states[BlockPos(x, y, -1)] = Blocks.STONE.defaultState
			states[BlockPos(x, y, 1)] = Blocks.STONE.defaultState
		}
		states[BlockPos(5, 64, 0)] = Blocks.STONE.defaultState
		for (x in 0..5) states[BlockPos(x, 65, 0)] = Blocks.STONE.defaultState
		states[STEP_TRAPDOOR] = Blocks.OAK_TRAPDOOR.defaultState.with(TrapdoorBlock.HALF, BlockHalf.BOTTOM).with(TrapdoorBlock.OPEN, false).with(TrapdoorBlock.FACING, Direction.EAST)
		states[STEP_GATE] = Blocks.OAK_FENCE_GATE.defaultState.with(FenceGateBlock.FACING, Direction.EAST).with(FenceGateBlock.OPEN, false)
		return environmentOf(states)
	}

	private fun doorway(open: Boolean, hazards: HazardRules = this.hazards): SnapshotSimulationEnvironment {
		val states = HashMap<BlockPos, BlockState>()
		for (x in -6..6) for (z in -14..8) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		for (x in -6..6) for (y in 64..67) if (x != 0 || y > 65) states[BlockPos(x, y, 0)] = Blocks.STONE.defaultState
		door(states, BlockPos(0, 64, 0), Direction.NORTH, open)
		return environmentOf(states, hazards)
	}

	private fun gateway(open: Boolean): SnapshotSimulationEnvironment {
		val states = HashMap<BlockPos, BlockState>()
		for (x in -6..6) for (z in -14..8) states[BlockPos(x, 63, z)] = Blocks.STONE.defaultState
		for (x in -6..6) if (x != 0) states[BlockPos(x, 64, 0)] = Blocks.OAK_FENCE.defaultState.with(FenceBlock.EAST, true).with(FenceBlock.WEST, true)
		states[BlockPos(0, 64, 0)] = Blocks.OAK_FENCE_GATE.defaultState.with(FenceGateBlock.FACING, Direction.NORTH).with(FenceGateBlock.OPEN, open)
		return environmentOf(states)
	}

	private fun door(states: MutableMap<BlockPos, BlockState>, lower: BlockPos, facing: Direction, open: Boolean, hinge: DoorHinge = DoorHinge.LEFT) {
		val state = Blocks.OAK_DOOR.defaultState.with(DoorBlock.FACING, facing).with(DoorBlock.OPEN, open).with(DoorBlock.HINGE, hinge)
		states[lower] = state.with(DoorBlock.HALF, DoubleBlockHalf.LOWER)
		states[lower.up()] = state.with(DoorBlock.HALF, DoubleBlockHalf.UPPER)
	}

	private fun environmentOf(states: Map<BlockPos, BlockState>, hazards: HazardRules = this.hazards, bounds: SimulationSnapshotBounds = this.bounds): SnapshotSimulationEnvironment {
		val sampler = BlockPhysicsSampler(StatesView(states), hazards)
		return SnapshotSimulationEnvironment.synthetic(bounds, states.keys.associateWith { sampler.physicsAt(it.x, it.y, it.z) })
	}

	private class StatesView(private val states: Map<BlockPos, BlockState>) : BlockView {
		override fun getBlockState(pos: BlockPos): BlockState = states[pos] ?: Blocks.AIR.defaultState
		override fun getFluidState(pos: BlockPos): FluidState = getBlockState(pos).fluidState
		override fun getBlockEntity(pos: BlockPos): BlockEntity? = null
		override fun getHeight(): Int = 384
		override fun getBottomY(): Int = -64
	}

	private companion object {
		val START = Stance(0, 64, -12)
		val GOAL = Stance(0, 64, 5)
		val STEP_START = Stance(13, 64, 0)
		val STEP_GOAL = Stance(2, 66, 0)
		val STEP_TRAPDOOR = BlockPos(6, 64, 0)
		val STEP_GATE = BlockPos(9, 64, 0)
		val CLOSING = MotionConstraints(openDoors = true, closeDoors = true)
		val LIVE_MICROS_PER_EXPANSION = listOf(150L, 350L, 640L, 1000L, 1300L)
		const val TUNNEL = 12
		const val CRUISE_FRAMES = 8
		const val SLACK_FRAMES = 3
	}
}
