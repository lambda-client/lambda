package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler

sealed class ScreenHandlerEvent : Event {
    class Open<H : ScreenHandler>(val screenHandler: H) : ScreenHandlerEvent()
    class Close<H : ScreenHandler>(val screenHandler: H) : ScreenHandlerEvent()
    data class Loaded(
        val revision: Int,
        val stacks: List<ItemStack>,
        val cursorStack: ItemStack,
    ) : ScreenHandlerEvent()
}