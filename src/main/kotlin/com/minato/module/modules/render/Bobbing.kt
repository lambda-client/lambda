
package com.minato.module.modules.render

import com.minato.module.Module
import com.minato.module.tag.ModuleTag

object Bobbing : Module(
	name = "Bobbing",
	description = "Modifies vanilla view bobbing when the player walks or runs",
	tag = ModuleTag.RENDER
) {
	val magnitude by setting("Magnitude", 1.0, 0.0..2.0, 0.01)
	val speed by setting("Speed", 1.0, 0.0..2.0, 0.01)
}