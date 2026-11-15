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

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Whether the part of the interface this is read in is on screen. False inside a hidden
 * [Retained], where a popup has to hold back: it floats above everything, hidden or not.
 */
val LocalShown = compositionLocalOf { true }

/**
 * Keeps [content] composed whether or not it is [shown], so that showing it is a matter of
 * putting it on the screen rather than of building it, which for the click GUI's hundreds of
 * rows takes long enough to be felt as a stutter.
 *
 * While hidden, the content is neither drawn nor reachable by the pointer, and nothing it
 * observes is re-read: its observations join [observations], which the owner leaves alone
 * meanwhile. It costs nothing but the memory it is kept in.
 */
@Composable
internal fun Retained(shown: Boolean, observations: Observations, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShown provides shown, LocalObservations provides observations) {
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { if (shown) placeable.place(0, 0) }
            }
        ) { content() }
    }
}
