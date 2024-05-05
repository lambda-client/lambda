package com.lambda.module.modules.movement

import com.lambda.event.events.MovementEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object SafeWalk : Module(
    name = "SafeWalk",
    description = "Keeps you at the edge",
    tag = ModuleTag.MOVEMENT
) {
    private val realisticCollision by setting("Collide", true, "Realistic collision on the edge")

    init {
//        listener<MovementEvent.Post> {
//            if (realisticCollision) player.velocity = Vec3d.ZERO
//        }

        listener<MovementEvent.ClipAtLedge> {
            it.clip = true
        }
    }
}