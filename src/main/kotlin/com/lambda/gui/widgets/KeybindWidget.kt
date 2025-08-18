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

package com.lambda.gui.widgets

import com.lambda.gui.Layout
import com.lambda.gui.dsl.ImStorageDsl.imProperty
import com.lambda.util.KeyCode
import com.lambda.util.KeyboardUtils
import imgui.ImGui.isMouseClicked
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiMouseButton
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KMutableProperty0

fun keybindWidget(
    label: String,
    description: String,
    value: KMutableProperty0<KeyCode>,
): Layout = {
    val key = value.get()
    val scancode = if (key == KeyCode.UNBOUND) -69 else GLFW.glfwGetKeyScancode(key.code)
    val translated = if (key == KeyCode.UNBOUND) key else KeyCode.virtualMapUS(key.code, scancode)

    var listening by imProperty<Boolean>("$label-listening", false)
    val preview = if (listening) "$label: Press any key…" else "$label: $translated"

    if (listening) {
        withStyleColor(ImGuiCol.Button, 0.20f, 0.50f, 1.00f, 1.00f) {
            withStyleColor(ImGuiCol.ButtonHovered, 0.25f, 0.60f, 1.00f, 1.00f) {
                withStyleColor(ImGuiCol.ButtonActive, 0.20f, 0.50f, 0.95f, 1.00f) {
                    button(preview)
                }
            }
        }
    } else {
        button(preview) { listening = true }
    }

    lambdaTooltip(
        if (!listening)
            description.ifBlank { "Click to set. Right-click to unbind. Esc cancels. Backspace/Delete unbinds." }
        else
            "Listening… Press a key to bind. Esc to cancel. Backspace/Delete to unbind."
    )

    onItemClick(ImGuiMouseButton.Right) {
        value.set(KeyCode.UNBOUND)
        listening = false
    }

    if (listening && !isAnyItemHovered && isMouseClicked(ImGuiMouseButton.Left)) {
        listening = false
    }

    sameLine()
    smallButton("Unbind") {
        value.set(KeyCode.UNBOUND)
        listening = false
    }
    onItemHover(ImGuiHoveredFlags.Stationary) {
        lambdaTooltip("Clear binding")
    }

    val poll = KeyboardUtils.lastEvent
    if (listening && poll.isPressed) {
        val key = poll.translated
        when (key) {
            KeyCode.ESCAPE -> listening = false
            KeyCode.BACKSPACE, KeyCode.DELETE -> {
                value.set(KeyCode.UNBOUND)
                listening = false
            }
            else -> {
                value.set(key)
                listening = false
            }
        }
    }
}
