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

import androidx.compose.runtime.withFrameNanos

/**
 * Waits until [millis] of the interface's own time have passed. That time only runs while the
 * interface is being drawn, so nothing times out behind a closed screen, and the wait ends on
 * the thread the interface lives on, which a plain delay would not.
 */
suspend fun awaitFrames(millis: Long) {
    val start = withFrameNanos { it }
    while (withFrameNanos { it } - start < millis * 1_000_000) Unit
}
