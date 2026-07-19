
package com.minato.config.settings.collections

import com.minato.Minato.typeFactory
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.serializers.ItemSerializer
import com.minato.gui.dsl.ImGuiBuilder
import net.minecraft.item.Item

class ItemCollectionSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CollectionSetting<Item>, MutableCollection<Item>>,
	visibility: () -> Boolean,
	immutableCollection: Collection<Item>,
	defaultValue: MutableCollection<Item>
) : CollectionSetting<Item>(
	name, description, config, layer, visibility,
	defaultValue,
	immutableCollection,
	typeFactory.constructCollectionType(Collection::class.java, Item::class.java),
	serialize = true,
) {
	override fun ImGuiBuilder.buildLayout() = buildDualPane("item") { ItemSerializer.stringify(it) }
}