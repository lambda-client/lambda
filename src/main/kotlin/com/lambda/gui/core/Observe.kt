/*
 * Copyright 2026 Lambda
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

package com.lambda.gui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.lambda.context.SafeContext
import com.lambda.threading.runSafe

/**
 * Turns anything the client knows into Compose state: a setting, whether a module is enabled,
 * the player's position.
 *
 * None of that is observable, and most of it changes from code that has never heard of the GUI.
 * So [read] is simply evaluated once per frame, and the caller recomposes only when the result is
 * not equal to the last one. That keeps the model free of GUI concerns and makes the interface
 * redraw exactly when something it shows has changed.
 *
 * ```
 * val enabled by observe { module.isEnabled }
 * ```
 *
 * [read] runs on the client thread every frame for as long as the caller is composed, so keep it
 * to reading: return an immutable value, and copy a collection that is mutated in place.
 */
@Composable
fun <T> observe(read: () -> T): State<T> {
    val observations = LocalObservations.current
    val observation = remember { Observation(read) }
    observation.read = read
    DisposableEffect(observations) {
        observations.active += observation
        onDispose { observations.active -= observation }
    }
    return observation.state
}

/** [observe] for values that need the player and world; null while there is neither */
@Composable
fun <T> observeSafe(read: SafeContext.() -> T): State<T?> = observe { runSafe(read) }

internal class Observation<T>(var read: () -> T) {
    val state = mutableStateOf(read())

    fun refresh() {
        state.value = read()
    }
}

/**
 * The values one part of the interface observes. A part that stays composed while it is not on
 * screen gets a group of its own, see [Retained], which is then simply not refreshed.
 */
internal class Observations {
    val active = ArrayList<Observation<*>>()

    /** Re-reads every observed value. Called once per frame, before composition. */
    fun refresh() = active.forEach { it.refresh() }
}

/** The group [observe] adds to */
internal val LocalObservations = staticCompositionLocalOf<Observations> { error("observe was called outside the interface") }
