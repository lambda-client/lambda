
package com.minato.module.modules.player

import com.minato.module.Module
import com.minato.module.tag.ModuleTag

object Interact : Module(
    name = "Interact",
    description = "Modify players interaction with the world",
    tag = ModuleTag.PLAYER,
) {
    @JvmStatic val placeDelay by setting("Item Use / Place Delay", 4, 0..20, 1, "Sets the delay between placing blocks or using items")
    @JvmStatic val multiAction by setting("Multi Action", false, "Allows to use many items while breaking blocks")
}
