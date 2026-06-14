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
import com.lambda.config.ConfigCategory
import com.lambda.config.Tab
import com.lambda.config.categories.AutomationCategory
import com.lambda.config.settings.blocks.BreakSettings
import com.lambda.config.settings.blocks.BuildSettings
import com.lambda.config.settings.blocks.EatSettings
import com.lambda.config.settings.blocks.HotbarSettings
import com.lambda.config.settings.blocks.InteractSettings
import com.lambda.config.settings.blocks.InventorySettings
import com.lambda.config.settings.blocks.RotationSettings
import com.lambda.context.Automated
import com.lambda.module.Module


open class AutomationConfig(
	name: String,
	configCategory: ConfigCategory = AutomationCategory
) : Config(
	name,
	configCategory
), Automated {
	@Tab(BUILD_TAB) override val buildConfig by configBlock(BuildSettings(this))
	@Tab(BREAK_TAB) override val breakConfig by configBlock(BreakSettings(this))
	@Tab(INTERACT_TAB) override val interactConfig by configBlock(InteractSettings(this))
	@Tab(ROTATION_TAB) override val rotationConfig by configBlock(RotationSettings(this))
	@Tab(INVENTORY_TAB) override val inventoryConfig by configBlock(InventorySettings(this))
	@Tab(HOTBAR_TAB) override val hotbarConfig by configBlock(HotbarSettings(this))
	@Tab(EAT_TAB) override val eatConfig by configBlock(EatSettings(this))

	companion object {
		private const val BUILD_TAB = "Build"
		private const val BREAK_TAB = "Break"
		private const val INTERACT_TAB = "Interact"
		private const val ROTATION_TAB = "Rotation"
		private const val INVENTORY_TAB = "Inventory"
		private const val HOTBAR_TAB = "Hotbar"
		private const val EAT_TAB = "Eat"

		@DslMarker
		private annotation class AutomationConfigMarker

		@AutomationConfigMarker
		context(module: Module)
        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String = module.name
		) = AutomationConfig("$name Automation Config").also { this.defaultAutomationConfig = it }

		@AutomationConfigMarker
        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String
		) = AutomationConfig("$name Automation Config").also { this.defaultAutomationConfig = it }

		val DEFAULT = AutomationConfig("Default")
    }
}