package com.lambda.config.groups

import com.lambda.core.PingManager

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
    val swingHand: Boolean
    val inScopeThreshold: Int
    val pingTimeout: Boolean

    val scopeThreshold: Int get() = if (pingTimeout) {
        (PingManager.lastPing / 50L).toInt()
    } else {
        inScopeThreshold
    }
}