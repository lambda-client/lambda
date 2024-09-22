package com.lambda.newgui.component.layout

import com.lambda.newgui.component.core.IListEntry
import com.lambda.newgui.component.core.UIBuilder

class ListLayout(
    owner: Layout,
    private val scrollable: Boolean
) : Layout(owner, false, true) {
    private var scrollOffset: Double = 0.0
    private var rubberbandRequest = 0.0
    private var rubberbandDelta = 0.0

    init {
        onShow {
            scrollOffset = 0.0
        }

        onTick {
            rubberbandDelta += rubberbandRequest
            rubberbandRequest = 0.0

            rubberbandDelta *= 0.5
            if (rubberbandDelta < 0.05) rubberbandDelta = 0.0

            var y = scrollOffset + rubberbandDelta

            children.forEach { child ->
                if (child !is IListEntry) return@forEach

                child.heightOffset = y
                y += child.rect.size.y + 2
            }
        }

        onMouseScroll { delta ->
            if (!scrollable) return@onMouseScroll

            scrollOffset += delta * 10.0

            val prevOffset = scrollOffset
            val range = -children.sumOf { it.rect.size.y } + rect.size.y
            scrollOffset = scrollOffset.coerceAtLeast(range).coerceAtMost(0.0)

            rubberbandRequest += prevOffset - scrollOffset
        }
    }

    companion object {
        /**
         * Creates an empty [ListLayout]
         *
         * @param block Actions to perform within this component
         */
        @UIBuilder
        fun Layout.listLayout(
            scrollable: Boolean = true,
            block: ListLayout.() -> Unit
        ) = ListLayout(this, scrollable).apply(children::add).apply(block)
    }
}