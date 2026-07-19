
package com.minato.threading

import com.minato.Minato.mc
import com.minato.context.Automated
import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.event.EventFlow
import com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

/**
 * Runs the [block] in a safe context.
 *
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 */
inline fun <T> runSafe(block: SafeContext.() -> T): T? =
    SafeContext.create()?.run(block)

/**
 * Runs the [block] in an automated context.
 *
 * The context contains various settings for various systems.
 */
@JvmName("runSafeAutomated0")
context(safeContext: SafeContext)
inline fun <T> Automated.runSafeAutomated(automated: Automated = this, block: AutomatedSafeContext.() -> T): T =
    AutomatedSafeContext(safeContext, automated).run(block)

/**
 * Runs the [block] in an automated context.
 *
 * The context contains various settings for various systems.
 */
@JvmName("runSafeAutomated1")
inline fun <T> Automated.runSafeAutomated(block: AutomatedSafeContext.() -> T): T? {
    return AutomatedSafeContext(SafeContext.create() ?: return null, this).run(block)
}

/**
 * Runs the [block] in a coroutine.
 *
 * Writing to game data is discouraged as it may cause race conditions.
 */
inline fun runConcurrent(scheduler: CoroutineDispatcher = Dispatchers.Default, crossinline block: suspend CoroutineScope.() -> Unit) =
    EventFlow.minatoScope.launch(scheduler) {
        block()
    }

inline fun runIO(crossinline block: suspend CoroutineScope.() -> Unit) =
    runConcurrent(Dispatchers.IO) {
        block()
    }

inline fun taskContext(crossinline block: suspend CoroutineScope.() -> Unit) =
    EventFlow.minatoScope.launch {
        block()
    }

/**
 * Runs the [block] within a safe context in a coroutine.
 *
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 *
 * Writing to game data is discouraged as it may cause race conditions.
 */
inline fun runSafeConcurrent(crossinline block: suspend SafeContext.() -> Unit) {
    EventFlow.minatoScope.launch {
        runSafe { block() }
    }
}

/**
 * Executes a given task before a new render tick begins.
 *
 * This function should only be used for synchronization of threads that wish to dispatch
 * to OpenGL.
 */
inline fun recordRenderCall(crossinline block: () -> Unit) {
    mc.execute { block() }
}

/**
 * Schedules or executes the [block] on the main thread.
 */
inline fun runGameScheduled(crossinline block: () -> Unit) {
    if (isOnRenderThread()) {
        block()
        return
    }

    mc.execute { block() }
}

/**
 * Schedules a task on the main thread within a safe context.
 *
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 */
inline fun runSafeGameScheduled(crossinline block: SafeContext.() -> Unit) {
    runGameScheduled { runSafe { block() } }
}

/**
 * Executes a given task on the game's main thread within a safe context
 * and blocks the coroutine until the task is completed.
 *
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 *
 * This function blocks until the task is completed.
 */
suspend inline fun <T> awaitMainThread(noinline block: SafeContext.() -> T) =
    CompletableFuture.supplyAsync({ runSafe { block() } }, mc).await() ?: throw IllegalStateException("Unsafe")
