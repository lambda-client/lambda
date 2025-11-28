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
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER
import org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
import org.lwjgl.glfw.GLFW.GLFW_MOD_CAPS_LOCK
import org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_MOD_NUM_LOCK
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER

class KeybindSetting(
    override var name: String,
    defaultValue: Bind,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Bind>(
    name,
    defaultValue,
    TypeToken.get(Bind::class.java).type,
    description,
    visibility
) {
    constructor(name: String, defaultValue: KeyCode, description: String, visibility: () -> Boolean)
            : this(name, Bind(defaultValue.code, 0, -1), description, visibility)

    private var listening = false

    override fun ImGuiBuilder.buildLayout() {
        text(name)
        sameLine()

        val bind = value
        val preview =
            if (listening) "Press any key…"
            else bind.name

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
            if (!listening) description.ifBlank { "Click to set. Esc cancels. Backspace/Delete unbinds." }
            else "Listening… Press a key to bind. Esc to cancel. Backspace/Delete to unbind."
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

        if (listening) {
            InputUtils.newMouseEvent()
                ?.let {
                    value = Bind(0, it.modifiers, it.button)
                    listening = false
                    return
                }

            InputUtils.newKeyboardEvent()
                ?.let {
                    val isModKey = it.keyCode in GLFW_KEY_LEFT_SHIFT..GLFW_KEY_RIGHT_SUPER

                    // If a mod key is pressed first ignore it unless it was released without any other keys
                    if ((it.isPressed && !isModKey) || (it.isReleased && isModKey)) {
                        when (it.translated) {
                            KeyCode.Escape -> {}
                            KeyCode.Backspace, KeyCode.Delete -> value = Bind.EMPTY
                            else -> value = Bind(it.keyCode, it.modifiers, -1)
                        }

                        listening = false
                    }

                    return
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
                        bind = Bind(0, 0, mouse = num)
                    } else {
                        bind = try {
                            Bind(KeyCode.valueOf(name().value()).code, 0)
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
    val key: Int,
    val modifiers: Int,
    val mouse: Int = -1,
) {
    val truemods = buildList {
        if (modifiers and GLFW_MOD_SHIFT != 0) add(KeyCode.LeftShift)
        if (modifiers and GLFW_MOD_CONTROL != 0) add(KeyCode.LeftControl)
        if (modifiers and GLFW_MOD_ALT != 0) add(KeyCode.LeftAlt)
        if (modifiers and GLFW_MOD_SUPER != 0) add(KeyCode.LeftSuper)
        if (modifiers and GLFW_MOD_CAPS_LOCK != 0) add(KeyCode.CapsLock)
        if (modifiers and GLFW_MOD_NUM_LOCK != 0) add(KeyCode.NumLock)
    }

    val isMouseBind: Boolean
        get() = mouse >= 0

    val isKeyBind: Boolean
        get() = key > 0

    val name: String
        get() {
            if (mouse < 0 && modifiers <= 0 && key <= 0) return "Unbound"

            val list = mutableListOf<Any>()

            if (mouse >= 0) list.add(Mouse.entries[mouse])
            if (modifiers > 0) list.add(truemods.joinToString(separator = "+") { it.name })
            if (key > 0) list.add(KeyCode.fromKeyCode(key))

            return list.joinToString(separator = "+") { it.toString() }
        }

    override fun toString() =
        "Key Code: $key, Modifiers: ${truemods.joinToString(separator = "+") { it.name }}, Mouse Button: ${Mouse.entries.getOrNull(mouse) ?: "None"}"

    companion object {
        val EMPTY = Bind(0, 0, -1)
    }
}
