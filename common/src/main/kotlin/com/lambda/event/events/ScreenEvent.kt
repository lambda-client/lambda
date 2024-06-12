package com.lambda.event.events

import com.lambda.event.Event
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.HandledScreen
import net.minecraft.item.ItemStack
import net.minecraft.screen.ScreenHandler

sealed class ScreenEvent : Event {
    class Open<T : Screen>(val screen: T) : ScreenEvent()
    class Close<T : Screen>(val screen: T) : ScreenEvent()
}