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

package com.lambda.config.settings.complex

import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.KeyCode
import com.lambda.util.KeyboardUtils
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.extension.CommandBuilder
import imgui.ImGui.isMouseClicked
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiHoveredFlags
import imgui.flag.ImGuiMouseButton
import net.minecraft.command.CommandRegistryAccess
import org.lwjgl.glfw.GLFW

class KeybindSetting(
    override val name: String,
    defaultValue: KeyCode,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<KeyCode>(
    defaultValue,
    TypeToken.get(KeyCode::class.java).type,
    description,
    visibility
) {
    private var listening = false

    override fun ImGuiBuilder.buildLayout() {
        val key = value
        val scancode = if (key == KeyCode.UNBOUND) -69 else GLFW.glfwGetKeyScancode(key.code)
        val translated = if (key == KeyCode.UNBOUND) key else KeyCode.virtualMapUS(key.code, scancode)
        val preview = if (listening) "$name: Press any key…" else "$name: $translated"

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

        lambdaTooltip {
            if (!listening) {
                description.ifBlank { "Click to set. Right-click to unbind. Esc cancels. Backspace/Delete unbinds." }
            } else {
                "Listening… Press a key to bind. Esc to cancel. Backspace/Delete to unbind."
            }
        }

        onItemClick(ImGuiMouseButton.Right) {
            value = KeyCode.UNBOUND
            listening = false
        }

        if (listening && !isAnyItemHovered && isMouseClicked(ImGuiMouseButton.Left)) {
            listening = false
        }

        sameLine()
        smallButton("Unbind") {
            value = KeyCode.UNBOUND
            listening = false
        }
        onItemHover(ImGuiHoveredFlags.Stationary) {
            lambdaTooltip("Clear binding")
        }

        val poll = KeyboardUtils.lastEvent
        if (listening && poll.isPressed) {
            when (val key = poll.translated) {
                KeyCode.ESCAPE -> listening = false
                KeyCode.BACKSPACE, KeyCode.DELETE -> {
                    value = KeyCode.UNBOUND
                    listening = false
                }
                else -> {
                    value = key
                    listening = false
                }
            }
        }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { parameter ->
            suggests { _, builder ->
                KeyCode.entries.forEach { builder.suggest(it.name.capitalize()) }
                builder.buildFuture()
            }
            execute {
                trySetValue(KeyCode.valueOf(parameter().value()))
            }
        }
    }
}
