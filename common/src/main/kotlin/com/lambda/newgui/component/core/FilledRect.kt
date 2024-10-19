package com.lambda.newgui.component.core

import com.lambda.newgui.component.layout.Layout
import com.lambda.util.math.Rect
import java.awt.Color

class FilledRect(
    owner: Layout
) : Layout(owner, true, true) {
    var rectangle = Rect.ZERO

    var leftTopRadius = 0.0
    var rightTopRadius = 0.0
    var rightBottomRadius = 0.0
    var leftBottomRadius = 0.0

    var leftTopColor: Color = Color.WHITE
    var rightTopColor: Color = Color.WHITE
    var rightBottomColor: Color = Color.WHITE
    var leftBottomColor: Color = Color.WHITE

    var shade = false

    private val updateActions = mutableListOf<FilledRect.() -> Unit>()

    fun onUpdate(block: FilledRect.() -> Unit) {
        updateActions += block
    }

    init {
        onRender {
            updateActions.forEach { action ->
                action(this@FilledRect)
            }

            filled.build(
                rectangle,
                leftTopRadius,
                rightTopRadius,
                rightBottomRadius,
                leftBottomRadius,
                leftTopColor,
                rightTopColor,
                rightBottomColor,
                leftBottomColor,
                shade
            )
        }
    }

    fun setRadius(radius: Double) {
        leftTopRadius = radius
        rightTopRadius = radius
        rightBottomRadius = radius
        leftBottomRadius = radius
    }

    fun setColor(color: Color) {
        leftTopColor = color
        rightTopColor = color
        rightBottomColor = color
        leftBottomColor = color
    }

    companion object {
        /**
         * Creates a [FilledRect] component - layout-based rect representation
         */
        @UIBuilder
        fun Layout.rect(block: FilledRect.() -> Unit = {}) =
            FilledRect(this).apply(children::add).apply {
                updateActions += block
            }
    }
}