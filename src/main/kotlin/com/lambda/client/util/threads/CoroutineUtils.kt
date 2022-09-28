package com.lambda.client.util.threads

import kotlinx.coroutines.*

/**
 * Single thread scope to use in Lambda
 */
@OptIn(DelicateCoroutinesApi::class)
val mainScope = CoroutineScope(newSingleThreadContext("Lambda Main"))

/**
 * Common scope with [Dispatchers.Default]
 */
val defaultScope = CoroutineScope(Dispatchers.Default)

/**
 * Return true if the job is active, or false is not active or null
 */
val Job?.isActiveOrFalse get() = this?.isActive ?: false