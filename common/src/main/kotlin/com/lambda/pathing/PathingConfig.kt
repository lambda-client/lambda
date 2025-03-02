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

package com.lambda.pathing

import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.RequestConfig

abstract class PathingConfig(priority: Priority) : RequestConfig<PathRequest>(priority) {
    abstract val kP: Double
    abstract val kI: Double
    abstract val kD: Double
    abstract val tolerance: Double
    abstract val cutoffTimeout: Long
    abstract val shortcutLength: Int
    abstract val clearancePrecition: Double
    abstract val allowSprint: Boolean
    abstract val maxFallHeight: Double

    abstract val assumeJesus: Boolean

    override fun requestInternal(request: PathRequest) {
        PathingManager.registerRequest(this, request)
    }
}
