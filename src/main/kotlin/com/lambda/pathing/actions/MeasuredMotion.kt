/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.actions

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.physics.MovementSimulationInput
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.MovementSimulator
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.physics.WaterPhysics
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.snapshot.SimulationSnapshotBounds
import com.lambda.pathing.world.snapshot.SnapshotBlockPhysics
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

internal object MeasuredMotion {
	private val profile = PlayerPhysicsProfile.VANILLA

	private val east = Rotation(EAST_YAW, 0.0)

	private val sprint = MovementSimulationInput(forward = 1.0, sprint = true, rotation = east)

	private val lane by lazy {
		world(LANE_LENGTH + 8) { put ->
			for (x in -4..LANE_LENGTH) for (z in -LANE_HALF_WIDTH..LANE_HALF_WIDTH) put(BlockPos(x, FLOOR_Y, z), SnapshotBlockPhysics.FULL_CUBE)
		}
	}

	private val sprinting by lazy { run(lane, rest(0.5), SPRINT_TICKS) { _, _ -> sprint } }

	/** The stored horizontal velocity of a sprint at cruise: what the search reads as an anchor's speed. */
	val cruiseVelocity: Double by lazy { sprinting[SPRINT_TICKS - 1].velocity.horizontalLength() }

	val startupLossTicks: Double by lazy {
		val cruise = sprinting[SPRINT_TICKS - 1].position.x - sprinting[SPRINT_TICKS - 2].position.x
		val reached = sprinting.indexOfFirst { it.position.x >= 0.5 + STARTUP_DISTANCE } + 1
		reached - STARTUP_DISTANCE / cruise
	}

	/** Ticks a sprint loses letting go until it stands still, net of the ground it slides meanwhile. */
	val stopTicks: Double by lazy {
		val cruising = sprinting[SPRINT_TICKS / 2]
		val cruise = cruising.position.x - sprinting[SPRINT_TICKS / 2 - 1].position.x
		val stopping = run(lane, cruising, SPRINT_TICKS, until = { it.velocity.horizontalLength() == 0.0 }) { _, _ -> MovementSimulationInput(rotation = east) }
		stopping.size - (stopping.last().position.x - cruising.position.x) / cruise
	}

	val sprintJumpBlocksPerTick: Double by lazy {
		val hopping = run(lane, rest(0.5), SPRINT_TICKS) { _, state -> sprint.copy(jump = state.onGround) }
		(hopping[SPRINT_TICKS - 1].position.x - hopping[SPRINT_TICKS / 2 - 1].position.x) / (SPRINT_TICKS / 2)
	}

	val swimBlocksPerTick: Double by lazy {
		val swimming = run(pool(LANE_LENGTH, rise = null), floating(0.5), SWIM_TICKS, input = ::paddle)
		(swimming[SWIM_TICKS - 1].position.x - swimming[SWIM_TICKS / 2 - 1].position.x) / (SWIM_TICKS / 2)
	}

	private val waterExits by lazy { DoubleArray(MAX_EXIT_RISE + 1) { rise -> waterExit(rise) } }

	fun waterExitTicks(rise: Int): Double = waterExits[rise]

	private fun waterExit(rise: Int): Double {
		val bank = EXIT_POOL_LENGTH.toDouble()
		val exits = (0 until EXIT_PHASES).map { phase ->
			val start = floating(0.5 + EXIT_PHASE_BLOCKS * phase / EXIT_PHASES)
			val out = { state: MovementSimulationState -> state.onGround && !state.touchingWater && state.position.x >= bank + 0.5 }
			val states = run(pool(EXIT_POOL_LENGTH, rise), start, EXIT_TICKS, out, ::paddle)
			val from = crossing(states, bank - 0.5)
			val landed = states.indexOfFirst(out)
			check(landed >= 0) { "the paddle never left the pool over a rise of $rise" }
			val walkedOut = states.indexOfFirst { it.onGround && !it.touchingWater && it.position.x >= bank }
			(if (walkedOut < landed) crossing(states, bank + 0.5) else landed + 1.0) - from
		}
		return exits.average()
	}

	private fun crossing(states: List<MovementSimulationState>, x: Double): Double {
		val index = states.indexOfFirst { it.position.x >= x }
		check(index > 0) { "the body never crossed $x" }
		val before = states[index - 1].position.x
		return index + (x - before) / (states[index].position.x - before)
	}

