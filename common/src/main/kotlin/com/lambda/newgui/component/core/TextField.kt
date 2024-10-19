package com.lambda.newgui.component.core

import com.lambda.newgui.component.layout.Layout
import com.lambda.util.math.Vec2d
import java.awt.Color

class TextField(
    owner: Layout,
) : Layout(owner, true, true) {
    var text = ""
    var color = Color.WHITE
    var scale = 1.0
    var bold = false
    var shadow = true

    val textWidth  get() = fr.getWidth(text, scale)
    val textHeight get() = fr.getHeight(scale)

    private val fr get() = if (bold) renderer.boldFont else renderer.font

    private val updateActions = mutableListOf<TextField.() -> Unit>()

    fun onUpdate(block: TextField.() -> Unit) {
        updateActions += block
    }

    init {
        properties.interactionPassthrough = true

        onRender {
            updateActions.forEach { action ->
                action(this@TextField)
            }

            val renderPos = Vec2d(renderPositionX, renderPositionY + textHeight * 0.5)
            fr.build(text, renderPos, color, scale, shadow)
        }
    }

    companion object {
        /**
         * Creates a [TextField] component
         */
        @UIBuilder
        fun Layout.textField(
            block: TextField.() -> Unit = {}
        ) = TextField(this).apply(children::add).apply(block)
    }
}