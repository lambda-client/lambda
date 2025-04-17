/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.debug

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.tasks.AcquireMaterial.Companion.acquire
import net.minecraft.item.Items

object ContainerTest : Module(
    name = "ContainerTest",
    description = "Test container",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    init {
        listen<TickEvent.Pre> {
//            info(task.info)
        }

        onEnable {
            acquire {
                Items.OBSIDIAN.select()
            }.run()
        }
    }
}
