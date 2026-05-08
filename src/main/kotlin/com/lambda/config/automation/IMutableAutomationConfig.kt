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

import com.lambda.config.Config
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.config.settings.blocks.BreakConfig
import com.lambda.config.settings.blocks.BuildConfig
import com.lambda.config.settings.blocks.EatConfig
import com.lambda.config.settings.blocks.HotbarConfig
import com.lambda.config.settings.blocks.InteractConfig
import com.lambda.config.settings.blocks.InventoryConfig
import com.lambda.config.settings.blocks.RotationConfig
import com.lambda.context.Automated

interface IMutableAutomationConfig : Automated {
    var defaultAutomationConfig: AutomationConfig
    var backingAutomationConfig: AutomationConfig
	var automationConfig: AutomationConfig

	override val buildConfig get() = automationConfig.buildConfig
	override val breakConfig get() = automationConfig.breakConfig
	override val interactConfig get() = automationConfig.interactConfig
	override val rotationConfig get() = automationConfig.rotationConfig
	override val inventoryConfig get() = automationConfig.inventoryConfig
	override val hotbarConfig get() = automationConfig.hotbarConfig
	override val eatConfig get() = automationConfig.eatConfig
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
					Config.forEachSetting(field) { it.restoreOriginalCore() }
				}
				field = value
			} else {
				Config.forEachMatchingSetting(field.settingLayers, value.settingLayers) { setting, newSetting ->
					if (setting.core.type != newSetting.core.type)
						throw IllegalStateException("Settings with the same name do not have the same type.")
					@Suppress("UNCHECKED_CAST")
					(setting as Setting<SettingCore<Any>, Any>).core = newSetting.core as SettingCore<Any>
				}
			}
			backingAutomationConfig = value
		}
}