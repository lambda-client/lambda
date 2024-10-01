package com.lambda.newgui.component.core

import com.lambda.newgui.component.layout.Layout
import java.awt.Color

class OutlineRect(
    owner: Layout
) : Layout(owner, true, true) {
    var rectangle = owner.rect

    var roundRadius = 0.0
    var glowRadius = 0.0

    var leftTopColor: Color = Color.WHITE
    var rightTopColor: Color = Color.WHITE
    var rightBottomColor: Color = Color.WHITE
    var leftBottomColor: Color = Color.WHITE

    var shade = false

    private val updateActions = mutableListOf<OutlineRect.() -> Unit>()

    fun onUpdate(block: OutlineRect.() -> Unit) {
        updateActions += block
    }

    init {
        onRender {
            updateActions.forEach { action ->
                action(this@OutlineRect)
            }

            outline.build(
                rectangle,
                roundRadius,
                glowRadius,
                leftTopColor,
                rightTopColor,
                rightBottomColor,
                leftBottomColor,
                shade
            )
        }
    }

    fun setColor(color: Color) {
        leftTopColor = color
        rightTopColor = color
        rightBottomColor = color
        leftBottomColor = color
    }

    companion object {
        /**
         * Creates a [OutlineRect] component - layout-based rect representation
         */
        @UIBuilder
        fun Layout.outline(block: OutlineRect.() -> Unit = {}) =
            OutlineRect(this).apply(children::add).apply {
                updateActions += block
            }
    }
}