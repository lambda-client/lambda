package com.lambda.gui.api.layer

import com.lambda.graphics.renderer.IRenderEntry
import com.lambda.graphics.renderer.IRenderer
import com.lambda.graphics.renderer.gui.font.IFontEntry
import com.lambda.graphics.renderer.gui.rect.IRectEntry

// Used to group all render entries related to a component
class LayerEntry (
    private val rect: IRenderer<IRectEntry>,
    private val font: IRenderer<IFontEntry>
) {
    private val entries = mutableSetOf<IRenderEntry<*>>()

    fun rect(block: IRectEntry.() -> Unit) =
        rect.build(block).apply(entries::add)

    fun font(block: IFontEntry.() -> Unit) =
        font.build(block).apply(entries::add)

    fun destroy() =
        entries.forEach(IRenderEntry<*>::destroy)
}