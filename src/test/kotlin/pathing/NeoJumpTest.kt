/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.session.PathPlanResult
import com.lambda.pathing.session.walkHorizon
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import pathing.ProbeScenarios.PROFILE
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A wall three high stands right after the take-off with no floor beside it, so the only way over is around its
 * side: run out over the open side, jump beside the wall and steer back onto the landing behind it. Layouts are
 * local (forward, side) and turned into each heading.
 */
class NeoJumpTest {
	private class Layout(val walls: List<Int>, val landing: Int, val rise: Int)

	private fun course(layout: Layout, fx: Int, fz: Int, sx: Int, sz: Int, runUp: Int = 3, landingLength: Int = 3, floor: Int = 63): Pair<SnapshotSimulationEnvironment, Stance> {
		fun at(forward: Int, side: Int, y: Int) = BlockPos(fx * forward + sx * side, y, fz * forward + sz * side)
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (forward in -runUp..0) blocks[at(forward, 0, floor)] = SnapshotBlockPhysics.FULL_CUBE
		for (wall in layout.walls) for (y in floor + 1..floor + 4) blocks[at(wall, 0, y)] = SnapshotBlockPhysics.FULL_CUBE
		for (forward in layout.landing until layout.landing + landingLength) blocks[at(forward, 0, floor + layout.rise)] = SnapshotBlockPhysics.FULL_CUBE
		for (y in floor + 1..floor + 4) blocks[at(1, -1, y)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 40, -16, 16, 90, 16), blocks)
		val goal = at(layout.landing + landingLength - 1, 0, floor + layout.rise + 1)
		return environment to Stance(goal.x, goal.y, goal.z)
	}

	private fun neo(layout: Layout, fx: Int = 1, fz: Int = 0, sx: Int = 0, sz: Int = 1, turnRate: Double = 180.0, runUp: Int = 3, landingLength: Int = 3): List<SimulatedTrajectoryFrame> {
		val (environment, goal) = course(layout, fx, fz, sx, sz, runUp, landingLength)
		val moves = ProbeScenarios.moveLibrary(SimpleMoveOptions(allowNeoJumps = true))
		val start = Stance(-runUp * fx, 64, -runUp * fz)
		val planner = CoarsePlanner(environment, moves, start, goal)
		assertTrue(planner.repair().converged)
		planner.expandSearchField()
		val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
		assertTrue(route.edges.any { it.movement == MovementId.NEO }, "only a neo passes the wall; got ${route.edges.map { "${it.movement} ${it.from}->${it.to}" }}")

		val yaw = Math.toDegrees(kotlin.math.atan2(-fx.toDouble(), fz.toDouble()))
		val outcome = walkHorizon(
			route, planner, resting(Vec3d(start.x + 0.5, 64.0, start.z + 0.5), yaw = yaw), PROFILE, environment,
			MotionConstraints(maxYawDegreesPerFrame = turnRate),
			publish = { _, _ -> },
			started = System.currentTimeMillis(),
		)
		val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
		assertTrue(!path.partial, "the neo must arrive on the far side")
		val terminal = path.plan.frames.last().state.position
		assertTrue(kotlin.math.abs(terminal.y - goal.y) < 1e-6, "the tape must end on the landing floor, ended at $terminal")
		return path.plan.frames
	}

	@Test
	fun `a wall right after the take-off is passed around its side`() {
		neo(Layout(walls = listOf(1), landing = 2, rise = 0))
	}

	@Test
	fun `the neo works in every heading and to either side`() {
		for ((fx, fz) in listOf(-1 to 0, 0 to 1, 0 to -1)) neo(Layout(walls = listOf(1), landing = 2, rise = 0), fx, fz, -fz, fx)
		neo(Layout(walls = listOf(1), landing = 2, rise = 0), 1, 0, 0, -1)
	}

	@Test
	fun `a neo onto a landing one block up`() {
		neo(Layout(walls = listOf(1), landing = 2, rise = 1))
	}

	@Test
	fun `a neo onto a landing one block down`() {
		neo(Layout(walls = listOf(1), landing = 2, rise = -1))
	}

	@Test
	fun `a neo across a gap behind the wall`() {
		neo(Layout(walls = listOf(1), landing = 3, rise = 0))
	}

	@Test
	fun `a neo around a wall two blocks thick`() {
		neo(Layout(walls = listOf(1, 2), landing = 3, rise = 0))
	}

	@Test
	fun `a neo needs a quick turn`() {
		neo(Layout(walls = listOf(1), landing = 2, rise = 0), turnRate = 90.0)
	}

	@Test
	fun `a neo onto a single block lands inside it`() {
		neo(Layout(walls = listOf(1), landing = 2, rise = 0), landingLength = 1)
		neo(Layout(walls = listOf(1), landing = 2, rise = 1), landingLength = 1)
		neo(Layout(walls = listOf(1), landing = 3, rise = -1), landingLength = 1)
	}

	@Test
	fun `a neo keeps the run-up's momentum`() {
		val frames = neo(Layout(walls = listOf(1), landing = 2, rise = 0), runUp = 6, landingLength = 1)
		val enter = frames.indexOfFirst { it.state.position.x >= 0.0 }
		val land = frames.indexOfFirst { it.state.onGround && it.state.position.x >= 2.0 }
		assertTrue(enter >= 0 && land > enter, "the tape must cross the take-off and land")
		assertTrue(frames[enter].state.velocity.horizontalLength() > SPRINTING, "the run-up must arrive sprinting")
		assertTrue(land - enter <= MOMENTUM_NEO_FRAMES, "the neo took ${land - enter} frames from the take-off cell")
		val slowest = frames.subList(enter, land).minOf { it.state.velocity.horizontalLength() }
		assertTrue(slowest > STILL, "the neo stopped on the take-off (slowest %.3f)".format(slowest))
	}

	private companion object {
		const val SPRINTING = 0.12

		const val STILL = 0.05

		const val MOMENTUM_NEO_FRAMES = 17
	}

	private fun diagonal(fx: Int, fz: Int, sx: Int, sz: Int) {
		fun at(forward: Int, side: Int, y: Int) = BlockPos(fx * forward + sx * side, y, fz * forward + sz * side)
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (forward in -3..0) blocks[at(forward, 0, 63)] = SnapshotBlockPhysics.FULL_CUBE
		for ((forward, side) in listOf(1 to 1, 0 to 1, 0 to 2, 1 to 2)) for (y in 64..67) blocks[at(forward, side, y)] = SnapshotBlockPhysics.FULL_CUBE
		for (side in 2..4) blocks[at(2, side, 63)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 40, -16, 16, 90, 16), blocks)
		val goalPos = at(2, 4, 64)
		val goal = Stance(goalPos.x, goalPos.y, goalPos.z)
		val start = Stance(-3 * fx, 64, -3 * fz)
		val planner = CoarsePlanner(environment, ProbeScenarios.moveLibrary(SimpleMoveOptions(allowNeoJumps = true)), start, goal)
		assertTrue(planner.repair().converged)
		planner.expandSearchField()
		val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
		assertTrue(route.edges.any { it.movement == MovementId.NEO }, "only a neo passes the wall; got ${route.edges.map { "${it.movement} ${it.from}->${it.to}" }}")
		val yaw = Math.toDegrees(kotlin.math.atan2(-fx.toDouble(), fz.toDouble()))
		val outcome = walkHorizon(
			route, planner, resting(Vec3d(start.x + 0.5, 64.0, start.z + 0.5), yaw = yaw), PROFILE, environment,
			MotionConstraints(maxYawDegreesPerFrame = 90.0),
			publish = { _, _ -> },
			started = System.currentTimeMillis(),
		)
		val path = checkNotNull((outcome as? PathPlanResult.Planned)?.path) { "no plan: $outcome" }
		assertTrue(!path.partial, "the diagonal neo must arrive on the far side")
	}

	@Test
	fun `a wall on the diagonal is passed around its corner in every direction`() {
		for ((fx, fz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) for ((sx, sz) in listOf(-fz to fx, fz to -fx)) diagonal(fx, fz, sx, sz)
	}

	@Test
	fun `without neo jumps the wall has no way around`() {
		val (environment, goal) = course(Layout(walls = listOf(1), landing = 2, rise = 0), 1, 0, 0, 1)
		val planner = CoarsePlanner(environment, ProbeScenarios.moveLibrary(SimpleMoveOptions()), Stance(-3, 64, 0), goal)
		planner.repair()
		assertNull(planner.routePlan(0L), "the wall blocks every other move")
	}
}
