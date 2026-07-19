
package com.minato.config.settings

import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder

class FunctionSetting<T : () -> R, R>(
	name: String,
	description: String,
	defaultValue: T,
	config: Config,
	layer: SettingEntryLayer<FunctionSetting<T, R>, T>,
	visibility: () -> Boolean
) : Setting<T>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {
        button(name) { value() }
        minatoTooltip(description)
    }
}
