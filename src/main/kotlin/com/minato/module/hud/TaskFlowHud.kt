
package com.minato.module.hud

import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask

@Suppress("unused")
object TaskFlowHud : HudModule(
    name = "TaskFlowHud",
    tag = ModuleTag.HUD,
) {
    override fun ImGuiBuilder.buildLayout() {
        text(RootTask.toString())
    }
}
