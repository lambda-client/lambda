package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.renderer.IRenderer
import com.lambda.graphics.renderer.gui.font.IFontEntry
import com.lambda.graphics.renderer.gui.rect.IRectEntry

// Used to group all render entries related to a component
class LayerEntry (
    private val filled: IRenderer<IRectEntry.Filled>,
    private val outline: IRenderer<IRectEntry.Outline>,
    private val font: IRenderer<IFontEntry>
) {
    private val entries = mutableSetOf<IRenderEntry<*>>()

    fun filled(block: IRectEntry.Filled.() -> Unit) =
        filled.build(block).apply(entries::add)

    fun outline(block: IRectEntry.Outline.() -> Unit) =
        outline.build(block).apply(entries::add)

    fun font(block: IFontEntry.() -> Unit) =
        font.build(block).apply(entries::add)

    fun destroy() =
        entries.forEach(IRenderEntry<*>::destroy)
}