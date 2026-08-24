/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

data class MovementKeys(val forward: Double, val strafe: Double) {
    val sustainsSprint: Boolean get() = forward > 0.0

    companion object {
        val FORWARD = MovementKeys(1.0, 0.0)
        val FORWARD_LEFT = MovementKeys(1.0, -1.0)
        val FORWARD_RIGHT = MovementKeys(1.0, 1.0)
        val LEFT = MovementKeys(0.0, -1.0)
        val RIGHT = MovementKeys(0.0, 1.0)
        val NEUTRAL = MovementKeys(0.0, 0.0)

        val SPRINTABLE = listOf(FORWARD, FORWARD_LEFT, FORWARD_RIGHT)
    }
}
