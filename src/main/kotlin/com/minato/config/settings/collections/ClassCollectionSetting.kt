
package com.minato.config.settings.collections

import com.minato.Minato.typeFactory
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.ReflectionUtils.className

/**
 * @see [CollectionSetting]
 * @see [com.minato.config.Config]
 */
class ClassCollectionSetting<T : Any>(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CollectionSetting<T>, MutableCollection<T>>,
	visibility: () -> Boolean,
	immutableCollection: Collection<T>,
	defaultValue: MutableCollection<T>
) : CollectionSetting<T>(
	name,
	description,
	config,
	layer,
	visibility,
	defaultValue,
	immutableCollection,
	typeFactory.constructCollectionType(Collection::class.java, Any::class.java),
	serialize = false,
) {
	override fun ImGuiBuilder.buildLayout() = buildDualPane("item") { it.className }
}