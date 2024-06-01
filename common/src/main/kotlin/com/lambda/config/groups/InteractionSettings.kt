package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.util.world.raycast.RayCastMask

class InteractionSettings(
    c: Configurable,
    vis: () -> Boolean = { true },
) : InteractionConfig {
    override val reach by c.setting("Reach", 4.9, 0.1..10.0, 0.1, "Players reach / range", " blocks", vis)
    override val useRayCast by c.setting("Raycast", false, "Verify hit vector with ray casting (for very strict ACs)", vis)
    override val resolution by c.setting("Resolution", 5, 1..20, 1, "How many raycast checks per surface (will be squared)") { vis() && useRayCast }
}