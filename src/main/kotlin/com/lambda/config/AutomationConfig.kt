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

import com.lambda.config.configurations.AutomationConfigs
import com.lambda.config.groups.BreakSettings
import com.lambda.config.groups.BuildSettings
import com.lambda.config.groups.EatSettings
import com.lambda.config.groups.HotbarSettings
import com.lambda.config.groups.InteractSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.Automated
import com.lambda.event.events.onStaticRender
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.module.Module
import com.lambda.util.NamedEnum


open class AutomationConfig(
    override val name: String,
    configuration: Configuration = AutomationConfigs
) : Configurable(configuration), Automated {
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
        fun MutableAutomationConfig.setDefaultAutomationConfig(
	        name: String = module.name,
	        edits: (AutomationConfig.() -> Unit)? = null
		) {
			defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) }
		}

        fun MutableAutomationConfig.setDefaultAutomationConfig(
	        name: String,
	        edits: (AutomationConfig.() -> Unit)? = null
		) {
			defaultAutomationConfig = AutomationConfig("$name Automation Config").apply { edits?.invoke(this) }
		}

        object DEFAULT : AutomationConfig("Default") {
            val renders by setting("Render", false).group(Group.Render)
            val avoidDesync by setting("Avoid Desync", true, "Cancels incoming inventory update packets if they match previous actions").group(Group.Debug)
            val desyncTimeout by setting("Desync Timeout", 30, 1..30, 1, unit = " ticks", description = "Time to store previous inventory actions before dropping the cache") { avoidDesync }.group(Group.Debug)
            val showAllEntries by setting("Show All Entries", false, "Show all entries in the task tree").group(Group.Debug)
            val shrinkFactor by setting("Shrink Factor", 0.001, 0.0..1.0, 0.001).group(Group.Debug)
            val ignoreItemDropWarnings by setting("Ignore Drop Warnings", false, "Hides the item drop warnings from the break manager").group(Group.Debug)

            @Volatile
            var drawables = listOf<Drawable>()

            init {
                onStaticRender {
                    if (renders)
                        with(it) { drawables.forEach { with(it) { buildRenderer() } } }
                }
            }
        }
    }
}