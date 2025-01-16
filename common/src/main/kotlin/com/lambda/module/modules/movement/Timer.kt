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

package com.lambda.module.modules.movement

import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag

object Timer : Module(
    name = "Timer",
    description = "Modify client tick speed.",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.WORLD)
) {
    private val timer by setting("Timer", 1.0, 0.0..10.0, 0.01)

    init {
        listen<ClientEvent.TimerUpdate> {
            it.speed = timer.coerceAtLeast(0.05)
        }
    }
}
