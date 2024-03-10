package com.lambda.module

import com.lambda.config.Configurable
import com.lambda.module.tag.ModuleTag
import com.lambda.util.KeyCode
import com.lambda.util.Nameable

abstract class Module(
    override val name: String,
    val description: String = "",
    val tags: Set<ModuleTag> = setOf(),
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: KeyCode = KeyCode.Unbound
) : Nameable, Configurable(ModuleConfig) {
    val isEnabled by setting("Enabled", enabledByDefault, { false })

    init {
        // register listeners
//        unsafeListener<EnableModuleEvent> { module ->
//
//        }
    }
}