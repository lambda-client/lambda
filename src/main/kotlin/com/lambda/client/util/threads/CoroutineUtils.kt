package com.lambda.client.util.threads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext

/**
 * Single thread scope to use in Lambda
 */
@Suppress("EXPERIMENTAL_API_USAGE")
val mainScope = CoroutineScope(newSingleThreadContext("Lambda Main"))

/**
 * Common scope with [Dispatchers.Default]
 */
val defaultScope = CoroutineScope(Dispatchers.Default)

/**
 * Return true if the job is active, or false is not active or null
 */
val Job?.isActiveOrFalse get() = this?.isActive ?: false