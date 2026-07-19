
package com.minato.config.automation

import com.minato.config.EntryCore
import com.minato.config.EntryLayer
import com.minato.config.blocks.BreakConfig
import com.minato.config.blocks.BuildConfig
import com.minato.config.blocks.EatConfig
import com.minato.config.blocks.HotbarConfig
import com.minato.config.blocks.InteractConfig
import com.minato.config.blocks.InventoryConfig
import com.minato.config.blocks.RotationConfig
import com.minato.config.entries.Setting
import com.minato.context.Automated

interface IMutableAutomationConfig : Automated {
    var defaultAutomationConfig: AutomationConfig
    var backingAutomationConfig: AutomationConfig
	var automationConfig: AutomationConfig

	override val buildConfig: BuildConfig get() = automationConfig.buildConfig
	override val breakConfig: BreakConfig get() = automationConfig.breakConfig
	override val interactConfig: InteractConfig get() = automationConfig.interactConfig
	override val rotationConfig: RotationConfig get() = automationConfig.rotationConfig
	override val inventoryConfig: InventoryConfig get() = automationConfig.inventoryConfig
	override val hotbarConfig: HotbarConfig get() = automationConfig.hotbarConfig
	override val eatConfig: EatConfig get() = automationConfig.eatConfig
}

class MutableAutomationConfig : IMutableAutomationConfig {
	override var defaultAutomationConfig: AutomationConfig = AutomationConfig.DEFAULT
		set(value) {
			field = value
			automationConfig = value
		}
	override var backingAutomationConfig: AutomationConfig = defaultAutomationConfig
	override var automationConfig: AutomationConfig = defaultAutomationConfig
		set(value) {
			if (value === defaultAutomationConfig) {
				if (backingAutomationConfig !== defaultAutomationConfig) {
					field.settingLayers.forEachEntry { _, single -> single.entry.restoreOriginalCore() }
				}
				field = value
			} else {
				field.settingLayers.forEachEntry { path, single ->
					var otherLayer: EntryLayer.Multiple<Setting<*>> = value.settingLayers
					path.forEach { layer ->
						val subLayer = otherLayer.layers
							.asSequence()
							.filterIsInstance<EntryLayer.Multiple<Setting<*>>>()
							.find { it.name == layer.name } ?: return@forEachEntry
						otherLayer = subLayer
					}
					val otherSetting = otherLayer.layers
						.asSequence()
						.filterIsInstance<EntryLayer.Single<Setting<*>>>()
						.find { it.name == single.name }
						?.entry ?: return@forEachEntry
					if (single.entry.core::class != otherSetting.core::class)
						throw IllegalStateException("Settings with the same name do not have the same type.")
					@Suppress("UNCHECKED_CAST")
					(single.entry as Setting<Any>).core = otherSetting.core as EntryCore<Any>
				}
			}
			backingAutomationConfig = value
		}
}