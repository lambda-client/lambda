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

package com.lambda.module

import com.lambda.command.LambdaCommand
import com.lambda.config.Configurable
import com.lambda.config.Configuration
import com.lambda.config.MutableAutomationConfig
import com.lambda.config.MutableAutomationConfigImpl
import com.lambda.config.SettingCore
import com.lambda.config.configurations.ModuleConfigs
import com.lambda.config.settings.complex.Bind
import com.lambda.config.settings.complex.KeybindSetting.Companion.onPress
import com.lambda.config.settings.complex.KeybindSetting.Companion.onRelease
import com.lambda.context.SafeContext
import com.lambda.event.Muteable
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.listener.Listener
import com.lambda.event.listener.SafeListener
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener
import com.lambda.module.tag.ModuleTag
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundManager.play
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
 * the default [keybind] should not be set (using [KeyCode.Unbound]).
 *
 * [Module]s are [Configurable]s with [settings] (see [SettingCore] for all setting types).
 * Example:
 * ```
 * private val foo by setting("Foo", true)
 * private val bar by setting("Bar", 0.0, 0.1..5.0, 0.1)
 * ```
 *
 * These settings are persisted in the `lambda/config/modules.json` config file.
 * See [ModuleConfigs.primary] and [Configuration] for more details.
 *
 * In the `init` block, you can add hooks like [onEnable], [onDisable], [onToggle] and add listeners.
 *
 * Example:
 * ```
 * init {
 *     onEnable { // runs on module activation
 *         LOG.info("I was enabled!")
 *     }
 *
 *     onToggle { to ->
 *          LOG.info("Module enabled: ${to}")
 *     }
 *
 *     onDisable {
 *          LOG.info("I was disable!")
 *     }
 *
 *     listener<TickEvent.Pre> { event ->
 *         LOG.info("I've ticked!")
 *     }
 * }
 * ```
 *
 * [Listener]s are only triggered if:
 * - [Module] is [isEnabled], otherwise it [isMuted] (see [Muteable])
 * - [Module] was configured to [alwaysListening]
 * - [Listener] was configured to [Listener.alwaysListen]
 *
 * Example:
 * ```
 * val bind1 = setting("Keybind", KeyCode.A)
 * val bind2 = setting("Keybind", Bind(KeyCode.A.code, 0, -1))
 *
 * listen<KeyboardEvent.Press>(alwaysListen = true) { event ->
 *     if (!event.satisfies(bind1) || !event.satisfies(bind2)) return@listen
 *
 *     if (event.isPressed) toggle()
 *     else if (event.isReleased) disable()
 * }
 * ```
 *
 * See [SafeListener] and [UnsafeListener] for more details.
 */
abstract class Module(
    override val name: String,
    val description: String = "",
    val tag: ModuleTag,
    private val alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.EMPTY,
    autoDisable: Boolean = false
) : Nameable, Muteable, Configurable(ModuleConfigs), MutableAutomationConfig by MutableAutomationConfigImpl() {
    private val isEnabledSetting = setting("Enabled", enabledByDefault) { false }
    val keybindSetting = setting("Keybind", defaultKeybind, alwaysListen = true) { false }
        .onPress { toggle() }
        .onRelease { if (disableOnRelease) disable() }
    val disableOnReleaseSetting = setting("Disable On Release", false) { false }
    val drawSetting = setting("Draw", true, "Draws the module in the module list hud element")

    var isEnabled by isEnabledSetting
    val isDisabled get() = !isEnabled

    val keybind by keybindSetting
    val disableOnRelease by disableOnReleaseSetting
    val draw by drawSetting

    override val isMuted: Boolean
        get() = !isEnabled && !alwaysListening

    init {
        onEnable { LambdaSound.ModuleOn.play() }
        onDisable { LambdaSound.ModuleOff.play() }

        onEnableUnsafe { LambdaSound.ModuleOn.play() }
        onDisableUnsafe { LambdaSound.ModuleOff.play() }

        listen<ClientEvent.Shutdown> { if (autoDisable) disable() }
        listen<ClientEvent.Startup> { if (autoDisable) disable() }
        listen<ConnectionEvent.Disconnect> { if (autoDisable) disable() }
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
