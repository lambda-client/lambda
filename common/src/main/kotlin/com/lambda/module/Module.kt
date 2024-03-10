package com.lambda.module

import com.lambda.config.Configurable
import com.lambda.event.Muteable
import com.lambda.event.events.KeyPressEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.Nameable

abstract class Module(
    override val name: String,
    val description: String = "",
    val tags: Set<ModuleTag> = setOf(),
    private val alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.Unbound,
) : Nameable, Muteable, Configurable(ModuleConfig) {
    private val isEnabledSetting = setting("Enabled", enabledByDefault, { false })
    private val keybindSetting = setting("Keybind", defaultKeybind)

    var isEnabled by isEnabledSetting
    override val isMuted: Boolean
        get() = !isEnabled && !alwaysListening
    private val keybind by keybindSetting

    init {
//        unsafeListener<ClientEvent.ConfigLoaded>(alwaysListen = true) { event ->
//            if (event.configuration != ModuleConfig) return@unsafeListener
//
//            isEnabled = isEnabledSetting.value
//        }

        listener<KeyPressEvent>(alwaysListen = true) { event ->
            if (event.key == keybind.key) {
                toggle()
            }
        }
    }

    private fun toggle() {
        isEnabled = !isEnabled
    }

    protected fun onEnable(block: () -> Unit) {
        isEnabledSetting.listener { from, to ->
            if (!from && to) block()
        }
    }

    protected fun onDisable(block: () -> Unit) {
        isEnabledSetting.listener { from, to ->
            if (from && !to) block()
        }
    }

    protected fun onToggle(block: (to: Boolean) -> Unit) {
        isEnabledSetting.listener { from, to ->
            if (from != to) block(to)
        }
    }

    protected fun onEnableUnsafe(block: () -> Unit) {
        isEnabledSetting.unsafeListener { from, to ->
            if (!from && to) block()
        }
    }

    protected fun onDisableUnsafe(block: () -> Unit) {
        isEnabledSetting.unsafeListener { from, to ->
            if (from && !to) block()
        }
    }

    protected fun onToggleUnsafe(block: (to: Boolean) -> Unit) {
        isEnabledSetting.unsafeListener { from, to ->
            if (from != to) block(to)
        }
    }
}