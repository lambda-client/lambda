
package com.minato.threading

import kotlin.concurrent.thread

/**
 * Registers a shutdown hook to execute the specified [block] of code when the application is shutting down.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/lang/hook-design.html">Design of the Shutdown Hooks API</a>
*/
inline fun onShutdown(crossinline block: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(thread(start = false) { block() })
}


