package com.lambda.pathing.search

import com.lambda.pathing.actions.CoarseMoveRates

internal fun momentumCredit(speed: Double, alignment: Double): Double {
	val aligned = speed * alignment.coerceIn(-1.0, 1.0)
	if (aligned <= 0.0) return 0.0
	return aligned * MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK
}

internal fun momentumTurnCost(speed: Double, headingErrorDegrees: Double, maxYawDegreesPerFrame: Double): Double {
	if (maxYawDegreesPerFrame <= 0.0) return 0.0
	val ticksToTurn = headingErrorDegrees / maxYawDegreesPerFrame
	val committed = (speed / SPRINT_TOP_SPEED).coerceIn(0.0, 1.0)
	return ticksToTurn * committed * TURN_COST_WEIGHT
}

private const val MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK = 10.7

private val SPRINT_TOP_SPEED = CoarseMoveRates.SPRINT_BLOCKS_PER_TICK

internal val MOMENTUM_CREDIT_MAX_TICKS = SPRINT_TOP_SPEED * MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK

private const val TURN_COST_WEIGHT = 0.6
