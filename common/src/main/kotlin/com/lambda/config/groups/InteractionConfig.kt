package com.lambda.config.groups

interface InteractionConfig {
    /**
     * Maximum distance to interact.
     */
    val reach: Double

    /**
     * Will check `resolution squared` many points on a grid on each visible surface of the hit box.
     */
    val resolution: Int

    val useRayCast: Boolean
}