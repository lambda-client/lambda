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

package com.lambda.event.events

import androidx.compose.runtime.Composable
import com.lambda.event.Event

sealed class ComposeEvent : Event {
    private val added = mutableListOf<@Composable () -> Unit>()
    val contents: List<@Composable () -> Unit> get() = added

    fun content(content: @Composable () -> Unit) {
        added += content
    }

    sealed class Hud : ComposeEvent() {
        class Pre : Hud()
        class Post : Hud()
    }

    sealed class Gui : ComposeEvent() {
        class Pre : Gui()
        class Post : Gui()
    }
}
