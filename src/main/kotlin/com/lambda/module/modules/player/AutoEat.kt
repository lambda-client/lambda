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

package com.lambda.module.modules.player

import com.lambda.config.AutomationConfig.Companion.automationConfig
import com.lambda.config.groups.EatConfig.Companion.reasonEating
import com.lambda.config.groups.EatSettings
import com.lambda.config.groups.InventorySettings
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.tasks.EatTask
import com.lambda.task.tasks.EatTask.Companion.eat
import com.lambda.threading.runSafeAutomated
import com.lambda.util.NamedEnum

object AutoEat : Module(
    name = "AutoEat",
    description = "Eats food when you are hungry",
    tag = ModuleTag.PLAYER,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        Eating("Eating"),
        Inventory("Inventory")
    }

    override val eatConfig = EatSettings(this, Group.Eating)
    override val inventoryConfig = InventorySettings(this, Group.Inventory)
    private var eatTask: EatTask? = null

    init {
        defaultAutomationConfig = automationConfig {
            hideAll(
                buildConfig,
                breakConfig,
                placeConfig,
                interactConfig,
                rotationConfig,
                inventoryConfig,
                hotbarConfig,
            )
        }

        listen<TickEvent.Pre> {
            val reason = runSafeAutomated { reasonEating() }
            if (eatTask != null || !reason.shouldEat()) return@listen

            val task = eat()
            task.finally { eatTask = null }
            task.run()
            eatTask = task
        }

        onDisable {
            eatTask?.cancel()
            eatTask = null
        }
    }
}
