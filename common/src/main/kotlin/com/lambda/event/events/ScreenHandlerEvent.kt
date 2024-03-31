package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler

sealed class ScreenHandlerEvent : Event {
    class Open<T : ScreenHandler>(val screen: T) : ScreenHandlerEvent()
    class Close<T : ScreenHandler>(val screen: T) : ScreenHandlerEvent()
    data class Loaded(
        val revision: Int,
        val stacks: List<ItemStack>,
        val cursorStack: ItemStack,
    ) : ScreenHandlerEvent()
}