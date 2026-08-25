package com.lambda.pathing.core

@JvmInline
value class MovementId(val key: String) {
    override fun toString(): String = key

    companion object {
        val WALK = MovementId("walk")
        val STEP_UP = MovementId("step_up")

        val WALK_OFF = MovementId("walk_off")

        val DROP = MovementId("drop")

        val JUMP = MovementId("jump")

        val CLIMB = MovementId("climb")

        val BOUNCE = MovementId("bounce")
    }
}
