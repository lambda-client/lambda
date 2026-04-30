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
import com.lambda.util.NamedEnum


open class AutomationConfig(
	override val name: String,
	configCategory: ConfigCategory = AutomationCategory
) : Config(configCategory), Automated {
	enum class Group(override val displayName: String) : NamedEnum {
		Build("Build"),
		Break("Break"),
		Interact("Interact"),
		Rotation("Rotation"),
		Inventory("Inventory"),
		Hotbar("Hotbar"),
		Eat("Eat"),
		Render("Render"),
		Debug("Debug")
	}

	override val buildConfig = BuildSettings(this, Group.Build)
	override val breakConfig = BreakSettings(this, Group.Break)
	override val interactConfig = InteractSettings(this, Group.Interact)
	override val rotationConfig = RotationSettings(this, Group.Rotation)
	override val inventoryConfig = InventorySettings(this, Group.Inventory)
	override val hotbarConfig = HotbarSettings(this, Group.Hotbar)
	override val eatConfig = EatSettings(this, Group.Eat)

	companion object {
		context(module: Module)
        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String = module.name,
	        edits: (AutomationConfig.() -> Unit)? = null
		) {
			this.defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) }
		}

        fun IMutableAutomationConfig.setDefaultAutomationConfig(
	        name: String,
	        edits: (AutomationConfig.() -> Unit)? = null
		) {
			defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) }
		}

		object DEFAULT : AutomationConfig("Default")
    }
}