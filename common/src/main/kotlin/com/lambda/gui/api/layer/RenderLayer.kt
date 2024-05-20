package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.gui.font.FontRenderer
import com.lambda.graphics.renderer.gui.rect.AbstractRectRenderer
import com.lambda.graphics.renderer.gui.rect.filled.FilledRectRenderer
import com.lambda.graphics.renderer.gui.rect.outline.OutlineRectRenderer
import com.lambda.module.modules.client.GuiSettings
import com.lambda.util.math.Vec2d

class RenderLayer {
    private val filled = FilledRectRenderer()
    private val outline = OutlineRectRenderer()
    private val font = FontRenderer()

    fun entry() = LayerEntry(filled, outline, font)

    fun assignOffset(ofs: Vec2d) {
        filled.matrixOffset = ofs
        outline.matrixOffset = ofs
        font.matrixOffset = ofs
    }

    fun render() {
        filled.update()
        outline.update()
        font.update()

        filled.render()
        outline.render()
        font.render()
    }

    fun destroy() {
        filled.destroy()
        outline.destroy()
        font.destroy()
    }
}
