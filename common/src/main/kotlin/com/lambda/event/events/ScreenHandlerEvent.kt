package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler

sealed class ScreenHandlerEvent : Event {
    class Open(val screenHandler: ScreenHandler) : ScreenHandlerEvent()
    class Close(val screenHandler: ScreenHandler) : ScreenHandlerEvent()

    data class Update(
        val revision: Int,
        val stacks: List<ItemStack>,
        val cursorStack: ItemStack,
    ) : ScreenHandlerEvent()
}