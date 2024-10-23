/*
 * Copyright 2024 Lambda
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

package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.module.modules.client.ClickGui

abstract class ListWindow<T : ListButton>(
    owner: AbstractClickGui,
) : WindowComponent<T>(owner) {
    private var scrollOffset: Double = 0.0
    private var rubberbandRequest = 0.0
    private var rubberbandDelta = 0.0

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Tick -> {
                rubberbandDelta += rubberbandRequest
                rubberbandRequest = 0.0

                rubberbandDelta *= 0.5
                if (rubberbandDelta < 0.05) rubberbandDelta = 0.0

                var y = scrollOffset + rubberbandDelta
                contentComponents.children.forEach { button ->
                    button.heightOffset = y
                    y += button.size.y + button.listStep
                }
            }

            is GuiEvent.MouseScroll -> {
                val delta = e.delta * 10.0 * ClickGui.scrollSpeed
                scrollOffset += delta

                val prevOffset = scrollOffset
                val range = -contentComponents.children.sumOf { it.size.y + it.listStep }
                scrollOffset = scrollOffset.coerceAtLeast(range).coerceAtMost(0.0)

                rubberbandRequest += prevOffset - scrollOffset
            }
        }

        super.onEvent(e)
    }
}
