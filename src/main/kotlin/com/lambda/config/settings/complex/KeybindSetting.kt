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
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.boolean
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.config.settings.complex.Bind.Companion.mouseBind
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.InputUtils
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
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
    defaultValue: Bind,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Bind>(
    defaultValue,
    TypeToken.get(Bind::class.java).type,
    description,
    visibility
) {
    private var listening = false

    override fun ImGuiBuilder.buildLayout() {
        val bind = value
        val scancode = if (bind.code == KeyCode.UNBOUND.code) -69 else GLFW.glfwGetKeyScancode(bind.code)
        val translated = if (bind.keyCode == KeyCode.UNBOUND) bind.keyCode else KeyCode.virtualMapUS(bind.code, scancode)
        val preview = if (listening) "$name: Press any key…"
        else if (bind.isMouseButton) "Mouse ${bind.code}" else "$name: $translated"

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
            value = Bind.EMPTY
            listening = false
        }

        if (listening && !isAnyItemHovered && isMouseClicked(ImGuiMouseButton.Left)) {
            listening = false
        }

        sameLine()
        smallButton("Unbind") {
            value = Bind.EMPTY
            listening = false
        }
        onItemHover(ImGuiHoveredFlags.Stationary) {
            lambdaTooltip("Clear binding")
        }

        val keyboardPoll = InputUtils.lastKeyboardEvent
        val mousePoll = InputUtils.lastMouseEvent
        if (listening) {
            if (keyboardPoll.isPressed) {
                when (val key = keyboardPoll.translated) {
                    KeyCode.ESCAPE -> {}
                    KeyCode.BACKSPACE, KeyCode.DELETE -> value = Bind.EMPTY
                    else -> value = Bind(key)
                }
                listening = false
            } else if (mousePoll.action == Mouse.Action.Click.ordinal) {
                value = mouseBind(mousePoll.button)
                listening = false
            }
        }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { name ->
            suggests { _, builder ->
                KeyCode.entries.forEach { builder.suggest(it.name.capitalize()) }
                (1..10).forEach { builder.suggest(it) }
                builder.buildFuture()
            }
            optional(boolean("mouse button")) { isMouseButton ->
                executeWithResult {
                    val isMouse = if (isMouseButton != null) isMouseButton().value() else false
                    var bind = Bind.EMPTY
                    if (isMouse) {
                        val num = try {
                            name().value().toInt()
                        } catch(_: NumberFormatException) {
                            return@executeWithResult failure("${name().value()} doesn't match with a mouse button")
                        }
                        bind = mouseBind(num)
                    } else {
                        bind = try {
                            Bind(KeyCode.valueOf(name().value()))
                        } catch(_: IllegalArgumentException) {
                            return@executeWithResult failure("${name().value()} doesn't match with a bind")
                        }
                    }

                    trySetValue(bind)
                    return@executeWithResult success()
                }
            }
        }
    }
}

data class Bind(
    val keyCode: KeyCode = KeyCode.UNBOUND,
    val code: Int = keyCode.code,
    val isMouseButton: Boolean = false
) {
    val name: String
        get() = if (isMouseButton) "Mouse $code" else keyCode.name

    override fun toString() =
        "Key Code: $keyCode, Code: $code, Mouse Button: $isMouseButton"

    companion object {
        val EMPTY = Bind()

        fun mouseBind(code: Int) = Bind(code = code, isMouseButton = true)
    }
}
