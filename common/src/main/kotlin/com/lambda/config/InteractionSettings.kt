package com.lambda.config

import com.lambda.interaction.InteractionConfig
import com.lambda.util.world.raycast.RayCastMask

class InteractionSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : InteractionConfig {
    override val reach by c.setting("Reach", 5.0, 0.1..10.0, 0.1, "Players reach / range", vis)
    override val resolution by c.setting("Resolution", 10, 1..100, 1, "Raycast resolution", vis)
    override val rayCastMask by c.setting("Raycast Mask", RayCastMask.BOTH, "What to raycast against", vis)
}