	private fun paddle(tick: Int, state: MovementSimulationState) =
		MovementSimulationInput(forward = 1.0, jump = state.touchingWater, sprint = !state.eyeInWater, rotation = east)

	private fun pool(length: Int, rise: Int?) = world(length + EXIT_RUN_OUT) { put ->
		for (x in -4..length + EXIT_RUN_OUT) for (z in -LANE_HALF_WIDTH..LANE_HALF_WIDTH) {
			put(BlockPos(x, FLOOR_Y, z), SnapshotBlockPhysics.FULL_CUBE)
			val top = if (x < length || rise == null) SURFACE_Y else SURFACE_Y + rise - 1
			for (y in FLOOR_Y + 1..top) put(BlockPos(x, y, z), if (x < length || rise == null) water(y == SURFACE_Y) else SnapshotBlockPhysics.FULL_CUBE)
		}
	}

	val sinkTicks: Double by lazy { sinking.first }

	val sinkDescent: Double by lazy { sinking.second }

	private val sinking: Pair<Double, Double> by lazy {
		val cellY = HANGING_Y
		val hanging = world(4) { put ->
			put(BlockPos(0, cellY, 0), water(true))
			put(BlockPos(0, FLOOR_Y, 0), SnapshotBlockPhysics.FULL_CUBE)
		}
		val start = MovementSimulationState.synthetic(profile, Vec3d(0.5, cellY.toDouble(), 0.5), east, Vec3d.ZERO, onGround = false)
		val states = run(hanging, start, SINK_LIMIT_TICKS) { _, _ -> MovementSimulationInput(sneak = true, rotation = east) }
		val out = states.indexOfFirst { !it.touchingWater }
		(out + 1).toDouble() to cellY - states[out].position.y
	}

	private fun rest(x: Double) = MovementSimulationState.synthetic(profile, Vec3d(x, FLOOR_Y + 1.0, 0.5), east, Vec3d(0.0, -Vanilla.GRAVITY * Vanilla.VERTICAL_DRAG, 0.0), onGround = true)

	private fun floating(x: Double) = MovementSimulationState.synthetic(profile, Vec3d(x, SURFACE_Y + FLOAT_HEIGHT, 0.5), east, Vec3d.ZERO, onGround = false)

	private fun water(surface: Boolean) = SnapshotBlockPhysics(
		VoxelShapes.empty(), coarseVoxel = CoarseVoxel.WATER,
		water = WaterPhysics(if (surface) Vanilla.STILL_WATER_HEIGHT else Vanilla.FULL_WATER_HEIGHT, Vec3d.ZERO),
	)

	private fun world(span: Int, fill: ((BlockPos, SnapshotBlockPhysics) -> Unit) -> Unit): SnapshotSimulationEnvironment {
		val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()
		fill { pos, physics -> blocks[pos] = physics }
		return SnapshotSimulationEnvironment.synthetic(
			SimulationSnapshotBounds(-span - 8, FLOOR_Y - 8, -span - 8, span + 8, HANGING_Y + 16, span + 8), blocks,
		)
	}

	private fun run(
		environment: SnapshotSimulationEnvironment,
		start: MovementSimulationState,
		ticks: Int,
		until: (MovementSimulationState) -> Boolean = { false },
		input: (Int, MovementSimulationState) -> MovementSimulationInput,
	): List<MovementSimulationState> {
		val simulator = MovementSimulator(profile = profile, environment = environment, initialState = start)
		val states = ArrayList<MovementSimulationState>(ticks)
		var state = start
		for (tick in 0 until ticks) {
			state = simulator.tickMovement(input(tick, state))
			states += state
			if (until(state)) break
		}
		return states
	}

	private const val EAST_YAW = -90.0
	private const val FLOOR_Y = 60
	private const val SURFACE_Y = 63
	private const val FLOAT_HEIGHT = 0.4
	private const val LANE_LENGTH = 400
	private const val LANE_HALF_WIDTH = 2
	private const val SPRINT_TICKS = 600
	private const val STARTUP_DISTANCE = 100.0
	private const val SWIM_TICKS = 400
	private const val HANGING_Y = 80
	private const val MAX_EXIT_RISE = 1
	private const val EXIT_POOL_LENGTH = 12
	private const val EXIT_RUN_OUT = 40
	private const val EXIT_PHASES = 40
	private const val EXIT_PHASE_BLOCKS = 4.0
	private const val EXIT_TICKS = 300
	private const val SINK_LIMIT_TICKS = 200
}
