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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lambda.event.EventFlow.post
import com.lambda.event.events.ComposeEvent

internal class ComposeSlot(private val event: () -> ComposeEvent) {
	private var contents by mutableStateOf(emptyList<@Composable () -> Unit>())
	val isEmpty get() = contents.isEmpty()

	fun collect(active: Boolean = true) {
		val collected =
			if (active) event().post().contents
			else emptyList()
		if (collected != contents) contents = collected
	}

	@Composable
	fun Content() = contents.forEach { it() }
}
