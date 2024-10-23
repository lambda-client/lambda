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

package com.lambda.http

import java.net.HttpURLConnection

/**
 * Represents an HTTP response.
 */
class Response<Success : Any>(
    /**
     * The response
     */
    var data: Success? = null,

    /**
     * The error
     */
    var error: Throwable? = null,

    /**
     * The HTTP connection associated with the response.
     */
    var connection: HttpURLConnection? = null,
) {
    /**
     * Indicates whether the request was successful (HTTP status code 2xx).
     */
    val success: Boolean
        get() = connection?.let { return it.responseCode in 200..299 } ?: false
}
