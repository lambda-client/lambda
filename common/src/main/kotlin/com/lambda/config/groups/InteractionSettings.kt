package com.lambda.config.groups

import com.lambda.config.Configurable
import com.lambda.config.groups.BuildSettings.Page
import com.lambda.core.PingManager
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.world.raycast.RayCastMask

class InteractionSettings(
    c: Configurable,
    defaultReach: Double = 4.6,
    vis: () -> Boolean = { true },
) : InteractionConfig {
    override val reach by c.setting("Reach", defaultReach, 0.1..10.0, 0.1, "Players reach / range", " blocks", vis)
    override val useRayCast by c.setting("Raycast", true, "Verify hit vector with ray casting (for very strict ACs)", vis)
    override val resolution by c.setting("Resolution", 4, 1..40, 1, "How many raycast checks per surface (will be squared)") { vis() && useRayCast }
    override val swingHand by c.setting("Swing Hand", true, "Swing hand on interactions", vis)
    override val pingTimeout by c.setting("Ping Timeout", false, "Timeout on high ping", vis)
    override val inScopeThreshold by c.setting("Constant Timeout", 1, 0..20, 1, "How many ticks to wait after target box is in rotation scope"," ticks") {
        vis() && !pingTimeout
    }
}