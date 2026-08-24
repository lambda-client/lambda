/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

/**
 * Names a kind of movement.
 *
 * Open on purpose. This replaced a closed enum, and the enum was the reason adding a
 * movement meant editing the graph, the renderer, the plan dump and the route validator
 * rather than writing one file. Anything that switched on the enum now looks the id up in
 * a map with a default, so an unregistered movement degrades instead of failing to
 * compile.
 */
@JvmInline
value class MovementId(val key: String) {
    override fun toString(): String = key

    companion object {
        val WALK = MovementId("walk")
        val STEP_UP = MovementId("step_up")

        /** A single step down, walked off at whatever speed the body already carries. */
        val WALK_OFF = MovementId("walk_off")

        /** A controlled ledge descent: solved leave speed, feet down. */
        val DROP = MovementId("drop")

        /** A ballistic gap crossing. Permissive at the graph, certified at the tape. */
        val JUMP = MovementId("jump")

        val CLIMB = MovementId("climb")
    }
}
