/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

interface SearchClock {
    fun elapsedMillis(): Long

    fun onExpansion() {}
}

class SystemSearchClock : SearchClock {
    private val startedNanos = System.nanoTime()

    override fun elapsedMillis(): Long = (System.nanoTime() - startedNanos) / 1_000_000
}

class VirtualSearchClock(private val microsPerExpansion: Long = DEFAULT_MICROS_PER_EXPANSION) : SearchClock {
    private var expansions = 0L

    override fun elapsedMillis(): Long = expansions * microsPerExpansion / 1_000

    override fun onExpansion() {
        expansions++
    }

    fun cursorFrame(): Int = (elapsedMillis() / MILLIS_PER_TICK).toInt()

    companion object {
        const val DEFAULT_MICROS_PER_EXPANSION = 640L
        private const val MILLIS_PER_TICK = 50L
    }
}
