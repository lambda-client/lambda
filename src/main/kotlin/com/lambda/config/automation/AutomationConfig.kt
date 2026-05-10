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
	override val name: String,
	configCategory: ConfigCategory = AutomationCategory
) : Config(configCategory), Automated {
	@Tab(BuildTab) override val buildConfig = BuildSettings(this)
	@Tab(BreakTab) override val breakConfig = BreakSettings(this)
	@Tab(InteractTab) override val interactConfig = InteractSettings(this)
	@Tab(RotationTab) override val rotationConfig = RotationSettings(this)
	@Tab(InventoryTab) override val inventoryConfig = InventorySettings(this)
	@Tab(HotbarTab) override val hotbarConfig = HotbarSettings(this)
	@Tab(EatTab) override val eatConfig = EatSettings(this)

	companion object {
		private const val BuildTab = "Build"
		private const val BreakTab = "Break"
		private const val InteractTab = "Interact"
		private const val RotationTab = "Rotation"
		private const val InventoryTab = "Inventory"
		private const val HotbarTab = "Hotbar"
		private const val EatTab = "Eat"

		context(module: Module)
        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String = module.name,
	        edits: (AutomationConfig.() -> Unit)? = null
		) { this.defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) } }

        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String,
	        edits: (AutomationConfig.() -> Unit)? = null
		) { defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) } }

		val Default = AutomationConfig("Default")
    }
}