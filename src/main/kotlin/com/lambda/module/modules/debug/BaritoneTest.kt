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

import baritone.api.pathing.goals.GoalXZ
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.module.Module
import com.lambda.module.ModuleTag

@Suppress("unused")
object BaritoneTest : Module(
    name = "BaritoneTest",
    description = "Test Baritone",
    tag = ModuleTag.DEBUG,
) {
    init {
        listen<TickEvent.Pre> {
            BaritoneHandler.setGoalAndPath(GoalXZ(0, 0))
        }

        onDisable {
            BaritoneHandler.cancel()
        }
    }
}
