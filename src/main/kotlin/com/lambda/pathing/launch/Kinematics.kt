/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.pathing.launch

/**
 * Vanilla player-movement constants, in one place. Every planner-side model of the body
 * (ballistics, horizontal dynamics, coarse costs, arc probes) reads these; the simulator
 * itself takes the live values from [com.lambda.pathing.physics.PlayerPhysicsProfile].
 */
object Kinematics {
	/** Vertical acceleration per tick for a player without attribute modifiers. */
	const val GRAVITY = 0.08

	/** Vertical velocity retained per air tick, applied after gravity. */
	const val VERTICAL_DRAG = 0.98

	/** Horizontal velocity retained per air tick; ground friction is this times slipperiness. */
	const val HORIZONTAL_DRAG = 0.91

	/** Movement-input damping vanilla applies before turning input into velocity. */
	const val INPUT_DAMPING = 0.98

	/** Slipperiness of ordinary ground; ice and slime differ. */
	const val DEFAULT_SLIPPERINESS = 0.6

	/** Ground friction on ordinary ground: [HORIZONTAL_DRAG] × [DEFAULT_SLIPPERINESS]. */
	const val DEFAULT_GROUND_FRICTION = HORIZONTAL_DRAG * DEFAULT_SLIPPERINESS

	const val DEFAULT_MOVEMENT_SPEED = 0.1

	/** Vertical launch velocity of an unmodified jump. */
	const val JUMP_VELOCITY = 0.42

	/** Horizontal boost a sprint jump adds along the facing direction. */
	const val SPRINT_JUMP_BOOST = 0.2

	const val BODY_HALF_WIDTH = 0.3
	const val BODY_HEIGHT = 1.8

	/** One air tick of vertical motion: gravity, then drag. */
	fun fallStep(verticalVelocity: Double): Double = (verticalVelocity + GRAVITY) * VERTICAL_DRAG
}
