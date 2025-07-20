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
 * This function ensures that the given [block] will be executed before the JVM terminates, allowing graceful cleanup
 * or finalization tasks to be performed.
 *
 * @param block the code to be executed on shutdown.
 *
 * ## Usage:
 * ```
 * onShutdown {
 *     // Perform cleanup tasks or finalizations here
 *     println("Shutting down gracefully...")
 * }
 * ```
 *
 * ## Advantages:
 * - Provides a convenient and reliable mechanism for executing cleanup tasks when the application exits.
 * - Helps ensure that critical resources are released properly, preventing resource leaks or data corruption.
 * - Can be used to handle cleanup in various scenarios, such as closing open connections, saving application state,
 *   or logging shutdown events.
 *
 * ## Code Examples:
 * 1. Registering a simple shutdown hook:
 *    ```
 *    onShutdown {
 *        println("Performing cleanup...")
 *    }
 *    ```
 * 2. Performing resource cleanup on shutdown:
 *    ```
 *    onShutdown {
 *        databaseConnection.close()
 *        fileWriter.close()
 *        println("Resources closed successfully.")
 *    }
 *    ```
 *
 * ## What Not to Do:
 * - Avoid performing time-consuming or blocking operations inside the shutdown hook, as it may delay the shutdown process
 *   and lead to undesirable behavior.
 * - Do not rely solely on shutdown hooks for critical tasks that must be executed reliably. Consider using other
 *   mechanisms such as proper exception handling or manual cleanup where necessary.
 * - Avoid registering multiple shutdown hooks for the same purpose, as it may result in unexpected behavior or conflicts
 *   between the hooks.
 *
 * ## Edge Cases:
 * - If the JVM is terminated abruptly or forcefully (e.g., via `kill -9` on Unix-like systems), the shutdown hooks may
 *   not have a chance to run, leading to potential resource leaks or incomplete cleanup. This is a rare scenario but
 *   should be considered when designing critical cleanup tasks.
 * - In certain environments or configurations where the JVM shutdown process is interrupted or bypassed, such as when
 *   running in a containerized environment where the container itself is forcibly stopped, the shutdown hooks may not
 *   execute as expected. It's important to be aware of the behavior of the runtime environment and handle such cases
 *   accordingly, possibly by implementing additional cleanup mechanisms or relying on external systems for graceful
 *   shutdown coordination.
 */
inline fun onShutdown(crossinline block: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(thread(start = false) { block() })
}


