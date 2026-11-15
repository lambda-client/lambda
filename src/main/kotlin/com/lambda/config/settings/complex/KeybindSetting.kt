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

@file:Suppress("unused")

package com.lambda.config.settings.complex

import com.fasterxml.jackson.annotation.JsonIncludeProperties
import com.lambda.Lambda.mc
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.boolean
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.ConfigEntryDsl
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.context.SafeContext
import com.lambda.event.Muteable
import com.lambda.event.events.ButtonEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.threading.runSafe
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.StringUtils.capitalize
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import org.lwjgl.glfw.GLFW.GLFW_MOD_ALT
import org.lwjgl.glfw.GLFW.GLFW_MOD_CAPS_LOCK
import org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_MOD_NUM_LOCK
import org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT
import org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER

class KeybindSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<KeybindSetting, Bind>,
    visibility: () -> Boolean,
    defaultValue: Bind,
    private val muteable: Muteable?,
    private val alwaysListening: Boolean,
    private val screenCheck: Boolean
) : Setting<Bind>(name, description, defaultValue, layer, config, visibility), Muteable {
    constructor(
        name: String,
        description: String,
        config: Config,
        layer: SettingEntryLayer<KeybindSetting, Bind>,
        visibility: () -> Boolean,
        defaultValue: KeyCode,
        muteable: Muteable?,
        alwaysListen: Boolean,
        screenCheck: Boolean
    ) : this(name, description, config, layer, visibility, Bind(defaultValue.code, 0, -1), muteable, alwaysListen, screenCheck)

    private val pressListeners = mutableListOf<SafeContext.(ButtonEvent) -> Unit>()
    private val unsafePressListeners = mutableListOf<(ButtonEvent) -> Unit>()
    private val repeatListeners = mutableListOf<SafeContext.(ButtonEvent) -> Unit>()
    private val unsafeRepeatListeners = mutableListOf<(ButtonEvent) -> Unit>()
    private val releaseListeners = mutableListOf<SafeContext.(ButtonEvent) -> Unit>()
    private val unsafeReleaseListeners = mutableListOf<(ButtonEvent) -> Unit>()

    override val isMuted
        get() = muteable?.isMuted == true && !alwaysListening

    init {
        listenUnsafe<ButtonEvent.Keyboard.Press> { event -> onButtonEvent(event) }
        listenUnsafe<ButtonEvent.Mouse.Click> { event -> onButtonEvent(event) }
    }

    private fun onButtonEvent(event: ButtonEvent) {
        if (mc.options.commandKey.isPressed ||
            (screenCheck && mc.currentScreen != null) ||
            !event.satisfies(value)) return

        if (event.isPressed) {
            if (event.isRepeated) {
                unsafeRepeatListeners.forEach { it(event) }
                runSafe { repeatListeners.forEach { it(event) } }
            } else {
                unsafePressListeners.forEach { it(event) }
                runSafe { pressListeners.forEach { it(event) } }
            }
        } else if (event.isReleased) {
            unsafeReleaseListeners.forEach { it(event) }
            runSafe { releaseListeners.forEach { it(event) } }
        }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(word(name)) { nameArg ->
            suggests { _, builder ->
                KeyCode.entries.forEach { builder.suggest(it.name.capitalize()) }
                (1..10).forEach { builder.suggest(it.toString()) }
                builder.buildFuture()
            }
            optional(boolean("mouse button")) { isMouseButton ->
                executeWithResult {
                    val isMouse = if (isMouseButton != null) isMouseButton().value() else false
                    var bind = Bind.EMPTY
                    if (isMouse) {
                        val num = try {
                            nameArg().value().toInt()
                        } catch (_: NumberFormatException) {
                            return@executeWithResult failure("${nameArg().value()} doesn't match with a mouse button")
                        }
                        bind = Bind(0, 0, mouse = num)
                    } else {
                        bind = try {
                            Bind(KeyCode.valueOf(nameArg().value()).code, 0)
                        } catch (_: IllegalArgumentException) {
                            return@executeWithResult failure("${nameArg().value()} doesn't match with a bind")
                        }
                    }

                    trySetValue(bind)
                    return@executeWithResult success()
                }
            }
        }
    }

    @ConfigEntryDsl
    fun onPress(block: SafeContext.(ButtonEvent) -> Unit) = apply { pressListeners.add(block) }

    @ConfigEntryDsl
    fun onPressUnsafe(block: (ButtonEvent) -> Unit) = apply { unsafePressListeners.add(block) }

    @ConfigEntryDsl
    fun onRepeat(block: SafeContext.(ButtonEvent) -> Unit) = apply { repeatListeners.add(block) }

    @ConfigEntryDsl
    fun onRepeatUnsafe(block: (ButtonEvent) -> Unit) = apply { unsafeRepeatListeners.add(block) }

    @ConfigEntryDsl
    fun onRelease(block: SafeContext.(ButtonEvent) -> Unit) = apply { releaseListeners.add(block) }

    @ConfigEntryDsl
    fun onReleaseUnsafe(block: (ButtonEvent) -> Unit) = apply { unsafeReleaseListeners.add(block) }
}

@Suppress("unused")
@JsonIncludeProperties("key", "modifiers", "mouse")
data class Bind(
    val key: Int,
    val modifiers: Int,
    val mouse: Int = -1,
) {
    val trueMods = buildList {
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
            if (modifiers > 0) list.add(trueMods.joinToString(separator = "+") { it.name })
            if (key > 0) list.add(KeyCode.fromKeyCode(key))

            return list.joinToString(separator = "+") { it.toString() }
        }

    override fun toString() =
        "Key Code: $key, Modifiers: ${trueMods.joinToString(separator = "+") { it.name }}, Mouse Button: ${Mouse.entries.getOrNull(mouse) ?: "None"}"

    companion object {
        val EMPTY = Bind(0, 0, -1)
    }
}
