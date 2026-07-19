
package com.minato.config.settings.collections

import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import tools.jackson.databind.JavaType

class MapSetting<K, V>(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<MapSetting<K, V>, MutableMap<K, V>>,
	visibility: () -> Boolean,
	defaultValue: MutableMap<K, V>,
	val type: JavaType
) : Setting<MutableMap<K, V>>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {}
}