package com.lambda.module.modules.movement

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object TridentBoost : Module(
    name = "TridentBoost",
    description = "Boosts you with tridents",
    defaultTags = setOf(ModuleTag.MOVEMENT)
) {
    val tridentSpeed by setting("Speed Factor", 2.0, 0.1..3.0, 0.1, description = "Speed factor of the trident boost")
    val forceUse by setting("Force Use", true, description = "Try to use the trident outside of water or rain")
}
