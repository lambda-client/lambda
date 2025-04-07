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

package com.lambda.module

import com.lambda.command.LambdaCommand
import com.lambda.config.AbstractSetting
import com.lambda.config.Configurable
import com.lambda.config.Configuration
import com.lambda.config.configurations.ModuleConfig
import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.numeric.DoubleSetting
import com.lambda.context.SafeContext
import com.lambda.event.Muteable
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.Listener
import com.lambda.event.listener.SafeListener
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener
import com.lambda.module.hud.ModuleList
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.playSoundRandomly
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import com.lambda.util.Nameable

/**
 * A [Module] is a feature or tool for the utility mod.
 * It represents a [Configurable] component of the mod,
 * with its own set of behaviors and properties.
 *
 * Each [Module] has a [name], which is displayed in-game.
 * The [description] of the module is shown when hovering over
 * the [ModuleButton] in the GUI and in [LambdaCommand]s.
 * The [Module] can be associated with a [Set] of [ModuleTag]s to allow for
 * easier filtering and searching in the GUI.
 *
 * A [Module] can be activated by a [keybind], represented by a [KeyCode].
 * The default [keybind] is the key on which
 * the module will be activated by default.
 * If a module does not need to be activated by a key (like [ClickGui]),
 * the default [keybind] should not be set (using [KeyCode.UNBOUND]).
 *
 * [Module]s are [Configurable]s with [settings] (see [AbstractSetting] for all setting types).
 * For example, a [BooleanSetting] and a [DoubleSetting] can be defined like this:
 * ```kotlin
 * private val foo by setting("Foo", true)
 * private val bar by setting("Bar", 0.0, 0.1..5.0, 0.1)
 * ```
 * These settings are persisted in the `lambda/config/modules.json` config file.
 * See [ModuleConfig.primary] and [Configuration] for more details.
 *
 * In the `init` block, you can add triggers like [onEnable], [onDisable], [onToggle] and register [Listener].
 * For example:
 *
 * ```kotlin
 * init {
 *     onEnable { // runs on module activation
 *         LOG.info("I was enabled!")
 *     }
 *
 *     listener<TickEvent.Pre> { event -> // runs every game tick
 *         LOG.info("I'm ${if (foo) "super boring ($bar)" else "boring"}!")
 *     }
 * }
 * ```
 *
 * [Listener]s are only triggered if:
 * - [Module] is [isEnabled], otherwise it [isMuted] (see [Muteable])
 * - [Module] was configured to [alwaysListening]
 * - [Listener] was configured to [Listener.alwaysListen]
 *
 * For example:
 *
 * ```kotlin
 * listener<KeyPressEvent>(alwaysListen = true) { event ->
 *     if (event.key == keybind.key) {
 *         toggle()
 *     }
 * }
 * ```
 *
 * See [SafeListener] and [UnsafeListener] for more details.
 *
 * @property name The name of the module, displayed in-game.
 * @property description The description of the module shown on hover over the module button in the GUI and in commands.
 * @property defaultTags The default [ModuleTag]s associated with the module.
 * @property alwaysListening If true, the module's listeners will be triggered even if the module is not enabled.
 * @property isEnabledSetting The setting that determines if the module is enabled.
 * @property keybindSetting The setting that determines the keybind for the module.
 * @property isEnabled The current enabled state of the module.
 * @property isMuted If true, the module's listeners will not be triggered.
 * @property keybind The current keybind for the module.
 * */
abstract class Module(
    override val name: String,
    val description: String = "",
    val defaultTags: Set<ModuleTag> = setOf(),
    private val alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.UNBOUND,
) : Nameable, Muteable, Configurable(ModuleConfig) {
    private val isEnabledSetting = setting("Enabled", enabledByDefault) { false }
    private val keybindSetting = setting("Keybind", defaultKeybind)
    val isVisible = setting("Visible", true) { ModuleList.isEnabled }
    val reset by setting("Reset", { settings.forEach { it.reset() }; this@Module.info("Settings set to default") })
    val customTags = setting("Tags", setOf<ModuleTag>()) { false }

    var isEnabled by isEnabledSetting
    val isDisabled get() = !isEnabled
    override val isMuted: Boolean
        get() = !isEnabled && !alwaysListening
    val keybind by keybindSetting

    init {
        listen<KeyboardEvent.Press>(alwaysListen = true) { event ->
            if (mc.options.commandKey.isPressed) return@listen
            if (keybind == KeyCode.UNBOUND) return@listen
            if (event.translated != keybind) return@listen
            if (mc.currentScreen != null) return@listen

            toggle()
        }

        onEnable {
            playSoundRandomly(LambdaSound.MODULE_ON.event)
        }

        onDisable {
            playSoundRandomly(LambdaSound.MODULE_OFF.event)
        }

        onEnable {
            playSoundRandomly(LambdaSound.MODULE_ON.event)
        }

        onDisable {
            playSoundRandomly(LambdaSound.MODULE_OFF.event)
        }

        onEnable {
            playSoundRandomly(LambdaSound.MODULE_ON.event)
        }

        onDisable {
            playSoundRandomly(LambdaSound.MODULE_OFF.event)
        }
    }

    fun enable() {
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
    }

    fun toggle() {
        isEnabled = !isEnabled
    }

    protected fun onEnable(block: SafeContext.() -> Unit) {
        isEnabledSetting.onValueChange { from, to ->
            if (!from && to) block()
        }
    }

    protected fun onDisable(block: SafeContext.() -> Unit) {
        isEnabledSetting.onValueChange { from, to ->
            if (from && !to) block()
        }
    }

    protected fun onToggle(block: SafeContext.(to: Boolean) -> Unit) {
        isEnabledSetting.onValueChange { from, to ->
            if (from != to) block(to)
        }
    }

    protected fun onEnableUnsafe(block: () -> Unit) {
        isEnabledSetting.onValueChangeUnsafe { from, to ->
            if (!from && to) block()
        }
    }

    protected fun onDisableUnsafe(block: () -> Unit) {
        isEnabledSetting.onValueChangeUnsafe { from, to ->
            if (from && !to) block()
        }
    }

    protected fun onToggleUnsafe(block: (to: Boolean) -> Unit) {
        isEnabledSetting.onValueChangeUnsafe { from, to ->
            if (from != to) block(to)
        }
    }
}
