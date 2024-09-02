package com.lambda.config.groups

import com.lambda.config.Configurable

class InteractionSettings(
    c: Configurable,
    defaultReach: Double = 4.9,
    vis: () -> Boolean = { true },
) : InteractionConfig {
    override val reach by c.setting("Reach", defaultReach, 0.1..10.0, 0.1, "Players reach / range", " blocks", vis)
    override val useRayCast by c.setting("Raycast", true, "Verify hit vector with ray casting (for very strict ACs)", vis)
    override val resolution by c.setting("Resolution", 20, 1..40, 1, "How many raycast checks per surface (will be squared)") { vis() && useRayCast }
    override val swingHand by c.setting("Swing Hand", true, "Swing hand on interactions", vis)
}