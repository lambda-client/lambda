package com.lambda.gui.api.component

import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.component.button.ListButton
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.module.modules.client.ClickGui

abstract class ListWindow<T : ListButton>(
    owner: AbstractClickGui,
) : WindowComponent<T>(owner) {
    private var scrollOffset: Double = 0.0
    private var rubberbandRequest = 0.0
    private var rubberbandDelta = 0.0

    override fun onEvent(e: GuiEvent) {
        when (e) {
            is GuiEvent.Tick -> {
                rubberbandDelta += rubberbandRequest
                rubberbandRequest = 0.0

                rubberbandDelta *= 0.5
                if (rubberbandDelta < 0.05) rubberbandDelta = 0.0

                var y = scrollOffset + rubberbandDelta
                contentComponents.children.forEach { button ->
                    button.heightOffset = y
                    y += button.size.y + button.listStep
                }
            }
            is GuiEvent.MouseScroll -> {
                val delta = e.delta * 10.0 * ClickGui.scrollSpeed
                scrollOffset += delta

                val prevOffset = scrollOffset
                val range = -contentComponents.children.sumOf { it.size.y + it.listStep }
                scrollOffset = scrollOffset.coerceAtLeast(range).coerceAtMost(0.0)

                rubberbandRequest += prevOffset - scrollOffset
            }
        }

        super.onEvent(e)
    }
}