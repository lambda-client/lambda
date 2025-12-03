/*
 * Copyright 2025 Lambda
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

package com.lambda.config

import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.EatConfig
import com.lambda.context.Automated
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.interaction.request.hotbar.HotbarConfig
import com.lambda.interaction.request.interacting.InteractConfig
import com.lambda.interaction.request.inventory.InventoryConfig
import com.lambda.interaction.request.placing.PlaceConfig
import com.lambda.interaction.request.rotating.RotationConfig

interface MutableAutomationConfig : Automated {
    var defaultAutomationConfig: AutomationConfig
    var backingAutomationConfig: AutomationConfig
	var automationConfig: AutomationConfig

	override val buildConfig: BuildConfig get() = automationConfig.buildConfig
	override val breakConfig: BreakConfig get() = automationConfig.breakConfig
	override val placeConfig: PlaceConfig get() = automationConfig.placeConfig
	override val interactConfig: InteractConfig get() = automationConfig.interactConfig
	override val rotationConfig: RotationConfig get() = automationConfig.rotationConfig
	override val inventoryConfig: InventoryConfig get() = automationConfig.inventoryConfig
	override val hotbarConfig: HotbarConfig get() = automationConfig.hotbarConfig
	override val eatConfig: EatConfig get() = automationConfig.eatConfig
}

class MutableAutomationConfigImpl : MutableAutomationConfig {
	override var defaultAutomationConfig: AutomationConfig = AutomationConfig.Companion.DEFAULT
		set(value) {
			field = value
			automationConfig = value
		}
	override var backingAutomationConfig: AutomationConfig = defaultAutomationConfig
	override var automationConfig: AutomationConfig = defaultAutomationConfig
		set(value) {
			if (value === defaultAutomationConfig) {
				if (backingAutomationConfig !== defaultAutomationConfig) {
					field.settings.forEach(Setting<*, *>::restoreOriginalCore)
				}
				field = value
			} else field.settings.forEach { setting ->
				value.settings.forEach { newSetting ->
					if (setting.name == newSetting.name) {
						if (setting.core.type != newSetting.core.type)
							throw IllegalStateException("Settings with the same name do not have the same type.")
						@Suppress("UNCHECKED_CAST")
						(setting as Setting<SettingCore<*>, Any>).core = newSetting.core as SettingCore<Any>
					}
				}
			}
			backingAutomationConfig = value
		}
}