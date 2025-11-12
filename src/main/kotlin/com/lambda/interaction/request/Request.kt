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

package com.lambda.interaction.request

import com.lambda.context.Automated

/**
 * A simple format to ensure basic requirements and information when requesting a manager.
 *
 * @property requestId Used for tracking how many requests there have been and what number this request is.
 * @property fresh If this request is new.
 * @property done If this request has been completed.
 */
abstract class Request : Automated {
    abstract val requestId: Int
    var fresh = true

    abstract val done: Boolean

    abstract fun submit(queueIfClosed: Boolean = true): Request

    companion object {
        fun submit(request: Request, queueIfClosed: Boolean = true) =
            request.submit(queueIfClosed)
    }
}
