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

@Composable
fun <T> observeSafe(read: SafeContext.() -> T): State<T?> = observe { runSafe(read) }

internal class Observation<T>(var read: () -> T) {
    val state = mutableStateOf(read())

    fun refresh() {
        state.value = read()
    }
}

internal class Observations {
    val active = ArrayList<Observation<*>>()

    fun refresh() = active.forEach { it.refresh() }
}

internal val LocalObservations = staticCompositionLocalOf<Observations> { error("observe was called outside the interface") }
