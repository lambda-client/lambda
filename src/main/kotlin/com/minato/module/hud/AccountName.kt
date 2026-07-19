
package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe

@Suppress("unused")
object AccountName : HudModule(
    name = "AccountName",
    description = "Displays the current accounts name",
    tag = ModuleTag.HUD
) {
    override fun ImGuiBuilder.buildLayout() {
        runSafe { text(player.name.string) }
    }
}