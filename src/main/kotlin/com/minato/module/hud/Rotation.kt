
package com.minato.module.hud

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.blocks.FormatterSettings
import com.minato.config.withEdits
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.FormattingUtils.format

@Suppress("unused")
object Rotation : HudModule(
	name = "Rotation",
	description = "Show your rotation",
	tag = ModuleTag.HUD,
) {
	private val formatter by configBlock(FormatterSettings(this))
		.withEdits {
			::timeFormat.editSetting { hide() }
		}

	override fun ImGuiBuilder.buildLayout() {
		runSafe {
			val rotation = player.rotationClient.format(formatter)
			textCopyable(rotation)
		}
	}
}
