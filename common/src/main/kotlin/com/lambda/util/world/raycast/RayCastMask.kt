package com.lambda.util.world.raycast

enum class RayCastMask(val block: Boolean, val entity: Boolean) {
    BOTH(true, true),
    BLOCK(true, false),
    ENTITY(false, true)
}