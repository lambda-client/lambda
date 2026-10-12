package pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import minecraft.MinecraftBootstrap
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.moveLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SaveFoodTest {
	init {
		MinecraftBootstrap.ensure()
	}

	private val environment = SnapshotSimulationEnvironment.synthetic(
		SimulationSnapshotBounds(-8, 56, -8, 56, 80, 8),
		buildMap { for (x in -6..54) for (z in -4..4) put(BlockPos(x, 63, z), SnapshotBlockPhysics.FULL_CUBE) },
	)

	private fun walk(options: SimpleMoveOptions, constraints: MotionConstraints): List<Pair<Boolean, Boolean>> {
		val planner = CoarsePlanner(environment, moveLibrary(options), Stance(0, 64, 0), Stance(48, 64, 0))
		assertTrue(planner.repair().converged)
		planner.expandSearchField()
		val route = assertNotNull(planner.routePlan(0L))
		val outcome = walkFrom(route, planner, resting(Vec3d(0.5, 64.0, 0.5), -90.0), environment, constraints)
		val frames = assertNotNull((outcome as? PathPlanResult.Planned)?.path?.takeUnless { it.partial }, "the run must arrive, got $outcome").plan.frames
		return frames.zipWithNext().map { (before, after) -> (after.input.jump && before.state.onGround && !after.state.onGround) to after.state.isSprinting }
	}

	@Test
	fun `save food walks a flat run without sprinting or jumping`() {
		val normal = walk(SimpleMoveOptions(), MotionConstraints())
		val frugal = walk(SimpleMoveOptions(jumpPenaltyTicks = 20.0), MotionConstraints(sprintModes = listOf(false), jumpPenaltyTicks = 20.0))
		println("[food] normal jumps=${normal.count { it.first }} sprint=${normal.count { it.second }} frugal jumps=${frugal.count { it.first }} sprint=${frugal.count { it.second }}")
		assertTrue(normal.any { it.second }, "the default run sprints")
		assertEquals(0, frugal.count { it.second }, "save food never sprints")
		assertEquals(0, frugal.count { it.first }, "save food does not jump on flat ground")
	}
}
