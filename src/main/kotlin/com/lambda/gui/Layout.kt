/*
 * Copyright 2025 Lambda
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

package com.lambda.gui

import com.lambda.gui.dsl.ImGuiBuilder

/**
 * [Layout] is the core interface for rendering custom elements in ImGui.
 *
 * It contains the [layout] property which is a getter that returns a lambda
 * that will be invoked when inside an ImGui frame.
 *
 * You are able to call other layouts by passing the [ImGuiBuilder] context.
 */
interface Layout {
    val layout: ImGuiBuilder.() -> Unit
}
