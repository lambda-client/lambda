package com.lambda.newgui

import com.lambda.graphics.RenderMain
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

@DslMarker
annotation class UIBuilder

/**
 * Creates a "gui" represented by layout component
 */
@UIBuilder
fun gui(block: Layout.() -> Unit) =
    Layout(owner = null, useBatching = false, batchChildren = false).apply {
        var screenSize = Vec2d.ONE * 10000.0

        rect {
            Rect(Vec2d.ZERO, screenSize)
        }

        onRender {
            screenSize = RenderMain.screenSize
        }
    }.apply(block)


/**
 * Creates empty layout
 *
 * @param useBatching
 * Increases performance by using parent's renderer instead of creating a new one.
 *
 * @param batchChildren
 * Whether allow children to use the renderer of this layout
 */
@UIBuilder
fun Layout.layout(
    useBatching: Boolean = false,
    batchChildren: Boolean = false,
    block: Layout.() -> Unit,
) = Layout(this, useBatching, batchChildren)
    .apply(children::add).apply(block)

