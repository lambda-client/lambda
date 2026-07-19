
package com.minato.config.automation

import com.minato.config.Config
import com.minato.config.ConfigCategory
import com.minato.config.Tab
import com.minato.config.blocks.BreakSettings
import com.minato.config.blocks.BuildSettings
import com.minato.config.blocks.EatSettings
import com.minato.config.blocks.HotbarSettings
import com.minato.config.blocks.InteractSettings
import com.minato.config.blocks.InventorySettings
import com.minato.config.blocks.RotationSettings
import com.minato.config.categories.AutomationCategory
import com.minato.context.Automated
import com.minato.module.Module

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