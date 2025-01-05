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

package com.lambda.core

import com.lambda.Lambda
import com.lambda.Lambda.LOG
import com.lambda.util.Communication.ascii
import com.lambda.util.reflections.getInstances
import kotlin.system.measureTimeMillis
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object Loader {
    private val started = System.currentTimeMillis()

    val runtime: String
        get() = "${(System.currentTimeMillis() - started).toDuration(DurationUnit.MILLISECONDS)}"

    private val loadables = getInstances<Loadable> { forPackages("com.lambda") }

    fun initialize(): Long {
        ascii.split("\n").forEach { LOG.info(it) }
        LOG.info("Initializing ${Lambda.MOD_NAME} ${Lambda.VERSION} (${loadables.size} loaders)...")

        val initTime = measureTimeMillis {
            loadables.forEach {
                var response: String
                val time = measureTimeMillis { response = it.load() }
                LOG.info("$response ($time ms)")
            }
        }

        return initTime
    }
}
