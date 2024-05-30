package com.lambda.interaction

import com.lambda.util.world.raycast.RayCastMask

interface InteractionConfig {
    /**
     * Maximum distance to interact.
     */
    val reach: Double

    /**
     * Will check `resolution squared` many points on a grid on each visible surface of the hit box.
     */
    val resolution: Int

    val rayCastMask: RayCastMask

    val ignoreRayCast: Boolean
}