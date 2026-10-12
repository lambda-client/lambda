package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.actions.InputTape
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.PathingChunk
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import com.lambda.pathing.search.AttemptAccumulator
import com.lambda.pathing.search.Certifier
import com.lambda.pathing.search.ExecutionFeedback
import com.lambda.pathing.search.MotionPlanResult
import com.lambda.pathing.search.Publications
import com.lambda.pathing.search.RouteState
import com.lambda.pathing.search.SearchProbe
import com.lambda.pathing.search.Solution
import com.lambda.pathing.search.ValueAnchor
import com.lambda.pathing.world.snapshot.ImmutableSnapshotSection
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import it.unimi.dsi.fastutil.longs.LongArrayList
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkSectionPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CertifierHistoryTest {
	private val profile = ProbeScenarios.PROFILE
	private val environment = SnapshotSimulationEnvironment.streaming(SimulationSnapshotBounds(-16, 96, -16, 95, 111, 15)) {}.apply {
		val stone = SnapshotBlockPhysics.of(VoxelShapes.fullCube())
		val floor = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in -2..80) for (z in -2..2) floor[BlockPos(x, 99, z)] = stone
		ImmutableSnapshotSection.sectionsOf(floor).forEach { (key, section) -> install(key, section) }
	}
	private val east = Rotation(-90.0, 0.0)
	private val sprint = MovementSimulationInput(forward = 1.0, sprint = true, rotation = east)
	private val walk = MovementSimulationInput(forward = 1.0, rotation = east)
	private val idle = MovementSimulationInput(rotation = east)
	private val initial = MovementSimulationState.synthetic(profile, Vec3d(2.5, 100.0, 0.5), east)
	private val root = ValueAnchor(initial, Stance(2, 100, 0), 0, 0, 0, null, emptyList())
	private val executed = 100
	private val feedback = object : ExecutionFeedback {
		override fun cursorFrame(): Int = executed
		override fun adoptedSequence(): Long = 1L
		override fun refusedSequence(): Long = 0L
	}
	private val publications = Publications(feedback)
	private val certifier = Certifier(
		initial, profile, environment, { false },
		RouteState(CoarseRoutePlan(0L, listOf(root.stance), emptyList(), 0.0, emptySet()), environment),
		AttemptAccumulator(SearchProbe.NONE), publications,
	)

	private fun extend(parent: ValueAnchor, inputs: List<MovementSimulationInput>): ValueAnchor {
		val frames = simulate(parent.state, inputs)
		val state = frames.last().state
		return ValueAnchor(state, Stance(state.position.x.toInt(), 100, 0), parent.elapsed + inputs.size, 0, 0, parent, inputs)
	}

	private fun simulate(from: MovementSimulationState, inputs: List<MovementSimulationInput>): List<SimulatedTrajectoryFrame> =
		TrajectoryRolloutEngine.rollout(from, profile, environment, InputTape(inputs), inputs.size).frames

	private fun braked(anchor: ValueAnchor) = Solution(anchor, simulate(anchor.state, List(40) { idle }))

	private fun publishRunning(): Pair<ValueAnchor, MotionPlanResult.Success> {
		val committed = extend(root, List(executed) { sprint })
		val solution = braked(committed)
		val plan = assertIs<MotionPlanResult.Success>(certifier.certify(solution))
		publications.record(committed, extend(committed, solution.tailFrames.map { it.input }), plan)
		return committed to plan
	}

	private fun dropStartChunk() {
		environment.removeSections(LongArrayList.of(ChunkSectionPos.asLong(0, 6, 0)))
		certifier.forget(emptySet(), setOf(PathingChunk(0, 0)), executed)
	}

	@Test
	fun `a continuation certifies after the terrain under executed frames is gone`() {
		val (committed, running) = publishRunning()
		dropStartChunk()
		val continued = assertIs<MotionPlanResult.Success>(certifier.certify(braked(extend(committed, List(60) { sprint })))).plan
		assertEquals(running.plan.frames.subList(0, executed), continued.frames.subList(0, executed))
	}

	@Test
	fun `the running tape supplies executed frames when the last certified branch forked before the cursor`() {
		val (committed, running) = publishRunning()
		assertIs<MotionPlanResult.Success>(certifier.certify(braked(extend(extend(root, List(40) { sprint }), List(60) { walk }))))
		dropStartChunk()
		val continued = assertIs<MotionPlanResult.Success>(certifier.certify(braked(extend(committed, List(60) { sprint })))).plan
		assertEquals(running.plan.frames.subList(0, executed), continued.frames.subList(0, executed))
	}
}
