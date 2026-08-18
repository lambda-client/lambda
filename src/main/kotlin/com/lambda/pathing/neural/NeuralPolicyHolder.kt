/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.neural

import java.nio.file.Files
import java.nio.file.Path

/**
 * Lazily loads and caches the ONNX movement policy from disk so planning never pays the
 * load twice and a missing model degrades to a typed refusal rather than a crash.
 *
 * The default location is `neolambda/policy.onnx` under the Minecraft run directory;
 * `PathingConfig.neuralModelPath` overrides it. Reloads when the resolved path changes.
 */
object NeuralPolicyHolder {
    private const val DEFAULT_RELATIVE_PATH = "neolambda/policy.onnx"

    private var loadedPath: String? = null
    private var policy: NeuralMovementPolicy? = null
    private var failure: String? = null

    /**
     * The cached policy for [modelPath] (or the default), or null with [lastFailure]
     * set when no model could be loaded. Never throws.
     */
    @Synchronized
    fun policy(modelPath: String?): NeuralMovementPolicy? {
        val resolved = (modelPath ?: DEFAULT_RELATIVE_PATH)
        if (resolved == loadedPath) return policy
        loadedPath = resolved
        policy?.close()
        policy = null
        failure = null
        val path = Path.of(resolved)
        if (!Files.exists(path)) {
            failure = "policy model not found at ${path.toAbsolutePath()}"
            return null
        }
        return try {
            NeuralMovementPolicy.fromFile(path).also { policy = it }
        } catch (error: Exception) {
            failure = "failed to load policy at ${path.toAbsolutePath()}: ${error.message}"
            null
        }
    }

    @Synchronized
    fun lastFailure(): String? = failure
}
