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

package com.lambda.module.modules.client

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module

object AnticheatPreset : Module(
    name = "AnticheatPreset",
    description = "Change the behavior of certain modules based on the server's anticheat",
) {
    private val autoDetect by setting("Automatic Detection", true, description = "Detect the server's anticheat using predefined patterns")
    private val minimumDetection by setting("Minimum Detection", 1, 1..5, 1, description = "How many patterns must be detected before assigning the anticheat")

    val anticheatPreset by setting("Anticheat Preset", Anticheats.Grim3) { !autoDetect }

    init {
        listener<TickEvent.Pre> {

        }
    }

    enum class Anticheats {
        Grim2, Grim3, Matrix, NCP
    }
}
