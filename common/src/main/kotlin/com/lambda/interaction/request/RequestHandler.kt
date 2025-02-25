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

import java.util.concurrent.ConcurrentHashMap

/**
 * This class manages a collection of requests, each associated with a `RequestConfig`.
 * It provides a mechanism to register requests and select the highest priority request
 * for processing.
 */
abstract class RequestHandler<R : Request> {

    private val requestMap = ConcurrentHashMap<RequestConfig<R>, R>()

    /**
     * The currently active request.
     */
    var currentRequest: R? = null; protected set

    /**
     * Registers a new request with the given configuration.
     *
     * @param config The configuration for the request.
     * @param request The request to register.
     * @return The registered request.
     */
    fun registerRequest(config: RequestConfig<R>, request: R): R {
        requestMap[config] = request
        return request
    }

    /**
     * Updates the current request to the highest priority registered request.
     * Clears the internal request map after updating.
     *
     * @return True, if the request was updated.
     */
    protected open fun updateRequest(
        keepIfNull: Boolean = false,
        filter: (Map.Entry<RequestConfig<R>, R>) -> Boolean = { true }
    ): Boolean {
        val prev = currentRequest

        currentRequest = requestMap.entries
                .filter(filter)
                .maxByOrNull { it.key.priority }?.value

        if (keepIfNull && currentRequest == null) currentRequest = prev

        requestMap.clear()
        return prev != currentRequest
    }
}