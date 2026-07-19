
package com.minato.module.modules.player

import com.minato.module.Module
import com.minato.module.tag.ModuleTag

object Reach : Module(
	name = "Reach",
	description = "Changes the reach distance of the player",
	tag = ModuleTag.PLAYER
) {
	@JvmStatic val blockReach by setting("Block Reach", 4.5, 0.0..10.0, 0.01)
	@JvmStatic val entityReach by setting("Entity Reach", 3.0, 0.0..10.0, 0.01)
}