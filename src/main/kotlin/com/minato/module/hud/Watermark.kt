
package com.minato.module.hud

import com.minato.graphics.texture.TextureOwner.upload
import com.minato.gui.dsl.ImGuiBuilder
import com.lambda.imgui.ImGui
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag

@Suppress("unused")
object Watermark : HudModule(
    name = "Watermark",
    tag = ModuleTag.HUD,
    enabledByDefault = true,
) {
    private val texture = upload("textures/minato.png")
    private val scale by setting("Watermark Scale", 0.15f, 0.01f..1f, 0.01f)

    override fun ImGuiBuilder.buildLayout() {
        val width = texture.width * scale
        val height = texture.height * scale
        ImGui.image(texture.id.toLong(), width, height)
    }
}
