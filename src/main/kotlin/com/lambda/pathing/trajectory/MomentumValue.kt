/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Makes a stance-indexed cost-to-go answer a question about a *moving* body.
 *
 * The coarse value prices a stationary, grid-aligned body, so two anchors standing on the
 * same block — one sprinting straight at the goal, one stopped, one pointing away — all
 * score identically. They are not equivalent, and a ranking that cannot separate them
 * turns best-first search into breadth-first: the corpus needed roughly 430 expanded
 * anchors to produce a twelve-anchor solution.
 *
 * The correction is applied where the value is *read*, not where it is computed. The D*
 * graph stays indexed by stance and the search state space is unchanged — it was always
 * an exact body state with a velocity. Only the number the frontier sorts by changes.
 *
 * Two terms, and they are not symmetric:
 *
 * - **Credit** for speed already carried toward the goal: ticks a body starting from rest
 *   would need to make up. Measured, not assumed ([MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK]).
 * - **Turn cost** for speed carried the wrong way: a body cannot rotate its velocity
 *   faster than the controller may turn, and every tick spent turning is a tick not spent
 *   closing distance. This is the larger of the two terms in practice, and the one the
 *   stance value is most blind to — an anchor sprinting *away* from the goal currently
 *   looks exactly as good as one sprinting toward it.
 *
 * Only the credit may be applied to an admissible bound, and only as a constant (see
 * [MOMENTUM_CREDIT_MAX_TICKS]): subtracting a constant from a lower bound leaves a lower
 * bound, while adding a turn cost to one would not.
 */

/**
 * Ticks of head start a body carrying [speed] toward its goal has over one at rest.
 *
 * [alignment] is the cosine between the velocity and the direction the value descends in;
 * momentum across or away from that direction earns nothing (its cost is the turn term's
 * business, not this one's).
 */
fun momentumCredit(speed: Double, alignment: Double): Double {
    val aligned = speed * alignment.coerceIn(-1.0, 1.0)
    if (aligned <= 0.0) return 0.0
    return aligned * MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK
}

/**
 * Ticks the body must spend turning before its momentum points where it needs to go.
 *
 * Scaled by how much speed is actually committed the wrong way: turning on the spot is
 * nearly free, turning a full sprint around is not, because the body keeps travelling the
 * wrong way while it comes round.
 */
fun momentumTurnCost(speed: Double, headingErrorDegrees: Double, maxYawDegreesPerFrame: Double): Double {
    if (maxYawDegreesPerFrame <= 0.0) return 0.0
    val ticksToTurn = abs(headingErrorDegrees) / maxYawDegreesPerFrame
    val committed = (speed / SPRINT_TOP_SPEED).coerceIn(0.0, 1.0)
    return ticksToTurn * committed * TURN_COST_WEIGHT
}

/** Cosine between a velocity and a bearing, or 0 when the body carries no momentum. */
fun headingAlignment(velocityX: Double, velocityZ: Double, towardX: Double, towardZ: Double): Double {
    val speed = hypot(velocityX, velocityZ)
    val distance = hypot(towardX, towardZ)
    if (speed <= 1e-9 || distance <= 1e-9) return 0.0
    return (velocityX * towardX + velocityZ * towardZ) / (speed * distance)
}

/**
 * Ticks per block-per-tick of aligned speed, pinned by `MomentumCreditTest` against the
 * exact simulator: a body starting at sprint speed finishes a 30-block run **3 ticks**
 * ahead of one starting from rest, and 3 / 0.2806 is this.
 *
 * Worth stating plainly, because the intuition is badly wrong: it is tempting to reason
 * "sprinting up from rest takes ten-odd ticks, so that is what momentum is worth". It is
 * not. Vanilla's ground friction makes the speed deficit decay geometrically, so the
 * whole head start is the sum of that series — about six tenths of a block, three ticks.
 * The measurement is the authority here; the guess was off by four times.
 */
const val MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK = 10.7

/** Sprint equilibrium on stone; the rate the coarse costs are already measured at. */
const val SPRINT_TOP_SPEED = 0.2806

/** The most any body can be credited: a full sprint, perfectly aligned. */
const val MOMENTUM_CREDIT_MAX_TICKS = SPRINT_TOP_SPEED * MOMENTUM_CREDIT_TICKS_PER_BLOCK_PER_TICK

/**
 * How much of the nominal turning time to charge. Below one because the body keeps
 * closing some distance while it turns, and because the controller can carry a heading
 * error through a wide corner without ever stopping.
 */
private const val TURN_COST_WEIGHT = 0.6
