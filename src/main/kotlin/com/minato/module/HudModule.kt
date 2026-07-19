
package com.minato.module

import com.minato.config.settings.complex.Bind
import com.minato.gui.Layout
import com.minato.module.tag.ModuleTag
import java.awt.Color

abstract class HudModule(
    name: String,
    description: String = "",
    tag: ModuleTag,
    alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    defaultKeybind: Bind = Bind.EMPTY,
) : Module(name, description, tag, alwaysListening, enabledByDefault, defaultKeybind = defaultKeybind), Layout {
    val backgroundColor = setting("Background Color", Color(0, 0, 0, 0))
}
