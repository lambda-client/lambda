/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

/**
 * One combination of movement keys, exactly as a keyboard can hold them.
 *
 * Values are only ever -1, 0 or 1: a real player presses a key or does not, and a tape
 * carrying analog input would be both unlike a human and rejected by servers that check.
 * Speed is modulated by *tapping* across ticks, never by a fraction of a key.
 *
 * The reason this type exists at all is that every controller here has, until now, held
 * `forward = 1` and `strafe = 0` for its whole life — a one-dimensional slice of the two
 * dimensions the simulator and the executor both already support. Vanilla applies input
 * in the *facing* frame and normalises diagonals, so a held strafe changes the direction
 * of travel by 45 degrees **within a single tick and without rotating**, where a yaw turn
 * is rate-limited. That is the manoeuvre the search has never been able to propose.
 */
data class MovementKeys(val forward: Double, val strafe: Double) {
    /** Vanilla sustains a sprint only while forward is held; strafing alone drops it. */
    val sustainsSprint: Boolean get() = forward > 0.0

    companion object {
        val FORWARD = MovementKeys(1.0, 0.0)
        val FORWARD_LEFT = MovementKeys(1.0, -1.0)
        val FORWARD_RIGHT = MovementKeys(1.0, 1.0)
        val LEFT = MovementKeys(0.0, -1.0)
        val RIGHT = MovementKeys(0.0, 1.0)
        val NEUTRAL = MovementKeys(0.0, 0.0)

        /** The combinations that keep a sprint alive, straight first. */
        val SPRINTABLE = listOf(FORWARD, FORWARD_LEFT, FORWARD_RIGHT)
    }
}
