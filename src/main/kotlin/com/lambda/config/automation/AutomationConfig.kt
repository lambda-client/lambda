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
import com.lambda.config.categories.AutomationCategory
import com.lambda.config.groups.BreakSettings
import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.EatSettings
import com.lambda.config.groups.HotbarSettings
import com.lambda.config.groups.InteractSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.Automated
import com.lambda.module.Module


open class AutomationConfig(
	override val name: String,
	configCategory: ConfigCategory = AutomationCategory
) : Config(configCategory), Automated {
	@Tab(BUILD_TAB) override val buildConfig = BuildSettings(this)
	@Tab(BREAK_TAB) override val breakConfig = BreakSettings(this)
	@Tab(INTERACT_TAB) override val interactConfig = InteractSettings(this)
	@Tab(ROTATION_TAB) override val rotationConfig = RotationSettings(this)
	@Tab(INVENTORY_TAB) override val inventoryConfig = InventorySettings(this)
	@Tab(HOTBAR_TAB) override val hotbarConfig = HotbarSettings(this)
	@Tab(EAT_TAB) override val eatConfig = EatSettings(this)

	companion object {
		private const val BUILD_TAB = "Build"
		private const val BREAK_TAB = "Break"
		private const val INTERACT_TAB = "Interact"
		private const val ROTATION_TAB = "Rotation"
		private const val INVENTORY_TAB = "Inventory"
		private const val HOTBAR_TAB = "Hotbar"
		private const val EAT_TAB = "Eat"

		context(module: Module)
        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String = module.name,
	        edits: (AutomationConfig.() -> Unit)? = null
		) { this.defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) } }

        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String,
	        edits: (AutomationConfig.() -> Unit)? = null
		) { defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) } }

		val DEFAULT = AutomationConfig("Default")
    }
}