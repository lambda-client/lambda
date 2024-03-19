package com.lambda.module.modules.player

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object Interact : Module(
    name = "Interact",
    description = "Modify players interaction with the world",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    // ToDo: Is this fast place / fast use? Should it be relocated with more options?
    @JvmStatic val placeDelay by setting("Item Use / Place Delay", 4, 0..20, 1, "Sets the delay between placing blocks or using items")
//    @JvmStatic val breakDelay by setting("Attack / Break Delay", 10, 0..20, 1)
    @JvmStatic val multiAction by setting("Multi Action", false, "Allows to use many items while breaking blocks")
}
