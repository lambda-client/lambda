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

package com.lambda.threading

import kotlin.concurrent.thread

/**
 * Registers a shutdown hook to execute the specified [block] of code when the application is shutting down.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/lang/hook-design.html">Design of the Shutdown Hooks API</a>
*/
inline fun onShutdown(crossinline block: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(thread(start = false) { block() })
}


