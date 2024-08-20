package com.lambda.threading

import com.lambda.Lambda.mc
import com.lambda.context.ClientContext
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow
import com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

/**
 * Executes a block of code only if the context is safe. A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 *
 * If the context is not safe, the function will return null and the block of code will not be executed.
 *
 * @param block The block of code to be executed within the safe context.
 * @return The result of the block execution if the context is safe, null otherwise.
 */
inline fun <T> runSafe(block: SafeContext.() -> T) =
    ClientContext().toSafe()?.let { block(it) }

/**
 * This function is used to execute a block of code on a new thread running asynchronously to the game thread.
 * It should only be used when you need to perform read actions on the game data (not write).
 *
 * Caution: Using this function to write to the game data can lead to race conditions. Therefore, it is recommended
 * to use this function only for read operations to avoid potential concurrency issues.
 *
 * @param block The block of code to be executed concurrently.
 */
inline fun runConcurrent(crossinline block: suspend () -> Unit) =
    EventFlow.lambdaScope.launch {
        block()
    }

inline fun runIO(crossinline block: suspend () -> Unit) =
    EventFlow.lambdaScope.launch(Dispatchers.IO) {
        block()
    }

inline fun taskContext(crossinline block: suspend () -> Unit) =
    EventFlow.lambdaScope.launch {
        block()
    }

/**
 * This function is used to execute a block of code within a safe context on a new thread running asynchronously to the game thread.
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 *
 * If the context is not safe, the function will not execute the block of code.
 *
 * @param block The block of code to be executed within the safe context.
 */
inline fun runSafeConcurrent(crossinline block: SafeContext.() -> Unit) {
    EventFlow.lambdaScope.launch {
        runSafe { block() }
    }
}

/**
 * Executes a given task on the game's main thread.
 *
 * This function is used when a task needs to be performed on the game's main thread,
 * as certain operations are not safe to perform on other threads.
 * It uses the Minecraft client's `execute` method to schedule the task.
 *
 * Note: This function is non-blocking as the task is scheduled to be executed
 * on the game's main thread, but does not provide any feedback.
 *
 * @param block The task to be executed on the game's main thread.
 */
inline fun runGameScheduled(crossinline block: () -> Unit) {
    if (isOnRenderThread()) {
        block()
        return
    }

    recordRenderCall {
        block()
    }
}

/**
 * Executes a given task on the game's main thread within a safe context.
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 *
 * This function is used when a task needs to be performed on the game's main thread,
 * as certain operations are not safe to perform on other threads.
 * It uses the Minecraft client's `execute` method to schedule the task.
 *
 * Note: This function is non-blocking as the task is scheduled to be executed
 * on the game's main thread, but does not provide any feedback.
 *
 * @param block The task to be executed on the game's main thread within a safe context.
 */
inline fun runSafeGameConcurrent(crossinline block: SafeContext.() -> Unit) {
    runGameScheduled { runSafe { block() } }
}

/**
 * Executes a given task on the game's main thread within a safe context
 * and blocks the coroutine until the task is completed.
 * A context is considered safe when all the following properties are not null:
 * - [SafeContext.world]
 * - [SafeContext.player]
 * - [SafeContext.interaction]
 * - [SafeContext.connection]
 *
 * This function is used when a task needs to be performed on the game's main thread,
 * as certain operations are not safe to perform on other threads.
 *
 * Note:
 * This function is blocking
 * as it uses [CompletableFuture]'s [await] method to [suspend] the coroutine until the task is completed.
 *
 * @param block The task to be executed on the game's main thread within a safe context.
 */
suspend inline fun <T> awaitMainThread(noinline block: SafeContext.() -> T) =
    CompletableFuture.supplyAsync({ runSafe { block() } }, mc).await() ?: throw IllegalStateException("Unsafe")
