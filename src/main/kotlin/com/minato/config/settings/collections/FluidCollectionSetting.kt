
package com.minato.config.settings.collections

import com.minato.Minato.typeFactory
import com.minato.config.Config
import com.minato.config.entries.SettingEntryLayer
import com.minato.config.serializers.FluidSerializer
import com.minato.gui.dsl.ImGuiBuilder
import net.minecraft.fluid.Fluid

class FluidCollectionSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CollectionSetting<Fluid>, MutableCollection<Fluid>>,
	visibility: () -> Boolean,
	immutableCollection: Collection<Fluid>,
	defaultValue: MutableCollection<Fluid>,
) : CollectionSetting<Fluid>(
	name,
	description,
	config,
	layer,
	visibility,
	defaultValue,
	immutableCollection,
	typeFactory.constructCollectionType(MutableCollection::class.java, Fluid::class.java),
	serialize = true,
) {
	override fun ImGuiBuilder.buildLayout() = buildDualPane("block") { FluidSerializer.stringify(it) }
}