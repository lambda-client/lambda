
package com.minato.gui

import com.minato.gui.dsl.ImGuiBuilder

interface Layout {
    fun ImGuiBuilder.buildLayout()
}