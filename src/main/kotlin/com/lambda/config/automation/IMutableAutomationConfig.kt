/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.config.automation

import com.lambda.config.EntryCore
import com.lambda.config.EntryLayer
import com.lambda.config.blocks.BreakConfig
import com.lambda.config.blocks.BuildConfig
import com.lambda.config.blocks.EatConfig
import com.lambda.config.blocks.HotbarConfig
import com.lambda.config.blocks.InteractConfig
import com.lambda.config.blocks.InventoryConfig
import com.lambda.config.blocks.MovementConfig
import com.lambda.config.blocks.PathfinderRenderConfig
import com.lambda.config.blocks.PathRefinementConfig
import com.lambda.config.blocks.PlannerConfig
import com.lambda.config.blocks.RotationConfig
import com.lambda.config.entries.Setting
import com.lambda.context.Automated

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
	override val plannerConfig: PlannerConfig get() = automationConfig.plannerConfig
	override val refinementConfig: PathRefinementConfig get() = automationConfig.refinementConfig
	override val movementConfig: MovementConfig get() = automationConfig.movementConfig
	override val renderConfig: PathfinderRenderConfig get() = automationConfig.renderConfig
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