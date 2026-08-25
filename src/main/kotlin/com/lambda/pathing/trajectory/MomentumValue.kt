package com.lambda.pathing.trajectory

import kotlin.math.abs
import kotlin.math.hypot

fun momentumCredit(speed: Double, alignment: Double): Double {
    val aligned = speed * alignment.coerceIn(-1.0, 1.0)
    if (aligned <= 0.0) return 0.0
    return aligned * MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK
}

fun momentumTurnCost(speed: Double, headingErrorDegrees: Double, maxYawDegreesPerFrame: Double): Double {
    if (maxYawDegreesPerFrame <= 0.0) return 0.0
    val ticksToTurn = abs(headingErrorDegrees) / maxYawDegreesPerFrame
    val committed = (speed / SPRINT_TOP_SPEED).coerceIn(0.0, 1.0)
    return ticksToTurn * committed * TURN_COST_WEIGHT
}

fun headingAlignment(velocityX: Double, velocityZ: Double, towardX: Double, towardZ: Double): Double {
    val speed = hypot(velocityX, velocityZ)
    val distance = hypot(towardX, towardZ)
    if (speed <= 1e-9 || distance <= 1e-9) return 0.0
    return (velocityX * towardX + velocityZ * towardZ) / (speed * distance)
}

const val MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK = 10.7

const val SPRINT_TOP_SPEED = 0.2806

const val MOMENTUM_CREDIT_MAX_TICKS = SPRINT_TOP_SPEED * MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK

private const val TURN_COST_WEIGHT = 0.6
