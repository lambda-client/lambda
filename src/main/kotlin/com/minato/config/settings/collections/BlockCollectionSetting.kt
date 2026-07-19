
package com.minato.config.settings.collections

import com.minato.Minato.typeFactory
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.serializers.BlockSerializer
import com.minato.gui.dsl.ImGuiBuilder
import net.minecraft.block.Block

class BlockCollectionSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CollectionSetting<Block>, MutableCollection<Block>>,
	visibility: () -> Boolean,
	immutableCollection: Collection<Block>,
	defaultValue: MutableCollection<Block>,
) : CollectionSetting<Block>(
	name,
	description,
	config,
	layer,
	visibility,
	defaultValue,
	immutableCollection,
	typeFactory.constructCollectionType(MutableCollection::class.java, Block::class.java),
	serialize = true,
) {
	override fun ImGuiBuilder.buildLayout() = buildDualPane("block") { BlockSerializer.stringify(it) }
}