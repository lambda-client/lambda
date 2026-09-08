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

package com.lambda.module.modules.debug

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.selection.select
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.task.start
import com.lambda.task.tasks.acquireStack
import net.minecraft.item.Items

@Suppress("unused")
object ContainerTest : Module(
    name = "ContainerTest",
    description = "Test container",
    tag = ModuleTag.DEBUG,
) {
    init {
        listen<TickEvent.Pre> {
//            info(task.info)
        }

        onEnable {
            acquireStack { Items.OBSIDIAN.select() }
                .start()
        }
    }
}
