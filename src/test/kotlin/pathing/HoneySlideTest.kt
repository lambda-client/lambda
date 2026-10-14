/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package pathing

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.physics.besideHoney
import com.lambda.pathing.world.snapshot.BlockPhysicsCapture
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes
import pathing.ProbeScenarios.PROFILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Vanilla HoneyBlock.onEntityCollision: a body falling beside a honey block (feet below its 15/16 top, falling
 * faster than the 0.08 onset, centre beyond 7/16 + half a width from the block's centre) has its fall set to the
 * slide speed, and a fast first contact scales the horizontal velocity by 0.05 over the fall.
 */
class HoneySlideTest {
	private val honey = run {
		val shape = VoxelShapes.cuboid(1 / 16.0, 0.0, 1 / 16.0, 15 / 16.0, 15 / 16.0, 15 / 16.0)
		SnapshotBlockPhysics(
			collisionShape = shape,
			velocityMultiplier = 0.4,
			jumpVelocityMultiplier = 0.5,
			coarseVoxel = BlockPhysicsCapture.coarseVoxelOf(shape, velocityMultiplier = 0.4),
			honey = true,
		)
	}

	private fun wall(): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (y in 5..15) blocks[BlockPos(1, y, 0)] = honey
		return SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-8, 0, -8, 8, 30, 8), blocks)
	}

	private fun falling(x: Double, y: Double, velocity: Vec3d): MovementSimulationState =
		resting(Vec3d(x, y, 0.5)).copy(velocity = velocity, onGround = false, verticalCollision = false, supportingBlockPos = null)

	@Test
	fun `a fast fall pressed against honey brakes to the slide speed`() {
		val face = 1 + 1 / 16.0 - PROFILE.width.toFloat() / 2.0F
		val environment = wall()
		val simulator = MovementSimulator(PROFILE, environment, falling(face, 12.0, Vec3d(0.1, -0.3, 0.05)))
		val state = simulator.tickMovement(MovementSimulationInput(rotation = Rotation(-90.0, 0.0)))

		val fall = (-0.3 - 0.08) * 0.98F
		val preGravity = fall / 0.98F + 0.08
		val brake = -0.05 / preGravity
		assertEquals((-0.05 - 0.08) * 0.98F, state.velocity.y, 1e-12, "the fall is set to the slide speed")
		assertEquals(0.05 * 0.91F * brake, state.velocity.z, 1e-9, "the fast first contact scales the horizontal velocity")
		assertTrue(environment.besideHoney(state), "the rollout judge sees the slide")

		val next = simulator.tickMovement(MovementSimulationInput(rotation = Rotation(-90.0, 0.0)))
		assertEquals((-0.05 - 0.08) * 0.98F, next.velocity.y, 1e-12, "the slide holds its speed")
		assertEquals(state.velocity.z * 0.91F, next.velocity.z, 1e-9, "a slow slide keeps the horizontal velocity")
	}

	@Test
	fun `a body clear of the honey falls freely`() {
		val environment = wall()
		val start = falling(0.2, 12.0, Vec3d(0.0, -0.3, 0.0))
		val state = MovementSimulator(PROFILE, environment, start).tickMovement(MovementSimulationInput())
		assertEquals((-0.3 - 0.08) * 0.98F, state.velocity.y, 1e-12)
		assertFalse(environment.besideHoney(state))
	}

	@Test
	fun `a honey wall carries a jump past ordinary reach`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		for (x in 0..2) blocks[BlockPos(x, 9, 0)] = SnapshotBlockPhysics.FULL_CUBE
		for (x in -6..-1) for (y in 8..11) blocks[BlockPos(x, y, 1)] = honey
		blocks[BlockPos(-7, 7, 0)] = SnapshotBlockPhysics.FULL_CUBE
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-16, 0, -16, 16, 30, 16), blocks)
		val moves = ProbeScenarios.moveLibrary(com.lambda.pathing.actions.SimpleMoveOptions())
		val planner = com.lambda.pathing.coarse.CoarsePlanner(environment, moves, com.lambda.pathing.core.Stance(2, 10, 0), com.lambda.pathing.core.Stance(-7, 8, 0))
		assertTrue(planner.repair().converged)
		planner.expandSearchField()
		val route = checkNotNull(planner.routePlan(0L)) { planner.routeFailureReport() }
		assertTrue(route.edges.any { it.movement == com.lambda.pathing.core.MovementId.HONEY_SLIDE }, "only a honey slide reaches the landing; got ${route.edges.map { it.movement }}")

		val outcome = com.lambda.pathing.session.walkHorizon(
			route, planner, resting(Vec3d(2.5, 10.0, 0.5), yaw = 90.0), PROFILE, environment, com.lambda.pathing.actions.MotionConstraints(),
			publish = { _, _ -> },
			started = System.currentTimeMillis(),
		)
		val path = checkNotNull((outcome as? com.lambda.pathing.session.PathPlanResult.Planned)?.path) { "no plan: $outcome" }
		assertTrue(!path.partial, "the slide must arrive on the landing block")
		val terminal = path.plan.frames.last().state.position
		assertTrue(terminal.y == 8.0 && terminal.x < -6.0, "the tape must end on the landing block, ended at $terminal")
	}

	@Test
	fun `a jump off honey reaches half the height`() {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		blocks[BlockPos(0, 9, 0)] = honey
		val environment = SnapshotSimulationEnvironment.synthetic(SimulationSnapshotBounds(-8, 0, -8, 8, 30, 8), blocks)
		val start = resting(Vec3d(0.5, 9 + 15 / 16.0, 0.5))
		val state = MovementSimulator(PROFILE, environment, start).tickMovement(MovementSimulationInput(jump = true))
		assertEquals(PROFILE.jumpStrength * 0.5, state.position.y - start.position.y, 1e-6, "honey halves the jump velocity")
	}
}
